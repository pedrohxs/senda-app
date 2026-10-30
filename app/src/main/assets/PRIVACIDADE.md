# Privacidade

## Resumo
O Senda funciona inteiro no seu aparelho. Não existe conta, não existe nuvem e o aplicativo não pede permissão de internet. Nada do que você faz aqui sai daqui.

## O que fica guardado
- Os grupos que você criou: apps, sites, palavras-chave, horários e limites.
- O tempo de uso por app monitorado e por dia, para mostrar os seus relatórios.
- Os motivos que você escreveu nos desafios, quando essa opção está ativa.
- As últimas 300 ações do serviço de proteção, usadas no diagnóstico.
- As suas preferências de proteção, como PIN do responsável (guardado com PBKDF2) e pausas.

## Onde fica guardado
Tudo fica em armazenamento privado do aplicativo, na pasta interna do Senda. Outros apps não conseguem ler esses arquivos.

## Rede
O Senda não declara a permissão android.permission.INTERNET. Sem ela, o sistema operacional bloqueia qualquer conexão de rede, mesmo que houvesse uma tentativa. É por isso que não há sincronização, telemetria, anúncios ou análise de uso.

## Backup
O backup é um arquivo JSON que você mesmo escolhe onde salvar. A cópia automática diária fica em Download/Senda, com as sete mais recentes. O PIN nunca é exportado: o arquivo guarda apenas se existia um PIN configurado. Quem tiver o arquivo consegue ler os seus grupos e o seu histórico, então trate-o como um documento pessoal.

## Permissões e o que elas fazem
- Acessibilidade: identifica qual app está na frente para aplicar as regras.
- Sobreposição: desenha a tela de bloqueio sobre o app.
- Notificações: mantém o serviço de proteção visível e ativo.
- Administrador do aparelho: impede a desinstalação acidental enquanto a proteção estiver ativa.
- Ignorar otimização de bateria: evita que o sistema encerre o serviço em segundo plano.

## Remoção dos dados
Desinstalar o Senda apaga os dados internos do aplicativo. Os backups continuam onde você os salvou, porque são arquivos seus. Excluir um grupo pelo editor apaga junto os apps, horários e limites dele.

## Dúvidas
Abra Ajustes, Sobre, Diagnóstico e exporte o relatório: ele mostra o estado real da proteção sem incluir o seu histórico de uso.
