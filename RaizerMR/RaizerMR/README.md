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
| 5 | Aplicativos: Relógio, Calculadora, Configurações já funcionam. YouTube, Google, Maps, Navegador, Galeria, Vídeos e Arquivos virão via WebView/MediaStore | parcial |
| 6-7 | Rastreamento de mãos (MediaPipe) e gestos | pendente |
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

## Limitações conhecidas

- O código foi escrito sem acesso ao SDK Android, então a primeira compilação real é a do GitHub Actions.
- Não há central de notificações, depth das janelas, nem rastreamento de mãos ainda.
