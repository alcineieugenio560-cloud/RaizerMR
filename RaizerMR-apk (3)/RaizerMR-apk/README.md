# RaizerMR – projeto para virar APK

Tudo (dock, loja, janelas, cenários, YouTube, Google, navegador, mão, escolha do VR) está em `www/index.html`.
As bibliotecas (three.js e MediaPipe) estão em `www/lib`, então o app roda **sem internet** (YouTube, Google e mapas precisam de internet).

## Gerar o APK na nuvem (sem instalar nada)
1. Crie uma conta grátis em github.com e um repositório novo.
2. Envie **todo o conteúdo desta pasta** (inclua `.github`; se ela não subir, crie o arquivo `.github/workflows/build-apk.yml` no site e cole o conteúdo).
3. Aba **Actions → Gerar APK do RaizerMR → Run workflow**.
4. Quando ficar verde, abra a execução e baixe **RaizerMR-APK** (dentro está o `app-debug.apk`). Extraia o zip, passe o APK para o celular e instale.

## Android Studio (alternativa)
```
npm install
npx cap add android
bash scripts/patch-android.sh
npx cap sync android
npx cap open android
```

## Como usar no app
- Tela inicial: escolha o óculos (VR Box, Shinecon, Cardboard, outro ou tela normal) e ajuste os gráficos.
- **Tela normal:** dock embaixo (Loja, Cenários, Mapear, Janela, Relógio, VR, Limpar, Ajustes). Os botões do topo aparecem quando você toca na tela e somem sozinhos.
- **Clique com a mão:** abra a pinça e feche (1 clique). Ou deixe o ponteiro parado em um botão por ~1 segundo.
- **Google:** pesquisa e atalhos abrem o Google de verdade (aba do navegador do celular).
- **YouTube:** busca dentro do app, toca o vídeo na janela. Se a busca falhar, o app oferece abrir o YouTube no celular.
- **Modo VR:** menu 3D; sem mãos, o ponto central mira e o clique é por permanência.
- Controle Bluetooth: pareie nas configurações do Android (A/Enter clica; B/X mostra ou esconde o menu).
- O botão Bluetooth do app e o AR real (WebXR) só existem no Chrome, não no APK.

## Observações
- O APK é de teste (debug). Para a Play Store é preciso assinar um release.
- Para trocar nome/pacote: `capacitor.config.json` e `scripts/patch-android.sh`.
