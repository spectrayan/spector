@echo off
REM Copyright 2026 Spectrayan — Apache 2.0
REM Regenerates Python and TypeScript Client SDKs from docs/openapi.yaml

setlocal enabledelayedexpansion
set "REPO_ROOT=%~dp0.."
cd "%REPO_ROOT%"

echo [Spector] Generating Python Client SDK...
call mvn org.openapitools:openapi-generator-maven-plugin:7.25.0:generate ^
    "-Dopenapi.generator.maven.plugin.inputSpec=docs/openapi.yaml" ^
    "-Dopenapi.generator.maven.plugin.generatorName=python" ^
    "-Dopenapi.generator.maven.plugin.output=target/temp-gen-python" ^
    "-Dopenapi.generator.maven.plugin.packageName=spector_client.generated"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to generate Python client.
    exit /b %ERRORLEVEL%
)

xcopy /E /I /Y "target\temp-gen-python\spector_client\generated" "sdks\python\src\spector_client\generated"
rmdir /S /Q "target\temp-gen-python"

echo [Spector] Generating TypeScript Client SDK...
call mvn org.openapitools:openapi-generator-maven-plugin:7.25.0:generate ^
    "-Dopenapi.generator.maven.plugin.inputSpec=docs/openapi.yaml" ^
    "-Dopenapi.generator.maven.plugin.generatorName=typescript-fetch" ^
    "-Dopenapi.generator.maven.plugin.output=target/temp-gen-ts" ^
    "-Dopenapi.generator.maven.plugin.configOptions=typescriptThreePlus=true,supportsES6=true"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to generate TypeScript client.
    exit /b %ERRORLEVEL%
)

xcopy /E /I /Y "target\temp-gen-ts\*" "sdks\typescript\spector-client\src\generated\"
rmdir /S /Q "target\temp-gen-ts"

echo [Spector] SDK generation complete.
