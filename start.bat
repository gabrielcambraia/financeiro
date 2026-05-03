@echo off
echo ================================
echo    Financeiro - Iniciando...
echo ================================

REM Inicia o backend em uma nova janela
echo Iniciando backend (porta 8080)...
start "Financeiro Backend" cmd /k "cd /d %~dp0backend && start-backend.bat"

REM Aguarda o backend subir
echo Aguardando backend iniciar...
timeout /t 20 /nobreak >nul

REM Inicia o frontend em uma nova janela
echo Iniciando frontend (porta 5173)...
start "Financeiro Frontend" cmd /k "cd frontend && npm run dev"

echo.
echo ================================
echo  Backend:  http://localhost:8080
echo  Frontend: http://localhost:5173
echo ================================
echo.
echo Abrindo no navegador em 5 segundos...
timeout /t 5 /nobreak >nul
start http://localhost:5173

echo.
echo Para parar, feche as janelas "Financeiro Backend" e "Financeiro Frontend"
pause
