@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0\.."
if not exist out mkdir out
set "SOURCE_LIST=%TEMP%\libgrampocket-sources-%RANDOM%.txt"
for /r src %%F in (*.java) do (
    set "SOURCE=%%F"
    set "SOURCE=!SOURCE:\=/!"
    echo "!SOURCE!" >> "%SOURCE_LIST%"
)
for /r test %%F in (*.java) do (
    set "SOURCE=%%F"
    set "SOURCE=!SOURCE:\=/!"
    echo "!SOURCE!" >> "%SOURCE_LIST%"
)
javac -Xlint:all -d out @"%SOURCE_LIST%"
set "COMPILE_RESULT=%ERRORLEVEL%"
del "%SOURCE_LIST%" >nul 2>nul
if not "%COMPILE_RESULT%"=="0" exit /b %COMPILE_RESULT%
set /a PASSED=0
set /a FAILED=0
for %%F in (test\*Test.java) do (
    set "TEST=%%~nF"
    if "!TEST!"=="StoragePathsTest" (
        java -cp out !TEST! "%CD%"
    ) else (
        java -cp out !TEST!
    )
    if errorlevel 1 (
        echo FAIL !TEST!
        set /a FAILED+=1
    ) else (
        echo PASS !TEST!
        set /a PASSED+=1
    )
)
echo Summary: !PASSED! passed, !FAILED! failed
if not "!FAILED!"=="0" exit /b 1
exit /b 0