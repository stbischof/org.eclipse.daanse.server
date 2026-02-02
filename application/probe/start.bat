@echo off
setlocal

REM Path to Java-Binary
set "JAVA_EXEC=java"

REM Logback-Configuration-File
set "LOGBACK_CONFIG=.\logback.xml"


REM Check if java -version works
"%JAVA_EXEC%" -version >nul 2>&1
if errorlevel 1 (
    echo "Java is installed but not working properly (java -version failed)."
    exit /b 1
)

REM Check Logback configuration file exists
if not exist "%LOGBACK_CONFIG%" (
    echo "Logback configuration file "%LOGBACK_CONFIG%" not found."
    exit /b 1
)

REM Start Server
echo "Starting server..."
"%JAVA_EXEC%" -Dlogback.configurationFile=file:"%LOGBACK_CONFIG%" -jar daanse.probe.jar

endlocal
