@echo off
setlocal
cd /d "%~dp0"
if not exist LibGramPocket.jar (
	echo LibGramPocket.jar is missing. Run build.bat first.
	exit /b 1
)
java -jar LibGramPocket.jar %*