@echo off
setlocal

SET MAVEN_VERSION=3.9.6
SET MAVEN_DIR=%~dp0.maven\apache-maven-%MAVEN_VERSION%
SET MVN=%MAVEN_DIR%\bin\mvn.cmd

IF EXIST "%MVN%" GOTO RUN

echo Maven nao encontrado. Baixando Maven %MAVEN_VERSION%...
IF NOT EXIST "%~dp0.maven" mkdir "%~dp0.maven"

powershell -Command "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%~dp0.maven\maven.zip'"

IF ERRORLEVEL 1 (
    echo Falha ao baixar Maven. Verifique sua conexao.
    pause
    exit /b 1
)

echo Extraindo Maven...
powershell -Command "Expand-Archive -Path '%~dp0.maven\maven.zip' -DestinationPath '%~dp0.maven' -Force"
del "%~dp0.maven\maven.zip"
echo Maven pronto!

:RUN
echo Iniciando backend Spring Boot...
"%MVN%" spring-boot:run

pause
