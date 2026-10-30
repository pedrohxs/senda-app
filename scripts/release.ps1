#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Gera o APK de release do Senda, assinado, e copia para a Área de Trabalho.

.DESCRIPTION
    Roda os testes unitários, compila com R8 e publica o APK com o nome
    `Senda-<versão>.apk`, mostrando tamanho e SHA-256.

    A assinatura vem de `keystore.properties` + pasta `keystore/` (ambos fora do
    git). Sem esses arquivos o Gradle cai na assinatura de debug — o script
    avisa quando isso acontece.

.PARAMETER Destination
    Pasta de destino do APK. Padrão: Área de Trabalho (OneDrive, quando existir).

.PARAMETER SkipTests
    Pula os testes unitários (use só em build de conferência rápida).

.EXAMPLE
    pwsh scripts/release.ps1
    pwsh scripts/release.ps1 -SkipTests
#>
[CmdletBinding()]
param(
    [string] $Destination,
    [switch] $SkipTests
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $Destination) {
    $desktop = Join-Path $env:USERPROFILE 'OneDrive\Desktop'
    $Destination = if (Test-Path $desktop) { $desktop } else { Join-Path $env:USERPROFILE 'Desktop' }
}

$gradle = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path $gradle)) { throw "gradlew.bat não encontrado em $repoRoot" }

$env:_JAVA_OPTIONS = ''
# R8 precisa de folga, mas heap grande demais estoura a memória nativa em
# máquina de 16 GB com emulador aberto. 3 GB + 2 workers é o ponto estável.
$env:GRADLE_OPTS = '-Dorg.gradle.jvmargs=-Xmx3g -Dorg.gradle.workers.max=2'

$hasKey = (Test-Path (Join-Path $repoRoot 'keystore.properties'))
if ($hasKey) {
    Write-Host 'Assinatura: chave de release (keystore.properties).' -ForegroundColor Green
} else {
    Write-Host 'Aviso: keystore.properties ausente — o APK sairá assinado com a chave de debug.' -ForegroundColor Yellow
    Write-Host '        Veja docs/RELEASE.md para criar a chave própria.' -ForegroundColor Yellow
}

if (-not $SkipTests) {
    Write-Host 'Rodando testes unitários...' -ForegroundColor Cyan
    & $gradle ':app:testDebugUnitTest' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw 'Testes unitários falharam.' }
}

Write-Host 'Compilando release com R8...' -ForegroundColor Cyan
# Daemons antigos seguram memória, e o R8 pode derrubar a JVM por falta dela.
& $gradle '--stop' | Out-Null
& $gradle ':app:assembleRelease' '--console=plain'
if ($LASTEXITCODE -ne 0) { throw 'Build de release falhou.' }

$metadataPath = Join-Path $repoRoot 'app\build\outputs\apk\release\output-metadata.json'
$apkPath = Join-Path $repoRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apkPath)) { throw "APK não encontrado em $apkPath" }

$versionName = (Get-Content $metadataPath -Raw | ConvertFrom-Json).elements[0].versionName
$versionCode = (Get-Content $metadataPath -Raw | ConvertFrom-Json).elements[0].versionCode
$target = Join-Path $Destination "Senda-$versionName.apk"

Copy-Item $apkPath $target -Force

$sizeMb = [math]::Round((Get-Item $target).Length / 1MB, 1)
$sha256 = (Get-FileHash $target -Algorithm SHA256).Hash

Write-Host ''
Write-Host "APK:        $target" -ForegroundColor Green
Write-Host "Versão:     $versionName (build $versionCode)"
Write-Host "Tamanho:    $sizeMb MB"
Write-Host "SHA-256:    $sha256"
Write-Host ''
Write-Host ('Para instalar por USB: adb install -r "{0}"' -f $target) -ForegroundColor Cyan
