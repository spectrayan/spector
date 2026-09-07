#!/usr/bin/env bash
# Copyright 2026 Spectrayan — Apache 2.0
# Regenerates Python and TypeScript Client SDKs from docs/openapi.yaml
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

echo "[Spector] Generating Python Client SDK..."
mvn org.openapitools:openapi-generator-maven-plugin:7.25.0:generate \
    "-Dopenapi.generator.maven.plugin.inputSpec=docs/openapi.yaml" \
    "-Dopenapi.generator.maven.plugin.generatorName=python" \
    "-Dopenapi.generator.maven.plugin.output=target/temp-gen-python" \
    "-Dopenapi.generator.maven.plugin.packageName=spector_client.generated"

mkdir -p "sdks/python/src/spector_client/generated"
cp -r target/temp-gen-python/spector_client/generated/* sdks/python/src/spector_client/generated/
rm -rf target/temp-gen-python

echo "[Spector] Generating TypeScript Client SDK..."
mvn org.openapitools:openapi-generator-maven-plugin:7.25.0:generate \
    "-Dopenapi.generator.maven.plugin.inputSpec=docs/openapi.yaml" \
    "-Dopenapi.generator.maven.plugin.generatorName=typescript-fetch" \
    "-Dopenapi.generator.maven.plugin.output=target/temp-gen-ts" \
    "-Dopenapi.generator.maven.plugin.configOptions=typescriptThreePlus=true,supportsES6=true"

mkdir -p "sdks/typescript/spector-client/src/generated"
cp -r target/temp-gen-ts/* sdks/typescript/spector-client/src/generated/
rm -rf target/temp-gen-ts

echo "[Spector] SDK generation complete."
