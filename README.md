# Capri Assistant (Android nativo)

Bolinha flutuante com IA que dá dicas de jogo. Toque nela = dica. Arraste = move.

## Gerar o APK pelo celular (GitHub Actions)
1. Crie um repositório no GitHub e suba esta pasta.
2. Aba **Actions → Build APK → Run workflow**.
3. Quando terminar, baixe o artefato **capri-apk** (zip com o `app-debug.apk`).
4. Instale o APK (permita "instalar de fontes desconhecidas").

## Chave da IA (grátis)
Pegue em https://aistudio.google.com/apikey e cole no painel do app.
Se der erro 404, confira o nome do modelo no painel.

## Pasta `_antigo-react-native`
Código TypeScript/Ollama da versão anterior. Não entra no APK; pode apagar.
