@echo off
setlocal

if defined TTN_JAVA8_HOME (
    set "TTN_SELECTED_JDK=%TTN_JAVA8_HOME%"
) else (
    set "TTN_SELECTED_JDK=%USERPROFILE%\.jdks\temurin-8u504-b01"
)

if not exist "%TTN_SELECTED_JDK%\bin\java.exe" (
    echo ERROR: Java 8 runtime not found at "%TTN_SELECTED_JDK%".
    echo Set TTN_JAVA8_HOME to a verified Java 8 JDK.
    exit /b 1
)

if not exist "%TTN_SELECTED_JDK%\bin\javac.exe" (
    echo ERROR: Java compiler not found at "%TTN_SELECTED_JDK%".
    echo TTN_JAVA8_HOME must point to a JDK, not a JRE.
    exit /b 1
)

for /f "tokens=2" %%V in ('"%TTN_SELECTED_JDK%\bin\javac.exe" -version 2^>^&1') do set "TTN_JAVAC_VERSION=%%V"
echo %TTN_JAVAC_VERSION% | findstr /b /c:"1.8." >nul
if errorlevel 1 (
    echo ERROR: Expected javac 1.8.x but found "%TTN_JAVAC_VERSION%".
    exit /b 1
)

set "JAVA_HOME=%TTN_SELECTED_JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "TTN_PREPARE_LEGACY_RUNTIME="
for %%A in (%*) do (
    if /I "%%~A"=="runClient" set "TTN_PREPARE_LEGACY_RUNTIME=1"
    if /I "%%~A"=="runServer" set "TTN_PREPARE_LEGACY_RUNTIME=1"
    if /I "%%~A"=="test" set "TTN_PREPARE_LEGACY_RUNTIME=1"
    if /I "%%~A"=="build" set "TTN_PREPARE_LEGACY_RUNTIME=1"
    if /I "%%~A"=="check" set "TTN_PREPARE_LEGACY_RUNTIME=1"
    if /I "%%~A"==":test" set "TTN_PREPARE_LEGACY_RUNTIME=1"
    if /I "%%~A"==":build" set "TTN_PREPARE_LEGACY_RUNTIME=1"
    if /I "%%~A"==":check" set "TTN_PREPARE_LEGACY_RUNTIME=1"
)

rem Preparation must use the requested Gradle home, not silently warm the default.
call :selectGradleHome %*

if defined TTN_PREPARE_LEGACY_RUNTIME (
    echo Preparing the mapped Forge 1.12.2 recomp runtime...
    call "%~dp0gradlew.bat" prepareLegacyForgeRuntime
    if errorlevel 1 exit /b 1
)

call "%~dp0gradlew.bat" %*
exit /b %ERRORLEVEL%

:selectGradleHome
if "%~1"=="" exit /b 0
if /I "%~1"=="--gradle-user-home" (
    set "GRADLE_USER_HOME=%~2"
    shift
) else if /I "%~1"=="-g" (
    set "GRADLE_USER_HOME=%~2"
    shift
) else (
    for /f "tokens=1,* delims==" %%G in ("%~1") do (
        if /I "%%G"=="--gradle-user-home" set "GRADLE_USER_HOME=%%H"
    )
)
shift
goto selectGradleHome
