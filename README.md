Monitor RCP - Sistema de Análise de RCP
Monitor RCP é uma solução Android composta por um aplicativo móvel e um aplicativo para Wear OS, desenvolvida para auxiliar no treinamento e execução de manobras de Ressuscitação Cardiopulmonar (RCP). O sistema utiliza os sensores do relógio inteligente para capturar dados de movimento em tempo real e fornecer feedback visual, sonoro e tátil sobre a qualidade das compressões.

📋 Funcionalidades
⌚ Módulo Wear OS
Captura de Sensores: Coleta dados de Acelerômetro e Giroscópio em alta frequência.

Feedback em Tempo Real: Analisa a magnitude do movimento localmente para indicar se o ritmo está "Lento", "Rápido" ou "Perfeito" diretamente na tela do relógio.

Metrônomo Tátil: Vibração periódica para guiar o ritmo ideal de 110 BPM.

Envio de Dados: Transmite pacotes de dados (chunks) e o resultado final para o celular via Bluetooth (WearableListenerService).

📱 Módulo Mobile
Processamento de Sinais: Recebe os dados brutos e aplica filtros digitais (Butterworth Passa-Alta e Passa-Baixa) para limpar o sinal.

Cálculo de Métricas: Calcula a frequência (CPM), profundidade das compressões (via dupla integração da aceleração) e o retorno do tórax (recoil).

Feedback Sonoro e Visual: Emite alertas de voz (Text-to-Speech) como "Acelere o ritmo" ou "Ritmo correto" durante o procedimento.

Histórico e Análise: Salva os resultados em um banco de dados local (Room) e exibe gráficos de evolução da frequência e profundidade.

Exportação: Permite exportar os dados detalhados de cada teste em formato CSV.

Guia Instrucional: Contém um tutorial visual passo a passo sobre como realizar a RCP.

🛠 Tecnologias Utilizadas
O projeto foi escrito inteiramente em Kotlin e utiliza as tecnologias modernas do Jetpack Android:

UI: Jetpack Compose (Mobile) e Wear Compose (Wear OS).

Arquitetura: MVVM (Model-View-ViewModel).

Banco de Dados: Room Database.

Comunicação: Google Play Services Wearable Data Layer API.

Processamento Matemático: Apache Commons Math (para interpolação Spline).

Assincronicidade: Kotlin Coroutines & Flow.

📂 Estrutura do Projeto
/mobile: Contém o código do aplicativo para smartphone (processamento pesado, histórico, gráficos).

/wear: Contém o código para o smartwatch (coleta de sensores, feedback tátil).

/gradle: Configurações de dependências e versões centralizadas (libs.versions.toml).

🚀 Como Executar
Abra o projeto no Android Studio.

Sincronize o projeto com os arquivos Gradle.

Para testar o fluxo completo:

Execute o módulo wear em um emulador ou dispositivo físico Wear OS.

Execute o módulo mobile em um emulador ou dispositivo físico Android pareado.

No relógio, inicie a captura. O celular deve exibir o status "Teste em Andamento" e fornecer feedback.
