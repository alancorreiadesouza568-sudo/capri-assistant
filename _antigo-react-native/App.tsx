// App.tsx
// Componente raiz do app React Native. Fecha o ciclo completo:
//   CapriAccessibilityService (Kotlin) -> evento onScreenContextChanged
//   -> este arquivo decide se pede dica ao CapriBrain (TS puro)
//   -> resposta volta para OverlayService via CapriBridge.showSpeechBubble
//
// Esta tela também serve de painel simples para ligar/desligar o overlay
// e configurar a URL do servidor Ollama.

import React, { useEffect, useRef, useState } from 'react';
import {
  NativeEventEmitter,
  NativeModules,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { CapriBrain } from './src/ai/CapriBrain';
import { ScreenContext } from './src/types';

const { CapriBridge } = NativeModules;
const capriEvents = new NativeEventEmitter(CapriBridge);

// Intervalo mínimo entre dicas automáticas disparadas por mudança de
// tela, para não spammar o usuário a cada toque/rolagem.
const MIN_MS_BETWEEN_AUTO_TIPS = 15_000;

export default function App() {
  const [ollamaUrl, setOllamaUrl] = useState('http://192.168.0.100:11434');
  const [currentGame, setCurrentGame] = useState('');
  const [status, setStatus] = useState('Overlay parado.');
  const [lastContext, setLastContext] = useState<ScreenContext | null>(null);

  const brainRef = useRef<CapriBrain | null>(null);
  const lastAutoTipAtRef = useRef(0);

  useEffect(() => {
    // Escuta o contexto de tela emitido pelo CapriAccessibilityService
    // (via CapriBridgeModule.emitEvent em Kotlin).
    const subscription = capriEvents.addListener(
      'onScreenContextChanged',
      (data: {
        appPackageName?: string;
        visibleTexts?: string[];
        focusedElementText?: string;
        timestamp: number;
      }) => {
        const ctx: ScreenContext = {
          appPackageName: data.appPackageName,
          appReadableName: data.appPackageName, // refinar depois com um mapa pacote->nome
          visibleTexts: data.visibleTexts ?? [],
          focusedElement: data.focusedElementText,
          timestamp: data.timestamp,
        };
        setLastContext(ctx);
        maybeOfferAutoTip(ctx);
      }
    );

    return () => subscription.remove();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentGame]);

  function ensureBrain(): CapriBrain {
    if (!brainRef.current) {
      brainRef.current = new CapriBrain(ollamaUrl);
    }
    return brainRef.current;
  }

  /**
   * Chamado a cada mudança de tela. Decide se vale a pena oferecer uma
   * dica automática (throttle por tempo) — mantém a Capri discreta em
   * vez de reagir a cada evento de acessibilidade.
   */
  async function maybeOfferAutoTip(ctx: ScreenContext) {
    if (!currentGame) return; // só oferece dica automática com um jogo definido

    const now = Date.now();
    if (now - lastAutoTipAtRef.current < MIN_MS_BETWEEN_AUTO_TIPS) return;
    lastAutoTipAtRef.current = now;

    try {
      const brain = ensureBrain();
      const response = await brain.getGameTip({
        gameName: currentGame,
        question: 'Com base no que está na tela agora, alguma dica rápida?',
        screenContext: ctx,
      });
      CapriBridge.showSpeechBubble(response.text);
    } catch (err) {
      console.warn('Falha ao pedir dica automática:', err);
    }
  }

  function handleStartOverlay() {
    CapriBridge.startOverlay();
    setStatus('Overlay ativo.');
  }

  function handleStopOverlay() {
    CapriBridge.stopOverlay();
    setStatus('Overlay parado.');
  }

  async function handleManualTip() {
    try {
      const brain = ensureBrain();
      const response = currentGame
        ? await brain.getGameTip({
            gameName: currentGame,
            question: 'Me dê uma dica geral agora.',
            screenContext: lastContext ?? undefined,
          })
        : await brain.chat('Oi, Capri!');
      CapriBridge.showSpeechBubble(response.text);
    } catch (err: any) {
      CapriBridge.showSpeechBubble('Não consegui falar com o servidor agora.');
      console.warn(err);
    }
  }

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Capri — Painel</Text>

      <Text style={styles.label}>Servidor Ollama</Text>
      <TextInput
        style={styles.input}
        value={ollamaUrl}
        onChangeText={setOllamaUrl}
        placeholder="http://SEU_SERVIDOR:11434"
        autoCapitalize="none"
      />

      <Text style={styles.label}>Jogo atual (opcional)</Text>
      <TextInput
        style={styles.input}
        value={currentGame}
        onChangeText={setCurrentGame}
        placeholder="ex: Elden Ring"
        autoCapitalize="none"
      />

      <View style={styles.row}>
        <TouchableOpacity style={styles.button} onPress={handleStartOverlay}>
          <Text style={styles.buttonText}>Iniciar overlay</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.button} onPress={handleStopOverlay}>
          <Text style={styles.buttonText}>Parar overlay</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.buttonPrimary} onPress={handleManualTip}>
        <Text style={styles.buttonText}>Pedir dica agora</Text>
      </TouchableOpacity>

      <Text style={styles.status}>{status}</Text>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, backgroundColor: '#121212' },
  title: { fontSize: 22, fontWeight: 'bold', color: '#FFF', marginBottom: 20 },
  label: { color: '#AAA', marginTop: 12, marginBottom: 4 },
  input: {
    backgroundColor: '#1E1E1E',
    color: '#FFF',
    borderRadius: 8,
    padding: 10,
  },
  row: { flexDirection: 'row', gap: 10, marginTop: 20 },
  button: {
    flex: 1,
    backgroundColor: '#333',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonPrimary: {
    marginTop: 12,
    backgroundColor: '#7C4DFF',
    padding: 14,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonText: { color: '#FFF', fontWeight: '600' },
  status: { color: '#888', marginTop: 20, textAlign: 'center' },
});
