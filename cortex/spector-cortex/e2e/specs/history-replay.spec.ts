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
import { setupChatMocks, loadJsonFixture } from './chat-fixtures.helper';

test.describe('Visual Regression — Historical Turn Replay', () => {
  const historyData = loadJsonFixture('history-replay.json');

  test.beforeEach(async ({ page }) => {
    await setupChatMocks(page, {
      chatEnabled: true,
      sessions: [
        {
          sessionId: historyData.sessionId,
          title: historyData.title,
          preview: historyData.turns[0].user.text,
          messageCount: 2,
          startedAt: 1758265000000,
          lastActivity: '2026-09-19T06:00:00Z',
        },
      ],
      historyResponse: historyData,
    });
  });

  test('accurately replays past turn containing user prompt, thinking trace, tool cards, and assistant markdown', async ({
    page,
  }) => {
    // Navigate with sessionId query param or directly to chat
    await page.goto(`/chat?session=${historyData.sessionId}`);
    await page.waitForLoadState('networkidle');

    // If load past session button is present, click it to hydrate history
    const loadPastBtn = page.locator('.load-more-btn');
    if (await loadPastBtn.isVisible()) {
      await loadPastBtn.click();
      await page.waitForLoadState('networkidle');
    }

    // Verify user prompt rendered
    const userMessage = page.locator('.message-row.user');
    await expect(userMessage.first()).toBeVisible();
    await expect(userMessage.first()).toContainText('Austin');

    // Verify assistant answer rendered with markdown formatting
    const assistantMessage = page.locator('.message-row.assistant');
    await expect(assistantMessage.first()).toBeVisible();
    await expect(assistantMessage.first()).toContainText('Austin, Texas');

    // Visual screenshot assertion
    await expect(page).toHaveScreenshot('history-replay.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
