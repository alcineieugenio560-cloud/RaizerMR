# RaizerMR – projeto para virar APK

Tudo (loja, menus, janelas 3D, mapa, navegador, mão, tela de escolha do VR) já está em `www/index.html`.
As bibliotecas (three.js e MediaPipe) estão em `www/lib`, então o app funciona **sem internet**.

## Jeito mais fácil: gerar o APK na nuvem (sem instalar nada)
1. Crie uma conta gratuita em github.com e um repositório novo (pode ser privado).
2. Envie **todo o conteúdo desta pasta** para o repositório (botão *Add file → Upload files*; inclua a pasta `.github`).
3. Abra a aba **Actions → "Gerar APK do RaizerMR" → Run workflow**.
4. Espere uns 5 a 10 minutos. Quando terminar, abra a execução e baixe **RaizerMR-APK** (dentro vem o `app-debug.apk`).
5. Copie o APK para o celular e instale (permita "instalar apps de fontes desconhecidas").

## Jeito alternativo: Android Studio
```
npm install
npx cap add android
bash scripts/patch-android.sh
npx cap sync android
npx cap open android      # Build > Build APK(s)
```

## O que o app faz no celular
- Abre direto na tela **RaizerMR**: escolha VR Box, Shinecon VR, Cardboard, outro óculos ou tela normal, e ajuste os gráficos.
- Fica sempre deitado, em tela cheia e com a tela ligada.
- Pede permissão da câmera na primeira vez.
- Controles Bluetooth: pareie nas configurações do Android. Eles aparecem como teclado ou gamepad e funcionam (A/Enter clica; B/X abre e fecha o menu).
- O botão "Bluetooth" do app (Web Bluetooth) **não existe** dentro do APK, é limitação do Android WebView.
- O modo "AR real" (WebXR) também não existe dentro do APK; ele só funciona no Chrome.

## Observações
- O APK gerado é de teste (debug). Para publicar na Play Store é preciso assinar um release.
- Se quiser trocar o nome ou o pacote, edite `capacitor.config.json` (`appId`, `appName`) e o pacote em `scripts/patch-android.sh`.
