/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { trimTrailingSlashes, trimLeadingSlashes, joinUrl } from '../dist/index.js';

describe('URL and Path Utilities', () => {
  it('should trim trailing slashes safely without regex', () => {
    assert.equal(trimTrailingSlashes('http://localhost:7070/'), 'http://localhost:7070');
    assert.equal(trimTrailingSlashes('http://localhost:7070///'), 'http://localhost:7070');
    assert.equal(trimTrailingSlashes('http://localhost:7070'), 'http://localhost:7070');
    assert.equal(trimTrailingSlashes('///'), '');
    assert.equal(trimTrailingSlashes(''), '');
  });

  it('should trim leading slashes safely without regex', () => {
    assert.equal(trimLeadingSlashes('/api/v1/events'), 'api/v1/events');
    assert.equal(trimLeadingSlashes('///api/v1/events'), 'api/v1/events');
    assert.equal(trimLeadingSlashes('api/v1/events'), 'api/v1/events');
    assert.equal(trimLeadingSlashes('///'), '');
    assert.equal(trimLeadingSlashes(''), '');
  });

  it('should join URLs without duplicate slashes or ReDoS', () => {
    assert.equal(joinUrl('http://localhost:7070/', '/api/v1/memory'), 'http://localhost:7070/api/v1/memory');
    assert.equal(joinUrl('http://localhost:7070///', '///api/v1/memory'), 'http://localhost:7070/api/v1/memory');
    assert.equal(joinUrl('http://localhost:7070', 'api/v1/memory'), 'http://localhost:7070/api/v1/memory');
    assert.equal(joinUrl('', '/api/v1/memory'), 'api/v1/memory');
  });
});
