# Senda - Time Control and Block Apps

Aplicativo Android nativo para foco consciente, controle de tempo de tela e bloqueio de hábitos automáticos.

Tudo fica no aparelho: **sem conta, sem nuvem e sem permissão de internet**.

## O que o Senda faz

- **Grupos de apps** com regras próprias de tempo e abertura.
- **Sessão compartilhada**: apps do mesmo grupo dividem a mesma sessão e o mesmo tempo restante.
- **Limites personalizáveis** de tempo diário, duração de sessão, aberturas por dia e tempo de espera.
- **Desafios antes de abrir**: respiração guiada, matemática (fácil, médio ou difícil) ou justificativa consciente.
- **Programação semanal** com janelas de Limite, Bloqueio estrito e Pausa.
- **Bolha flutuante** arrastável com o ícone do app atual e o tempo restante da sessão.
- **Modo Blindado**: proteção contra desinstalação, alteração de relógio e edições impulsivas.
- **Início informativo** com a sessão em andamento, o próximo bloqueio do dia e o progresso de cada grupo.
- **Relatórios** por semana, inclusive anteriores, com exportação em CSV.
- **Teste de proteção** de 3 segundos, que confere permissões, serviço e sobreposição.
- **Sem listas prontas**: só é bloqueado o que você cadastrar (apps, sites e palavras-chave).

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Room, DataStore e WorkManager
- Android AccessibilityService e Foreground Service
- Fontes Outfit e Inter

## Requisitos

- Android Studio
- JDK 17
- Android SDK 36
- Android 8.0 (API 26) ou superior

## Como compilar

```bash
./gradlew assembleDebug
```

O APK de debug é gerado em `app/build/outputs/apk/debug/app-debug.apk`. Para instalar em um aparelho conectado:

```bash
./gradlew installDebug
```

## Permissões necessárias

- **Acessibilidade**: detectar a abertura dos apps monitorados e dos sites bloqueados.
- **Sobreposição**: exibir pausas, bloqueios e a bolha flutuante.
- **Notificações**: manter a proteção visível e avisar se a acessibilidade for desativada.
- **Administrador do dispositivo** (opcional): impedir desinstalações impulsivas.

O serviço de acessibilidade é usado **só** para identificar qual app ou site está em
primeiro plano e aplicar as regras configuradas. Ele não lê o que você digita, não
captura a tela, não registra histórico de navegação e não envia nada para fora do
aparelho — o app não declara a permissão de internet.

## Estrutura

```text
app/src/main/java/com/senda/app/
├── core/          # utilitários e chaves de uso
├── data/          # Room, DataStore, repositórios e permissões
├── di/            # injeção de dependências
├── domain/        # regras de política, sessões, segurança e desafios
├── service/       # acessibilidade, foreground service, notificações e overlays
└── ui/            # telas Compose, componentes, tema e overlays

scripts/           # scripts de build (release assinada)
.github/           # integração contínua
```

## Release assinada

Cada pessoa que publicar uma build precisa da **própria chave**. O build de release
falha de propósito quando ela não existe — assim ninguém publica, sem perceber, um
APK assinado com a chave de debug (que é pública e igual em todo computador).

Crie `keystore.properties` na raiz do projeto (não versionado, já está no `.gitignore`):

```properties
storeFile=keystore/senda-release.jks
storePassword=SUA_SENHA
keyAlias=senda
keyPassword=SUA_SENHA
```

E gere o `.jks` uma única vez:

```bash
keytool -genkeypair -v -keystore keystore/senda-release.jks -alias senda \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
```

Depois:

```powershell
# Windows: roda testes, compila com R8 e copia o APK com SHA-256
powershell -ExecutionPolicy Bypass -File scripts\release.ps1
```

```bash
# Linux/macOS
./gradlew assembleRelease
```

Para gerar um APK de release assinado com a chave de debug — **apenas para teste
local, nunca para distribuir** — use a saída explícita:

```bash
./gradlew assembleRelease -Psenda.allowDebugSigning=true
```

> Quem já tem o app instalado com outra assinatura precisa desinstalar uma vez antes de instalar esta build; depois as atualizações funcionam por cima.

## Estado atual

- As novidades de cada versão ficam em `app/src/main/assets/CHANGELOG.md` e aparecem em `Ajustes → Sobre → Novidades`.
- Estado da proteção e últimos eventos: `Ajustes → Sobre → Diagnóstico`, com exportação em `.txt`.
- Histórico de uso é mantido por 365 dias; o resto é descartado na abertura.

## Licença

Senda é software livre sob a **GNU General Public License v3.0**. Veja [LICENSE](LICENSE) para o texto completo.

```text
Copyright (C) 2026 pedrohxs

Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
os termos da GNU General Public License, versão 3, publicada pela Free Software
Foundation. Este programa é distribuído na esperança de ser útil, mas SEM
QUALQUER GARANTIA, sem mesmo a garantia implícita de COMERCIALIZAÇÃO ou
ADEQUAÇÃO A UM PROPÓSITO ESPECÍFICO.
```

Se você distribuir uma versão modificada, ela precisa continuar sob a GPL-3.0 e
manter disponível o código-fonte correspondente.
