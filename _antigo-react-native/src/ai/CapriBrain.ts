// src/ai/CapriBrain.ts
// Núcleo de IA: mantém histórico de conversa e fala com Ollama rodando
// no servidor próprio do usuário. Esta classe é pura TypeScript/JS —
// roda tanto em RN quanto em Node puro para testes, sem depender de
// nenhum módulo nativo.

import {
  ChatMessage,
  CapriConfig,
  CapriResponse,
  CapriMood,
  GameTipRequest,
  ScreenContext,
} from '../types';
import { DEFAULT_CONFIG } from './config';

export class CapriBrain {
  private config: CapriConfig;
  private history: ChatMessage[] = [];

  /** @param ollamaBaseUrl ex: "http://SEU_SERVIDOR:11434" (sem barra no final) */
  constructor(ollamaBaseUrl: string, overrides: Partial<CapriConfig> = {}) {
    this.config = {
      ollamaBaseUrl,
      ...DEFAULT_CONFIG,
      ...overrides,
    };
  }

  /** Conversa livre (chat casual). */
  async chat(userMessage: string): Promise<CapriResponse> {
    this.pushHistory('user', userMessage);

    const text = await this.callAPI(this.config.personality);
    this.pushHistory('assistant', text);

    return {
      text,
      mood: this.inferMood(text),
      shouldSpeak: true,
    };
  }

  /** Dica de jogo: prompt separado, mais objetivo, opcionalmente com contexto de tela. */
  async getGameTip(request: GameTipRequest): Promise<CapriResponse> {
    const contextBlock = request.screenContext
      ? this.formatScreenContext(request.screenContext)
      : '';

    const prompt = `${request.question}\n\nJogo: ${request.gameName}${contextBlock}`;
    this.pushHistory('user', prompt);

    const systemPrompt = `${this.config.personality}

Modo atual: DICA DE JOGO. Responda com foco em utilidade prática
(build, item, estratégia). Máximo 3-4 frases. Se não tiver certeza
sobre a meta atual do jogo "${request.gameName}", diga isso em vez
de inventar.`;

    const text = await this.callAPI(systemPrompt);
    this.pushHistory('assistant', text);

    return {
      text,
      mood: 'focused',
      shouldSpeak: true,
    };
  }

  /** Limpa o histórico (ex: ao trocar de jogo ou app). */
  resetHistory(): void {
    this.history = [];
  }

  getHistory(): ReadonlyArray<ChatMessage> {
    return this.history;
  }

  private formatScreenContext(ctx: ScreenContext): string {
    const parts: string[] = [];
    if (ctx.appReadableName) parts.push(`App atual: ${ctx.appReadableName}`);
    if (ctx.visibleTexts?.length) {
      parts.push(`Textos visíveis: ${ctx.visibleTexts.slice(0, 10).join(', ')}`);
    }
    return parts.length ? `\n\nContexto de tela:\n${parts.join('\n')}` : '';
  }

  private pushHistory(role: ChatMessage['role'], content: string): void {
    this.history.push({ role, content, timestamp: Date.now() });
    const max = this.config.maxHistoryMessages;
    if (this.history.length > max) {
      this.history = this.history.slice(this.history.length - max);
    }
  }

  private async callAPI(systemPrompt: string): Promise<string> {
    // Ollama espera o system prompt como a primeira mensagem da lista,
    // com role "system", seguido do histórico normal.
    const messages = [
      { role: 'system', content: systemPrompt },
      ...this.history.map(({ role, content }) => ({ role, content })),
    ];

    const url = `${this.config.ollamaBaseUrl}/api/chat`;

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: this.config.model,
        messages,
        stream: false, // resposta completa de uma vez, mais simples de consumir
        options: {
          num_predict: 400, // limite de tokens gerados, equivalente a max_tokens
        },
      }),
    });

    if (!response.ok) {
      const errText = await response.text().catch(() => '');
      throw new Error(
        `Capri (Ollama) error ${response.status} em ${url}: ${errText}\n` +
          `Verifique se o Ollama está rodando no servidor e acessível nesse endereço.`
      );
    }

    const data = await response.json();
    return data.message?.content ?? '(sem resposta)';
  }

  private inferMood(text: string): CapriMood {
    const lower = text.toLowerCase();
    if (/[!]{1,3}$/.test(text.trim())) return 'happy';
    if (lower.includes('cuidado') || lower.includes('atenção')) return 'alert';
    return 'talking';
  }
}
