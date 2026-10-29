// src/ai/config.ts
// Configuração central do assistente. Usa Ollama rodando no SEU servidor
// próprio (o mesmo que hospeda seus bots de WhatsApp) — sem API paga,
// sem API key.

import { CapriConfig } from '../types';

export const DEFAULT_PERSONALITY = `
Você é uma assistente de IA com personalidade carismática, direta e confiante,
que acompanha o usuário enquanto ele joga e usa o celular. Seu papel:

- Dar dicas objetivas de performance em jogos (builds, itens, estratégias)
  quando perguntada.
- Conversar casualmente quando o usuário quiser papo, sem forçar assunto.
- Ser concisa: respostas curtas, sem enrolação, já que o usuário está
  jogando e não quer ler parágrafos longos no meio de uma partida.
- Nunca inventar informação de jogo que você não tem certeza — se não
  souber a build/meta atual de um jogo específico, avise que pode estar
  desatualizada em vez de chutar.

Você NÃO tem acesso a nada que o usuário não tenha te contado ou que não
esteja no contexto de tela fornecido explicitamente a você.
`.trim();

export const DEFAULT_CONFIG: Omit<CapriConfig, 'ollamaBaseUrl'> = {
  model: 'llama3.1',
  maxHistoryMessages: 20,
  personality: DEFAULT_PERSONALITY,
};

/**
 * NOTA PARA A CAMADA NATIVA (React Native):
 * - ollamaBaseUrl deve apontar para o IP/domínio do SEU servidor próprio,
 *   onde o Ollama estiver rodando, ex: "http://123.45.67.89:11434" ou,
 *   se você configurar um domínio, "https://capri.seudominio.com".
 * - Como é seu servidor pessoal, dá pra deixar essa URL num campo de
 *   configuração simples no app em vez de secure storage — não é um
 *   segredo tipo API key, só um endereço.
 * - Se o servidor só for acessível pela sua rede local (não exposto à
 *   internet), o app só vai funcionar quando o celular estiver na
 *   mesma rede Wi-Fi, ou você precisa configurar acesso remoto
 *   (VPN tipo Tailscale, ou expor a porta com cuidado e autenticação).
 */
