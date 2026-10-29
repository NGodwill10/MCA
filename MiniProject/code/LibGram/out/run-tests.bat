@echo off
setlocal
cd /d "%~dp0\.."
if not exist out mkdir out
javac -Xlint:all -d out src\DemoData.java src\Main.java src\data\*.java src\model\*.java src\services\*.java src\ui\*.java
if errorlevel 1 exit /b %ERRORLEVEL%
javac -cp out -d out test\*.java
if errorlevel 1 exit /b %ERRORLEVEL%
set failed=0
for %%T in (AccountServiceTest CatalogServiceTest LendingServiceTest MemberServiceTest ChargeCalculatorTest CsvUtilTest RepositoryRecoveryTest PermissionMatrixTest MigrationTest InvariantSequenceTest PhaseOneSmokeTest PhaseSixBillingTest PhaseSixWorkflowTest PhaseEightNotificationTest PhaseNineHardeningTest ReservationServiceTest PerformanceSmokeTest StoragePathsTest) do call :run %%T
if %failed% equ 0 (echo PASS: all LibGram tests) else (echo FAIL: one or more LibGram tests failed & exit /b 1)
exit /b 0
:run
java -cp out %1 "%CD%"
if errorlevel 1 set failed=1
exit /b 0
