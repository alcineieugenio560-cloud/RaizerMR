# RaizerMR – projeto para virar APK

Tudo está em `www/index.html` (menus, loja, janelas, YouTube, Google, navegador, mãos, tela inicial, idiomas).
As bibliotecas (three.js e MediaPipe) estão em `www/lib`: o app abre sem internet. YouTube, Google e páginas da web precisam de internet.

## Gerar o APK na nuvem (sem instalar nada)
1. Crie uma conta grátis em github.com e um repositório novo.
2. Envie **todo o conteúdo desta pasta** (inclua `.github`; se ela não subir, crie `.github/workflows/build-apk.yml` no site e cole o conteúdo).
3. Aba **Actions → Gerar APK do RaizerMR → Run workflow**.
4. Quando ficar verde, baixe **RaizerMR-APK** (dentro está o `app-debug.apk`), extraia e instale no celular.

## Android Studio (alternativa)
```
npm install
npx cap add android
bash scripts/patch-android.sh
npx cap sync android
npx cap open android
```

## Como usar
- O app abre deitado. Tela inicial: **JOGAR** e, embaixo, **CONFIGURAR**.
- **Configurar:** versão **Normal** ou **Lite**, óculos (VR Box, Shinecon, Cardboard, outro ou tela normal), qualidade, ajustes das lentes e **idioma** (Português, English, Español).
- **Lite:** mais leve. Câmera pequena, 1 mão, rastreamento em esqueleto de pontos (poucos quadros por segundo), sem mapeamento do local e sem molduras de redimensionar.
- **Clique:** pinça (polegar + indicador) abrindo e fechando = 1 clique na ponta do dedo. Ou deixe o ponteiro parado ~1 segundo sobre o botão.
- **Segurar janela, menu ou borda:** abra a mão e FECHE a mão em cima da barra branca. Fica segurando até você abrir a mão.
- **Tela normal:** dock embaixo; os botões do topo aparecem ao tocar na tela.
- **VR:** menu e janelas planas (estilo papel). YouTube, Navegador e Google funcionam em 3D no VR.
  - Navegador/Google no VR: modo leitura (texto e links) com resultados do DuckDuckGo quando o Google não responde.
  - YouTube no VR: busca e lista dentro do painel; o vídeo depende de servidores de terceiros e pode falhar (tem botão para assistir em tela normal).
- **Controle do VR Box / Shinecon** (pareie nas configurações do Android): A/Enter clica no ponto central; **B ou gatilho (segure)** pega a janela, você olha para onde quer pôr e solta (analógico para cima/baixo muda o tamanho); X mostra/esconde o menu; Y entra/sai do VR. No teclado: G ou B (segure) pega a janela, setas para cima/baixo mudam o tamanho.

## Observações
- Powered By RN Studio aparece na tela inicial e na tela de abertura (splash). O ícone e a splash usam a logo branca sem fundo claro.
- APK de teste (debug). Para a Play Store é preciso assinar um release.
- Nome/pacote: `capacitor.config.json` e `scripts/patch-android.sh`.
