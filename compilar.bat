@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
if not exist "out" mkdir "out"
if exist "fontes.txt" del "fontes.txt"
rem Caminhos relativos com / evitam escapes de \ no arquivo de argumentos do javac.
for /R src %%F in (*.java) do (
    set "fonte=%%F"
    set "fonte=!fonte:%CD%\=!"
    set "fonte=!fonte:\=/!"
    >>"fontes.txt" echo "!fonte!"
)
javac -encoding UTF-8 -d "out" @fontes.txt
if errorlevel 1 exit /b 1
echo Compilacao concluida. Classes em out\
