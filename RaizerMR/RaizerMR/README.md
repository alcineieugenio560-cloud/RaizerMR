# RaizerMR

Sistema de realidade mista para Android (projeto independente, sem relação com a Meta).
Kotlin + OpenGL ES 2.0, sem dependências externas nesta fase.

## Estado do desenvolvimento

| Etapa | Descrição | Estado |
|---|---|---|
| 1 | Estrutura Android + tela inicial | feito |
| 2 | Ambiente 3D (estrelas, piso em grade, câmera por giroscópio, estéreo) | feito |
| 3 | Janelas 3D fixas no mundo (barra de título, fechar/minimizar/maximizar, animações) | feito |
| 4 | Mover e redimensionar (arrastar pela barra, alça no canto inferior direito) | feito (falta controle de profundidade) |
| 5 | Aplicativos: YouTube, Google, Google Maps, Navegador (WebView real em janela 3D), Mãos, Relógio, Calculadora, Configurações. Faltam Galeria, Reprodutor de vídeos e Explorador de arquivos | parcial |
| 6 | Rastreamento de mãos: câmera (CameraX) + MediaPipe Hand Landmarker, esqueleto, mão esquerda/direita, calibração | feito |
| 7 | Gestos: pinça (clicar/arrastar), apontar, mão aberta/fechada, redimensionar com duas mãos | feito |
| 8 | Configurações avançadas (taxa de atualização, idioma, calibração) | parcial |
| 9-10 | Testes em aparelho e correções | pendente |

## Como compilar

Push para o GitHub: o workflow `.github/workflows/build.yml` instala Java 17, Android SDK e
Gradle 8.9, roda `gradle assembleDebug` e publica o APK em **Actions > execução > Artifacts**.
Localmente: `gradle assembleDebug` (ou gere o wrapper com `gradle wrapper`).

## Como usar

- **INICIAR** abre o ambiente VR. **Voltar** sai.
- Modo normal: toque nos botões; arraste a barra de título para mover; arraste o canto
  inferior direito para redimensionar. Sem giroscópio, arraste no espaço vazio para olhar.
- Modo estéreo (Configurações): tela dividida para óculos; o cursor fica no centro do olhar e
  um toque na tela clica/segura. Ainda sem correção de distorção das lentes.
- Barra do sistema: Home (recentraliza), Aplicativos, Configurações, Recentes, Wi-Fi, bateria, hora.
- As janelas ficam fixas em coordenadas do mundo (yaw/pitch/distância), nunca seguem a câmera.
  Limite de segurança: inclinação vertical limitada a ~51° e largura entre 0,7 m e 4,2 m.

## Arquitetura (app/src/main/java/com/raizer/mr)

- `MainActivity`, `Prefs`: tela inicial e preferências.
- `vr/VrActivity`, `VrSurfaceView`: tela cheia, ciclo de vida, toque.
- `vr/HeadTracker`: giroscópio -> matriz de orientação (ajustada para landscape).
- `vr/VrRenderer`, `GlProgram`: shaders, câmera, estéreo, desenho.
- `vr/VrWindow`, `WindowManager3D`: janelas em coordenadas esféricas do mundo, raio-plano, foco, animações.
- `vr/WindowContent` (+ `Host`, `AppId`): contrato dos apps; `SystemBarApp`, `LauncherApp`,
  `RecentsApp`, `SettingsApp`, `ClockApp`, `CalculatorApp`; `Icons`, `Theme`.

## Mãos (como usar)

1. Abra o app **Mãos** (ou Configurações > Aplicativos) e ligue o rastreamento; aceite a permissão da câmera.
2. Câmera **Traseira** é a do uso em óculos (ela enxerga suas mãos); **Frontal** serve para testar com o celular na mão.
3. Aponte com o indicador (cursor), **pinça** = clicar, pinça e mover = arrastar janelas / rolar páginas.
4. Pinça com as duas mãos sobre uma janela e afastar/aproximar = redimensionar.
5. **Calibrar pinça**: abra a mão por ~1,5 s, depois faça a pinça por ~1,5 s. Os limites ficam salvos.
6. Opcional: "Mão aberta recentraliza" (segure a mão aberta por 2 s).
O esqueleto das mãos aparece no ambiente 3D e na prévia do app Mãos. Sem modelo, sem câmera ou sem
permissão, o status explica o motivo e o modo por toque continua funcionando (nada é simulado).

O modelo `hand_landmarker.task` é baixado pelo workflow durante o build. Para compilar localmente,
coloque-o em `app/src/main/assets/`.

## Apps web

YouTube, Google, Maps e Navegador usam o WebView do Android com user-agent de desktop. Barra própria
(voltar, avançar, recarregar, endereço/pesquisa, início). Login do Google costuma ser bloqueado em
WebViews embutidos. Rolagem e cliques funcionam por toque ou por pinça.

## Limitações conhecidas

- O código foi escrito sem acesso ao SDK Android, então a primeira compilação real é a do GitHub Actions.
- Ainda não há central de notificações, controle de profundidade, Galeria, Vídeos e Arquivos.
- Teclado do WebView pode não abrir; use a barra de endereço (campo de texto nativo) para digitar.
- Vídeo do YouTube depende do WebView do aparelho; teste em aparelho real.
