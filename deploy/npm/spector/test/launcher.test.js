/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');

const {
  parseCliArgs,
  parseJavaMajorVersion,
  computeSha256,
  checkServerOnline,
} = require('../bin/spector.js');

test('parseCliArgs parses defaults correctly', () => {
  const parsed = parseCliArgs(['node', 'spector']);
  assert.equal(parsed.host, '127.0.0.1');
  assert.equal(parsed.port, '7070');
  assert.equal(parsed.printHttpConfig, false);
  assert.equal(parsed.showHelp, false);
  assert.deepEqual(parsed.forwardedArgs, []);
});

test('parseCliArgs handles custom host, port, and print-http-config', () => {
  const parsed = parseCliArgs(['node', 'spector', '--host', '10.0.0.5', '--port', '8080', '--print-http-config']);
  assert.equal(parsed.host, '10.0.0.5');
  assert.equal(parsed.port, '8080');
  assert.equal(parsed.printHttpConfig, true);
  assert.equal(parsed.showHelp, false);
});

test('parseCliArgs handles inline flags and help', () => {
  const parsed = parseCliArgs(['node', 'spector', '--host=remote.host', '--port=9090', '--help']);
  assert.equal(parsed.host, 'remote.host');
  assert.equal(parsed.port, '9090');
  assert.equal(parsed.showHelp, true);
});

test('parseCliArgs preserves forwarded commands and options', () => {
  const parsed = parseCliArgs(['node', 'spector', 'doctor', '--json', '--data-dir', '/tmp/data']);
  assert.equal(parsed.showHelp, false);
  assert.deepEqual(parsed.forwardedArgs, ['doctor', '--json', '--data-dir', '/tmp/data']);
});

test('parseJavaMajorVersion extracts major version numbers accurately', () => {
  assert.equal(parseJavaMajorVersion('openjdk version "25" 2025-09-16'), 25);
  assert.equal(parseJavaMajorVersion('java version "25.0.1" 2025-10-21'), 25);
  assert.equal(parseJavaMajorVersion('openjdk version "25-ea" 2025-03-18'), 25);
  assert.equal(parseJavaMajorVersion('openjdk version "21.0.2" 2024-01-16 LTS'), 21);
  assert.equal(parseJavaMajorVersion('openjdk version "17.0.9" 2023-10-17'), 17);
  assert.equal(parseJavaMajorVersion('invalid output without version'), null);
});

test('computeSha256 produces exact hex digest of file', async () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'spector-test-'));
  const tempFile = path.join(tempDir, 'hash-test.txt');
  fs.writeFileSync(tempFile, 'Spector Cognitive Memory');
  try {
    const hash = await computeSha256(tempFile);
    assert.equal(hash, '5608b20d3dfc448e32ab01ebe32c70593f0cf50074014e2594f5eee7247fee8b');
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
});

test('checkServerOnline returns true on 200 /actuator/health and false on error', async () => {
  const server = http.createServer((req, res) => {
    if (req.url === '/actuator/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'UP' }));
    } else {
      res.writeHead(404);
      res.end();
    }
  });

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;

  try {
    const isOnline = await checkServerOnline('127.0.0.1', port);
    assert.equal(isOnline, true);

    const isOffline = await checkServerOnline('127.0.0.1', 65432, 200);
    assert.equal(isOffline, false);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
