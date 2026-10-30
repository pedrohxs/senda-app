# Novidades do Senda

## 0.4.0 · 2026-10-30
- O app virou software livre: licença GPL-3.0.
- Saiu a lista embutida de domínios adultos. Agora só é bloqueado o que você cadastrar em Bloqueios; a tela ficou sem o cartão de filtro adulto e o APK ficou bem menor.
- Backups antigos continuam válidos: a preferência do filtro é simplesmente ignorada.

## 0.3.0 · 2026-10-30
- O filtro adulto deixou de ocupar quase 12 MB no aparelho: o app lê um índice compacto direto do APK (3,8 MB) e apaga a cópia antiga.
- APK de release assinado com chave própria, gerado por um script só seu.
- Notificações do serviço separadas do restante do código, para o bloqueio ficar mais fácil de manter.
- Documentação reorganizada, com índice e um guia de release.

## 0.2.1 · 2026-10-30
- O editor de grupo voltou para a tela única de antes: resumo no topo, cartão de apps, folhas de Limites e Desafio, programação semanal e "Outras opções".
- Tudo o mais da 0.2.0 continua igual: Sobre, testar proteção, estados padronizados, Início informativo e Relatórios por semana.
- A dica de horários agora diz o que o app faz de verdade: fora das janelas cadastradas, o uso é livre.

## 0.2.0 · 2026-10-30
- Ajustes sem promessas falsas: saíram "Tema escuro" e "Idioma", que não faziam nada.
- Seção Sobre de verdade: versão, novidades, licenças, privacidade, diagnóstico e teste de proteção.
- Testar proteção mostra uma sobreposição real por 3 segundos e confere acessibilidade, serviço e batimento.
- Início informativo: sessão ativa com tempo restante, próximo bloqueio do dia e progresso por grupo.
- Relatórios com semanas anteriores, exportação em CSV e métricas que explicam a cobertura dos dias.
- Editor de grupo dividido em seções, com validação na hora, confirmação ao excluir e desfazer ao remover um app.
- Estados de carregando, vazio e erro padronizados, com botão de tentar de novo em todas as telas principais.

## 0.1.11 · 2026-10-24
- Mudança de fuso horário e ajuste automático de hora não contam mais como adulteração do relógio.
- PIN do responsável agora usa PBKDF2, com código de recuperação de uso único.
- Diagnóstico exporta um relatório com as últimas 300 ações do serviço.
- Backup automático diário em Download/Senda, com oferta de restauração na primeira abertura.
- O app avisa quando o banco de dados local apresenta problema.

## 0.1.8 · 2026-10-16
- Bloqueio rígido de sites deixa de reabrir a página bloqueada.
- Relatórios mostram o detalhe de cada dia e o que a cobertura significa.
- Botão Voltar dentro da página bloqueada volta para a Home, sem piscar.

## 0.1.7 · 2026-10-08
- Sobreposição opaca, com pausa do áudio de fundo enquanto o bloqueio está na tela.
- Transições mais calmas entre a intervenção e o app liberado.

## 0.1.6 · 2026-09-30
- Bloqueio rígido de sites por domínio e palavra-chave.
- Relatórios com histórico parcial e média dos dias completos.

## 0.1.5 · 2026-09-20
- Fricção progressiva apenas no desafio de respiração.
- Teclado próprio no desafio de matemática.
- Bloqueio não reaparece em cima da própria Home do Senda.

## 0.1.1 · 2026-09-05
- Pausa temporária de um grupo por 1 hora, 4 horas ou até amanhã.
- Sessão interrompida quando o limite diário chega ao fim.
- Reinício do aparelho volta com a proteção de pé.

## 0.1.0 · 2026-08-25
- Primeira versão: grupos de apps, limites diários, sessões com desafio e tela de bloqueio nativa.
- Backup manual em arquivo e restauração transacional.
