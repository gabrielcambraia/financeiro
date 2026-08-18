# Console do Windows usa codepage legada por padrão (850/1252); o Java escreve
# os logs em UTF-8, e sem isso os acentos saem como mojibake ("├º├úo").
chcp 65001 > $null

$envFile = Join-Path $PSScriptRoot ".env.local"

if (-not (Test-Path $envFile)) {
    Write-Error "Arquivo $envFile não encontrado. Copie .env.local.example para .env.local e ajuste os valores."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $key, $value = $_ -split '=', 2
    Set-Item -Path "env:$key" -Value $value
}

& "$PSScriptRoot\.maven\apache-maven-3.9.6\bin\mvn.cmd" -f "$PSScriptRoot\pom.xml" spring-boot:run
