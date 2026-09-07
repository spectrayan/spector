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

const GITHUB_REPO = 'spectrayan/spector';

function parseCliArgs(argv) {
  const args = argv.slice(2);
  let host = process.env.SPECTOR_HOST || '127.0.0.1';
  let port = process.env.SPECTOR_PORT || '7070';
  let printHttpConfig = false;
  let showHelp = false;
  const forwardedArgs = [];

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--help' || arg === '-h' || arg === 'help') {
      showHelp = true;
    } else if (arg === '--host') {
      if (i + 1 < args.length) host = args[++i];
    } else if (arg.startsWith('--host=')) {
      host = arg.slice('--host='.length);
    } else if (arg === '--port') {
      if (i + 1 < args.length) port = args[++i];
    } else if (arg.startsWith('--port=')) {
      port = arg.slice('--port='.length);
    } else if (arg === '--print-http-config') {
      printHttpConfig = true;
    } else {
      forwardedArgs.push(arg);
    }
  }

  return { host, port, printHttpConfig, showHelp, forwardedArgs };
}

function parseJavaMajorVersion(versionOutput) {
  if (!versionOutput) return null;
  const match = versionOutput.match(/(?:java|openjdk) version "([0-9]+)(?:[.\-_][0-9a-zA-Z]+)?"/i) ||
                versionOutput.match(/"([0-9]+)(?:\.[0-9]+)*.*"/);
  if (match && match[1]) {
    const major = parseInt(match[1], 10);
    return isNaN(major) ? null : major;
  }
  return null;
}

function getJavaCommand() {
  if (process.env.JAVA_HOME) {
    const javaBin = path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
    if (fs.existsSync(javaBin)) {
      return javaBin;
    }
  }
  return 'java';
}

function checkJavaVersion(javaCmd = getJavaCommand()) {
  try {
    const output = execSync(`"${javaCmd}" -version 2>&1`, { encoding: 'utf8' });
    const major = parseJavaMajorVersion(output);
    return { ok: major !== null && major >= 25, major, raw: output };
  } catch (err) {
    return { ok: false, major: null, error: err.message };
  }
}

function computeSha256(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('data', (data) => hash.update(data));
    stream.on('end', () => resolve(hash.digest('hex')));
    stream.on('error', reject);
  });
}

