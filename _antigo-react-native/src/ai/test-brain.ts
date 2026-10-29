// src/ai/test-brain.ts
// Teste manual da camada de IA, isolado — roda direto no Termux com:
//   npx ts-node src/ai/test-brain.ts
// Sem precisar de Android Studio, emulador ou nada nativo.
// Serve pra validar o "cérebro" antes de integrar com overlay/accessibility.
//
// Pré-requisito: Ollama rodando em algum lugar acessível (seu servidor,
// ou até localhost se estiver testando no próprio PC/servidor onde o
// Ollama está instalado) com um modelo já baixado, ex:
//   ollama pull llama3.1

import { CapriBrain } from './CapriBrain';

async function main() {
  const ollamaUrl = process.env.OLLAMA_URL;
  if (!ollamaUrl) {
    console.error(
      'Defina OLLAMA_URL no ambiente antes de rodar, ex:\n' +
        '  export OLLAMA_URL=http://SEU_SERVIDOR:11434'
    );
    process.exit(1);
  }

  const capri = new CapriBrain(ollamaUrl);

  console.log('--- Teste 1: chat casual ---');
  const r1 = await capri.chat('E aí, Capri, tudo certo?');
  console.log(`[${r1.mood}] ${r1.text}`);

  console.log('\n--- Teste 2: dica de jogo, sem contexto de tela ---');
  const r2 = await capri.getGameTip({
    gameName: 'Elden Ring',
    question: 'Qual a melhor build pra iniciante focada em magia?',
  });
  console.log(`[${r2.mood}] ${r2.text}`);

  console.log('\n--- Teste 3: dica de jogo, COM contexto de tela simulado ---');
  const r3 = await capri.getGameTip({
    gameName: 'Elden Ring',
    question: 'O que eu faço agora?',
    screenContext: {
      appReadableName: 'Elden Ring',
      visibleTexts: ['HP: 20%', 'Chefe: Margit', 'Frasco: 2 restantes'],
      timestamp: Date.now(),
    },
  });
  console.log(`[${r3.mood}] ${r3.text}`);

  console.log('\n--- Histórico acumulado ---');
  console.log(JSON.stringify(capri.getHistory(), null, 2));
}

main().catch((err) => {
  console.error('Erro no teste:', err);
  process.exit(1);
});
