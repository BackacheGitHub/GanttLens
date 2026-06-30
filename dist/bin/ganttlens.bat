@echo off
REM GanttLens - PlantUML Gantt Chart Analyzer
REM Wrapper script for Windows

setlocal

REM Determine the script's location
set "SCRIPT_DIR=%~dp0"

REM Check if JAVA_HOME is set
if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java"
) else (
    REM Try to find java in PATH
    where java >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        set "JAVA_CMD=java"
    ) else (
        echo Error: Java is not installed or JAVA_HOME is not set.
        echo Please install JDK 17 or later and set JAVA_HOME.
        exit /b 1
    )
)

REM Set the JAR location
set "JAR_FILE=%SCRIPT_DIR%..\lib\ganttlens-cli.jar"

REM Check if JAR exists
if not exist "%JAR_FILE%" (
    echo Error: GanttLens JAR not found at: %JAR_FILE%
    echo Please ensure the distribution is properly installed.
    exit /b 1
)

REM Run GanttLens
"%JAVA_CMD%" -jar "%JAR_FILE%" %*

endlocal