function checkServerOnline(host, port, timeoutMs = 800) {
  return new Promise((resolve) => {
    const req = http.request(
      {
        host,
        port: parseInt(port, 10),
        path: '/actuator/health',
        method: 'GET',
        timeout: timeoutMs,
      },
      (res) => {
        resolve(res.statusCode === 200);
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

function downloadFile(url, destPath, maxRedirects = 5) {
  return new Promise((resolve, reject) => {
    if (maxRedirects < 0) return reject(new Error('Too many HTTP redirects'));

    const parsed = new URL(url);
    const client = parsed.protocol === 'https:' ? https : http;
    const req = client.get(
      url,
      {
        headers: {
          'User-Agent': '@spectrayan/spector-launcher',
          'Accept': 'application/octet-stream, application/json',
        },
      },
      (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          return downloadFile(res.headers.location, destPath, maxRedirects - 1)
            .then(resolve)
            .catch(reject);
        }
        if (res.statusCode === 403) {
          return reject(new Error('GitHub API rate limit exceeded (HTTP 403). Set SPECTOR_VERSION or wait before retrying.'));
        }
        if (res.statusCode !== 200) {
          return reject(new Error(`HTTP ${res.statusCode} downloading ${url}`));
        }
        const file = fs.createWriteStream(destPath);
        res.pipe(file);
        file.on('finish', () => {
          file.close(() => resolve());
        });
      }
    );
    req.on('error', reject);
  });
}

async function fetchReleaseMetadata(version) {
  const apiPath = (!version || version === 'latest')
    ? `/repos/${GITHUB_REPO}/releases/latest`
    : `/repos/${GITHUB_REPO}/releases/tags/${version.startsWith('v') ? version : 'v' + version}`;

  return new Promise((resolve, reject) => {
    const options = {
      host: 'api.github.com',
      path: apiPath,
      headers: {
        'User-Agent': '@spectrayan/spector-launcher',
        'Accept': 'application/vnd.github.v3+json',
      },
    };

    https.get(options, (res) => {
      let data = '';
      if (res.statusCode === 403) {
        return reject(new Error('GitHub API rate limit exceeded (HTTP 403). Set SPECTOR_VERSION or wait before retrying.'));
      }
      if (res.statusCode === 404) {
        return reject(new Error(`Release ${version || 'latest'} not found in GitHub repository ${GITHUB_REPO}.`));
      }
      if (res.statusCode !== 200) {
        return reject(new Error(`GitHub API returned HTTP ${res.statusCode}`));
      }
      res.on('data', (chunk) => (data += chunk));
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          reject(new Error(`Failed to parse GitHub release JSON: ${e.message}`));
        }
      });
    }).on('error', reject);
  });
}

async function ensureSpectorJar(spectorHome) {
  const binDir = path.join(spectorHome, 'bin');
  const jarPath = path.join(binDir, 'spector.jar');
  const shaPath = path.join(binDir, 'spector.jar.sha256');

  if (!fs.existsSync(binDir)) {
    fs.mkdirSync(binDir, { recursive: true });
  }

  const configuredVersion = process.env.SPECTOR_VERSION;
  const configuredSha = process.env.SPECTOR_JAR_SHA256;

  // Check if existing JAR matches expected hash
  if (fs.existsSync(jarPath) && fs.existsSync(shaPath)) {
    const expectedSha = (fs.readFileSync(shaPath, 'utf8').trim().split(/\s+/)[0] || '').toLowerCase();
    const actualSha = (await computeSha256(jarPath)).toLowerCase();
    if (expectedSha && actualSha === expectedSha) {
      return jarPath;
    }
    process.stderr.write(`[spector-npx] Existing spector.jar hash mismatch or corrupted. Re-downloading...\n`);
  }

  process.stderr.write(`[spector-npx] Fetching release metadata from GitHub (${GITHUB_REPO})...\n`);
  const release = await fetchReleaseMetadata(configuredVersion);
  if (!release.assets || !Array.isArray(release.assets)) {
    throw new Error('No assets found in release metadata.');
  }

  const jarAsset = release.assets.find((a) => a.name === 'spector.jar');
  if (!jarAsset) {
    throw new Error("Release asset 'spector.jar' was not found on GitHub Releases.");
  }

  const shaAsset = release.assets.find((a) => a.name === 'spector.jar.sha256');
  if (!shaAsset && !configuredSha) {
    throw new Error("Release asset 'spector.jar.sha256' was not found on GitHub Releases and SPECTOR_JAR_SHA256 is not set.");
  }

  process.stderr.write(`[spector-npx] Downloading spector.jar from ${jarAsset.browser_download_url}...\n`);
  await downloadFile(jarAsset.browser_download_url, jarPath);

  let expectedHash = configuredSha;
  if (shaAsset) {
    await downloadFile(shaAsset.browser_download_url, shaPath);
    expectedHash = fs.readFileSync(shaPath, 'utf8').trim().split(/\s+/)[0];
  }

  if (expectedHash) {
    const computedHash = await computeSha256(jarPath);
    if (computedHash.toLowerCase() !== expectedHash.trim().toLowerCase()) {
      fs.unlinkSync(jarPath);
      throw new Error(`SHA-256 verification failed! Expected ${expectedHash}, got ${computedHash}`);
    }
    process.stderr.write(`[spector-npx] Verified SHA-256: ${computedHash}\n`);
  }

  return jarPath;
}

function printUsage() {
  console.log(`
Spector Zero-Install MCP & CLI Runner (@spectrayan/spector)

Usage:
  npx -y @spectrayan/spector [command] [options]

Commands:
  mcp                    Run Model Context Protocol server over STDIO (default)
  doctor                 Diagnose environment, Java 25 Vector API, and storage
  init                   Initialize local Spector configuration and storage
  serve                  Start local Spector Synapse daemon (REST, SSE, MCP HTTP)
  [args...]              Forward all other arguments directly to spector.jar

Options:
  --host <host>          Spector Synapse daemon host (default: 127.0.0.1 or SPECTOR_HOST)
  --port <port>          Spector Synapse daemon port (default: 7070 or SPECTOR_PORT)
  --print-http-config    Print MCP HTTP JSON configuration for active daemon and exit
  --help, -h             Show this help message
`);
}

async function main() {
  const { host, port, printHttpConfig, showHelp, forwardedArgs } = parseCliArgs(process.argv);

  if (showHelp) {
    printUsage();
    return;
  }

  const spectorHome = process.env.SPECTOR_HOME || path.join(os.homedir(), '.spector');

  // 1. Probe if local or remote Synapse daemon is online
  const isDaemonOnline = await checkServerOnline(host, port);

  if (isDaemonOnline) {
    process.stderr.write(`[spector-npx] Spector daemon detected at http://${host}:${port}\n`);
    if (printHttpConfig) {
      console.log(JSON.stringify({
        mcpServers: {
          spector: {
            url: `http://${host}:${port}/mcp`
          }
        }
      }, null, 2));
      return;
    }
  }

  // 2. Validate Java 25+ requirement
  const javaCmd = getJavaCommand();
  const javaCheck = checkJavaVersion(javaCmd);

  if (!javaCheck.ok) {
    process.stderr.write(`
[spector-npx] Error: OpenJDK 25+ is required to execute the local Spector engine.
Detected Java runtime: ${javaCheck.major ? `Java ${javaCheck.major}` : 'Not found on PATH'}

Spector leverages Project Panama FFM and the Java Vector API (SIMD) for sub-millisecond AI memory retrieval.

To install OpenJDK 25:
  macOS:    brew install openjdk@25
  Windows:  winget install Microsoft.OpenJDK.25   (or: scoop install openjdk25)
  Linux:    sudo apt install openjdk-25-jdk        (or: sudo dnf install java-25-openjdk)

Alternatively, connect to a running Spector daemon or container:
  docker compose up -d
  npx -y @spectrayan/spector --host 127.0.0.1 --port 7070 --print-http-config
\n`);
    process.exit(1);
  }

  // 3. Ensure verified spector.jar is present
  let jarPath;
  try {
    jarPath = await ensureSpectorJar(spectorHome);
  } catch (err) {
    process.stderr.write(`[spector-npx] Failed to acquire spector.jar: ${err.message}\n`);
    process.exit(1);
  }

  // 4. Forward arguments directly to spector.jar
  const subArgs = forwardedArgs.length > 0 ? forwardedArgs : ['mcp'];
  const javaArgs = [
    '--enable-preview',
    '--add-modules=jdk.incubator.vector',
    '--enable-native-access=ALL-UNNAMED',
    '-jar',
    jarPath,
    ...subArgs,
  ];

  const proc = spawn(javaCmd, javaArgs, {
    stdio: 'inherit',
    env: process.env,
  });

  proc.on('exit', (code) => {
    process.exit(code ?? 0);
  });
}

if (require.main === module) {
  main().catch((err) => {
    process.stderr.write(`[spector-npx] Fatal: ${err.message}\n`);
    process.exit(1);
  });
}

module.exports = {
  parseCliArgs,
  parseJavaMajorVersion,
  checkJavaVersion,
  computeSha256,
  checkServerOnline,
};

