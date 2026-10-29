// src/types/index.ts
// Tipos compartilhados do "cérebro" da Capri.

export type CapriMood =
  | 'idle'
  | 'thinking'
  | 'talking'
  | 'happy'
  | 'alert'
  | 'focused';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
}

export interface ScreenContext {
  // Preenchido pelo módulo nativo de Accessibility (camada futura).
  // Aqui o chat/IA só CONSOME esse contexto, não captura nada sozinho.
  appPackageName?: string;
  appReadableName?: string;
  visibleTexts?: string[];
  focusedElement?: string;
  timestamp: number;
}

export interface GameTipRequest {
  gameName: string;
  question: string;
  screenContext?: ScreenContext;
}

export interface CapriResponse {
  text: string;
  mood: CapriMood;
  shouldSpeak: boolean;
}

export interface CapriConfig {
  /** URL base do servidor Ollama, ex: "http://SEU_SERVIDOR:11434" */
  ollamaBaseUrl: string;
  /** Nome do modelo no Ollama, ex: "llama3.1", "mistral" */
  model: string;
  maxHistoryMessages: number;
  personality: string;
}
