/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-cortex/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */

import { test, expect } from '@playwright/test';
import { setupChatMocks } from './chat-fixtures.helper';

test.describe('Visual Regression — Live Streaming with Open Thinking Accordion', () => {
  test.beforeEach(async ({ page }) => {
    await setupChatMocks(page, {
      chatEnabled: true,
      streamFixture: 'thinking-then-tokens.sse',
    });
  });

  test('displays thinking trace and renders streamed assistant response', async ({ page }) => {
    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    // Type query and submit
    const textarea = page.locator('.chat-input-row textarea');
    await expect(textarea).toBeVisible();
    await textarea.fill('What is Spector memory architecture?');

    const sendBtn = page.locator('.send-btn, button[aria-label="Send message"]');
    await sendBtn.click();

    // Verify user message appears
    const userRow = page.locator('.message-row.user');
    await expect(userRow).toBeVisible();
    await expect(userRow).toContainText('What is Spector memory architecture?');

    // Verify thinking trace element is rendered
    const thinkingElement = page.locator(
      '.thinking-indicator, .thinking-accordion, [data-testid="thinking-trace"]',
    );
    await expect(thinkingElement.first()).toBeVisible({ timeout: 10_000 });

    // Verify final assistant text matches tokens from fixture
    const assistantRow = page.locator('.message-row.assistant');
    await expect(assistantRow).toBeVisible();
    await expect(assistantRow).toContainText('Spector');

    // Visual screenshot assertion
    await expect(page).toHaveScreenshot('thinking-stream.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
