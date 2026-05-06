@echo off
setlocal enabledelayedexpansion
title Financeiro

echo ================================
echo    Financeiro - Iniciando...
echo ================================
echo.

REM ── Verifica Java ──────────────────────────────────────────────────────────
java -version >nul 2>&1
IF ERRORLEVEL 1 GOTO INSTALAR_JAVA

REM Verifica versao minima (Java 21)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER=%%v"
)
set "JAVA_VER=!JAVA_VER:"=!"
for /f "delims=." %%m in ("!JAVA_VER!") do set "JAVA_MAJOR=%%m"
IF !JAVA_MAJOR! LSS 21 GOTO INSTALAR_JAVA
GOTO JAVA_OK

:INSTALAR_JAVA
echo Java 21 nao encontrado. Instalando via winget...
echo (Isso pode levar alguns minutos na primeira vez)
echo.
winget install --id Microsoft.OpenJDK.21 --source winget --accept-source-agreements --accept-package-agreements
IF ERRORLEVEL 1 (
    echo.
    echo Falha ao instalar Java automaticamente.
    echo Por favor, baixe o Java 21 manualmente em: https://adoptium.net
    pause
    exit /b 1
)
echo.
echo Java instalado! Reiniciando o script...
echo Por favor, feche esta janela e execute o start.bat novamente.
pause
exit /b 0

:JAVA_OK
echo Java encontrado (versao !JAVA_MAJOR!).

REM ── Localiza o JAR ─────────────────────────────────────────────────────────
SET JAR_PATH=%~dp0backend\target\backend-1.0.0.jar
SET MVN=%~dp0backend\.maven\apache-maven-3.9.6\bin\mvn.cmd

IF EXIST "%JAR_PATH%" GOTO INICIAR

REM ── Primeiro uso: compila o projeto ────────────────────────────────────────
echo.
echo Primeira execucao detectada. Compilando o projeto...
echo (Isso pode levar alguns minutos: baixa dependencias e builda o frontend)
echo.

IF NOT EXIST "%MVN%" (
    echo Baixando Maven...
    IF NOT EXIST "%~dp0backend\.maven" mkdir "%~dp0backend\.maven"
    powershell -Command "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%~dp0backend\.maven\maven.zip'"
    IF ERRORLEVEL 1 (
        echo Falha ao baixar Maven. Verifique sua conexao com a internet.
        pause
        exit /b 1
    )
    powershell -Command "Expand-Archive -Path '%~dp0backend\.maven\maven.zip' -DestinationPath '%~dp0backend\.maven' -Force"
    del "%~dp0backend\.maven\maven.zip"
)

echo Compilando...
cd /d "%~dp0backend"
"%MVN%" package -DskipTests
IF ERRORLEVEL 1 (
    echo.
    echo Falha na compilacao. Verifique os erros acima.
    pause
    exit /b 1
)
cd /d "%~dp0"

:INICIAR
echo.
echo ================================
echo  Iniciando Financeiro...
echo  Acesse: http://localhost:8080
echo ================================
echo.
echo (Para encerrar, feche esta janela)
echo.

REM Abre o navegador apos 5 segundos
start /b cmd /c "timeout /t 5 /nobreak >nul && start http://localhost:8080"

REM Inicia o backend (bloqueia ate fechar)
java -jar "%JAR_PATH%"

pause
