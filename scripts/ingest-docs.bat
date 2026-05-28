@echo off
REM ═══════════════════════════════════════════════════════════════
REM  Spector File Ingestion Script
REM  Discovers and ingests files from the repo using FileIngestionMain.
REM  All configuration is read from spector.yml (or CLI overrides).
REM
REM  Usage: scripts\ingest-docs.bat [--pattern "**\*.java"] [--root path]
REM ═══════════════════════════════════════════════════════════════

set SPECTOR_HOME=%~dp0..
set JAR=%SPECTOR_HOME%\spector-dist\target\spector.jar
set CONFIG=%SPECTOR_HOME%\spector.yml

if not exist "%JAR%" (
    echo [ERROR] Fat JAR not found: %JAR%
    echo [INFO]  Run: mvn package -pl spector-dist -am -DskipTests
    exit /b 1
)

echo ═══════════════════════════════════════════════════
echo  Spector — File Ingestion
echo ═══════════════════════════════════════════════════

java ^
    --add-modules jdk.incubator.vector ^
    --enable-native-access=ALL-UNNAMED ^
    --enable-preview ^
    -cp "%JAR%" ^
    com.spectrayan.spector.ingestion.FileIngestionMain ^
    --config "%CONFIG%" ^
    %*

echo.
echo [Done] Ingestion complete.
