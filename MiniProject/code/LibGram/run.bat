@echo off
setlocal
cd /d "%~dp0"
if not exist LibGram.jar call build.bat
if errorlevel 1 exit /b %ERRORLEVEL%
java -jar LibGram.jar
