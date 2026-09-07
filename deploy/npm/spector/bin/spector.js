#!/usr/bin/env node
/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { spawn, execSync } = require('child_process');
const readline = require('readline');

const GITHUB_REPO = 'spectrayan/spector';
const DEFAULT_PORT = process.env.SPECTOR_PORT || '7070';
const DEFAULT_HOST = process.env.SPECTOR_HOST || '127.0.0.1';
const SPECTOR_HOME = process.env.SPECTOR_HOME || path.join(os.homedir(), '.spector');
const BIN_DIR = path.join(SPECTOR_HOME, 'bin');
const JAR_PATH = path.join(BIN_DIR, 'spector.jar');

function checkServerOnline(host, port, timeoutMs = 800) {
  return new Promise((resolve) => {
    const req = http.request(
      {
        host,
        port,
        path: '/api/v1/engine/status',
        method: 'GET',
        timeout: timeoutMs,
      },
      (res) => {
        resolve(res.statusCode === 200 || res.statusCode === 204);
      }
    );
    req.on('error', () => resolve(false));
    req.on('timeout', () => {
      req.destroy();
      resolve(false);
    });
    req.end();
  });
}

function runHttpMcpBridge(host, port) {
  process.stderr.write(`[spector-npx] Connected to active Spector Synapse daemon at http://${host}:${port}\n`);
  process.stderr.write(`[spector-npx] Ready for Model Context Protocol (MCP) JSON-RPC over stdio.\n`);

  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false,
  });

  rl.on('line', (line) => {
    const trimmed = line.trim();
    if (!trimmed) return;

    const req = http.request(
      {
        host,
        port,
        path: '/mcp',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          'Content-Length': Buffer.byteLength(trimmed),
        },
      },
      (res) => {
        let body = '';
        res.setEncoding('utf8');
        res.on('data', (chunk) => {
          body += chunk;
        });
        res.on('end', () => {
          if (body.trim()) {
            process.stdout.write(body.trim() + '\n');
          }
        });
      }
    );

    req.on('error', (err) => {
      process.stderr.write(`[spector-npx] MCP bridge error: ${err.message}\n`);
    });

    req.write(trimmed);
    req.end();
  });
}

function checkJavaAvailable() {
  try {
    const output = execSync('java -version 2>&1', { encoding: 'utf8' });
    return output.includes('Runtime Environment') || output.includes('OpenJDK') || output.includes('Java(TM)');
  } catch {
    return false;
  }
}

async function downloadReleaseJar() {
  if (!fs.existsSync(BIN_DIR)) {
    fs.mkdirSync(BIN_DIR, { recursive: true });
  }

  process.stderr.write(`[spector-npx] Fetching latest release metadata from GitHub (${GITHUB_REPO})...\n`);

  return new Promise((resolve, reject) => {
    const options = {
      host: 'api.github.com',
      path: `/repos/${GITHUB_REPO}/releases/latest`,
      headers: { 'User-Agent': '@spectrayan/spector-npx' },
    };

    https.get(options, (res) => {
      let data = '';
      res.on('data', (chunk) => (data += chunk));
      res.on('end', () => {
        try {
          const release = JSON.parse(data);
          if (!release.assets || !Array.isArray(release.assets)) {
            return reject(new Error('No release assets found in latest release.'));
          }

          const jarAsset = release.assets.find((a) => a.name === 'spector.jar' || a.name.endsWith('-cli.jar') || a.name.endsWith('.jar'));
          if (!jarAsset) {
            return reject(new Error('spector.jar was not found among release assets.'));
          }

          process.stderr.write(`[spector-npx] Downloading ${jarAsset.name} to ${JAR_PATH}...\n`);
          const file = fs.createWriteStream(JAR_PATH);
          const download = (url) => {
            https.get(url, { headers: { 'User-Agent': '@spectrayan/spector-npx' } }, (resp) => {
              if (resp.statusCode === 302 || resp.statusCode === 301) {
                return download(resp.headers.location);
              }
              resp.pipe(file);
              file.on('finish', () => {
                file.close(() => {
                  process.stderr.write(`[spector-npx] Download complete (${(fs.statSync(JAR_PATH).size / (1024 * 1024)).toFixed(1)} MB).\n`);
                  resolve();
                });
              });
            }).on('error', reject);
          };

          download(jarAsset.browser_download_url);
        } catch (e) {
          reject(e);
        }
      });
    }).on('error', reject);
  });
}

function runJavaStandalone(args) {
  const javaArgs = [
    '--enable-preview',
    '--add-modules=jdk.incubator.vector',
    '--enable-native-access=ALL-UNNAMED',
    '-jar',
    JAR_PATH,
    ...args,
  ];

  const proc = spawn('java', javaArgs, {
    stdio: 'inherit',
    env: process.env,
  });

  proc.on('exit', (code) => {
    process.exit(code ?? 0);
  });
}

async function main() {
  const args = process.argv.slice(2);
  const command = args[0] || 'mcp';

  if (command === '--help' || command === '-h' || command === 'help') {
    console.log(`
Spector Zero-Install MCP & CLI Runner (@spectrayan/spector)

Usage:
  npx -y @spectrayan/spector [command] [options]

Commands:
  mcp            Run Model Context Protocol server over stdio (default)
  doctor         Inspect local environment and connectivity
  init           Generate starter ~/.spector/spector.yml configuration
  [args...]      Forward arguments directly to underlying spector.jar

Options:
  --host <host>  Spector Synapse host (default: 127.0.0.1)
  --port <port>  Spector Synapse port (default: 7070)
  --help, -h     Show this help message
`);
    return;
  }

  // 1. Check if a local Spector Synapse daemon is online
  const isOnline = await checkServerOnline(DEFAULT_HOST, DEFAULT_PORT);

  if (isOnline) {
    if (command === 'mcp') {
      runHttpMcpBridge(DEFAULT_HOST, DEFAULT_PORT);
      return;
    }
  }

  // 2. Fallback to Java spector.jar
  const hasJava = checkJavaAvailable();
  if (!hasJava) {
    process.stderr.write(`
[spector-npx] Notice: No local Spector daemon detected on http://${DEFAULT_HOST}:${DEFAULT_PORT}, and Java 25 was not found on PATH.

To use Spector:
  1. Start the Docker container (Recommended):
     docker compose up -d

  2. Or install OpenJDK 25:
     macOS:   brew install openjdk@25
     Windows: winget install Microsoft.OpenJDK.25
     Linux:   sudo apt install openjdk-25-jdk

  3. Or run against a remote Spector instance:
     export SPECTOR_HOST="my-remote-server"
     export SPECTOR_PORT="7070"
     npx -y @spectrayan/spector mcp
\n`);
    process.exit(1);
  }

  // Ensure spector.jar is downloaded
  if (!fs.existsSync(JAR_PATH)) {
    try {
      await downloadReleaseJar();
    } catch (err) {
      process.stderr.write(`[spector-npx] Error acquiring spector.jar: ${err.message}\n`);
      process.stderr.write(`[spector-npx] Please build from source with: mvn clean package -DskipTests\n`);
      process.exit(1);
    }
  }

  runJavaStandalone(args);
}

main().catch((err) => {
  process.stderr.write(`[spector-npx] Fatal error: ${err.message}\n`);
  process.exit(1);
});
