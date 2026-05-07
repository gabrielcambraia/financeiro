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

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VER=%%v"
set "JAVA_VER=!JAVA_VER:"=!"
for /f "delims=." %%m in ("!JAVA_VER!") do set "JAVA_MAJOR=%%m"
IF !JAVA_MAJOR! LSS 21 GOTO INSTALAR_JAVA
GOTO JAVA_OK

:INSTALAR_JAVA
echo Java 21 nao encontrado. Instalando automaticamente...
echo (Pode levar alguns minutos na primeira vez)
echo.
winget install --id Microsoft.OpenJDK.21 --source winget --accept-source-agreements --accept-package-agreements
IF ERRORLEVEL 1 (
    echo.
    echo Nao foi possivel instalar o Java automaticamente.
    echo Baixe manualmente em: https://adoptium.net
    pause
    exit /b 1
)
echo.
echo Java instalado! Por favor, feche esta janela e execute o start.bat novamente.
pause
exit /b 0

:JAVA_OK
echo Java encontrado.
echo.
echo ================================
echo  Iniciando Financeiro...
echo  Acesse: http://localhost:8080
echo ================================
echo.
echo (Para encerrar, feche esta janela)
echo.

start /b cmd /c "timeout /t 4 /nobreak >nul && start http://localhost:8080"

java -jar "%~dp0financeiro.jar"

pause
