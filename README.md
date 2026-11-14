# Kwai Fácil

## Preparar os vídeos

Escolha uma pasta com pares de nomes iguais, como `001.mp4` e `001.txt`. As legendas devem estar em UTF-8, com até 32 KB por arquivo. O app lê MP4, MOV, M4V e WEBM. A linha opcional `CAPA_SEGUNDOS=3` indica o momento do vídeo a selecionar na capa.

Para usar o mesmo nome de novela na fila, marque **Inserir nome na capa** e digite o texto. O app usa o primeiro estilo do editor de capas do Kwai, centraliza o nome sobre a cena e tenta incliná-lo para cima à direita. Se precisar ajustar manualmente, o Kwai Fácil pede que você confira o texto e toque em ✓ no editor de capa antes de continuar.

## Preparar e conferir uma publicação

1. Ative o serviço de acessibilidade do Kwai Fácil nas configurações do Android.
2. Faça o primeiro teste com **Publicar a fila automaticamente** desmarcado.
3. Se precisar colar a legenda manualmente, abra o vídeo na fila e toque em **Copiar legenda**, quando disponível.
4. Se o Kwai não confirmar a publicação, a primeira conferência no perfil começa 70 segundos após o toque em **Publicar**. As próximas começam 30 segundos após o fim da conferência anterior. Após três conferências sem confirmação, a fila pausa para revisão.

## Compilação

Requer JDK 17, Android SDK 35 e Gradle 8.9. Execute `gradle assembleDebug` a partir desta pasta. O keystore incluído é apenas de desenvolvimento. Preserve-o para instalar futuras atualizações sobre a mesma instalação de teste.
