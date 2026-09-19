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

test.describe('Visual Regression — Interleaved Tool Execution Cards', () => {
  test.beforeEach(async ({ page }) => {
    await setupChatMocks(page, {
      chatEnabled: true,
      streamFixture: 'tool-interleave.sse',
    });
  });

  test('renders interleaved tool execution card with arguments and success status', async ({
    page,
  }) => {
    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    // Submit prompt that triggers tool invocation
    const textarea = page.locator('.chat-input-row textarea');
    await textarea.fill('Where am I moving and when?');

    const sendBtn = page.locator('.send-btn, button[aria-label="Send message"]');
    await sendBtn.click();

    // Verify tool execution representation exists (either tool-card component or trace element)
    const toolElement = page.locator(
      '.tool-card, .trace-step, [data-testid="tool-card"], .tool-badge',
    );
    await expect(toolElement.first()).toBeVisible({ timeout: 10_000 });

    // Check for tool name memory_recall
    await expect(page.locator('body')).toContainText('memory_recall');

    // Verify assistant answer appears after tool completion
    const assistantRow = page.locator('.message-row.assistant');
    await expect(assistantRow).toBeVisible();
    await expect(assistantRow).toContainText('Austin');

    // Visual screenshot assertion
    await expect(page).toHaveScreenshot('tool-interleave.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
