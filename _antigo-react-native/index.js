/**
 * index.js
 * Ponto de entrada padrão de um app React Native — registra o
 * componente App (App.tsx) para o Metro bundler e o runtime Android.
 */
import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';

AppRegistry.registerComponent(appName, () => App);
