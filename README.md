# Kwai Fácil — versão 1.1 (verificação automática)

Este pacote contém o código-fonte Android atualizado. A versão 1.1 melhora o reconhecimento de confirmação e confere o perfil automaticamente se o Kwai não avisar que publicou.

## O que mudou

- Ícone novo com mascote caricatural de celular e vídeo.
- Leitura de pares de arquivos sem diferenciar maiúsculas/minúsculas (`001.mp4` + `001.TXT` também funciona).
- Mensagem de erro mais clara quando um vídeo ou legenda não pode ser lido.
- Proteção quando a fila é retomada sem o destino do Kwai selecionado.
- A fila continua aguardando uma confirmação explícita do Kwai antes de iniciar a checagem do perfil.
- A fila só marca um item como publicado após encontrar um dos sinais explícitos configurados no serviço.
- Textos de confirmação são normalizados para ignorar acentos, caixa e pontuação, e passam a aceitar outras mensagens explícitas de publicação.
- Eventos de sucesso só são aceitos depois do toque em Publicar e depois do início da tentativa atual; notificações antigas não concluem a fila.
- Se não chegar confirmação explícita, a primeira checagem do perfil ocorre após 1 minuto; a segunda e a terceira ocorrem 30 segundos depois uma da outra. Se encontrar a legenda no perfil, marca como publicado e continua a fila.
- Se não encontrar a legenda nas três checagens, tenta publicar o vídeo mais uma vez. Se a segunda tentativa também não puder ser confirmada, pausa a fila para revisão.

## Preparar os vídeos

Escolha uma pasta contendo pares com nomes iguais, por exemplo `001.mp4` e `001.txt`, `002.mp4` e `002.txt`. Legendas devem estar em UTF-8. Extensões de vídeo aceitas: MP4, MOV, M4V e WEBM. Cada legenda pode ter até 32 KB. Subpastas não são lidas.

A linha opcional `CAPA_SEGUNDOS=3` no TXT define o tempo sugerido para a capa. A capa continua experimental e deve ser conferida no Kwai antes de publicar.

## Uso seguro

1. Escolha a pasta e confira os itens importados.
2. Ative o serviço de acessibilidade do Kwai Fácil nas configurações do Android.
3. Faça o primeiro teste com a publicação automática desmarcada.
4. Mantenha o celular desbloqueado, o Kwai em primeiro plano e a conexão ativa.
5. Se a publicação não for confirmada, confira o perfil do Kwai antes de marcar “Não foi: tentar de novo”. Isso evita duplicar um vídeo que possa ter sido enviado.

O Android ou uma mudança na interface do Kwai pode impedir uma etapa automática. O app procura a legenda na tela do perfil; se a interface do Kwai não expuser a legenda para acessibilidade, após as três checagens ele fará uma única nova tentativa. Se a segunda tentativa continuar sem confirmação, pausará para revisão manual.

## Compilação

Requer JDK 17, Android SDK 35 e Gradle 8.9. Execute `gradle assembleDebug` a partir desta pasta. O keystore incluído é apenas de desenvolvimento; preserve-o para instalar futuras atualizações sobre a mesma instalação de teste. Não use essa chave para distribuição pública.
