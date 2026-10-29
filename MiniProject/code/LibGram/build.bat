@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac -Xlint:all -d out src\DemoData.java src\Main.java src\data\*.java src\model\*.java src\services\*.java src\ui\*.java
if errorlevel 1 exit /b %ERRORLEVEL%
call test\run-tests.bat
if errorlevel 1 exit /b %ERRORLEVEL%
jar cfe LibGram.jar Main -C out .
echo Built LibGram.jar
