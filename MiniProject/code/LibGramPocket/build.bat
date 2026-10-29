@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
call test\run-tests.bat
if errorlevel 1 exit /b %ERRORLEVEL%
if not exist out\app mkdir out\app
set "SOURCE_LIST=%TEMP%\libgrampocket-build-%RANDOM%.txt"
for /r src %%F in (*.java) do (
	set "SOURCE=%%F"
	set "SOURCE=!SOURCE:\=/!"
	echo "!SOURCE!" >> "%SOURCE_LIST%"
)
javac -Xlint:all -d out\app @"%SOURCE_LIST%"
set "COMPILE_RESULT=%ERRORLEVEL%"
del "%SOURCE_LIST%" >nul 2>nul
if not "%COMPILE_RESULT%"=="0" exit /b %COMPILE_RESULT%
jar cfe LibGramPocket.jar Main -C out\app .
if errorlevel 1 exit /b %ERRORLEVEL%
echo Built LibGramPocket.jar