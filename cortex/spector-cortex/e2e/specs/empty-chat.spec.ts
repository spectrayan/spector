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

test.describe('Visual Regression — Empty Chat with Suggestions', () => {
  test.beforeEach(async ({ page }) => {
    // Intercept features to prevent redirect to /memories
    await setupChatMocks(page, {
      chatEnabled: true,
      sessions: [],
    });
  });

  test('renders empty chat state with suggestion chips and input bar', async ({ page }) => {
    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    // Confirm navigation reached /chat (not redirected to /memories)
    await expect(page).toHaveURL(/\/chat/);

    // Verify empty state container and title
    const emptyState = page.locator('.empty-state');
    await expect(emptyState).toBeVisible();
    await expect(emptyState.locator('h3')).toHaveText('Spector Chat');

    // Verify presence of suggestion chips
    const chips = page.locator('.suggestion-chip');
    await expect(chips).toHaveCount(3);
    await expect(chips.first()).toContainText('What do you remember about me?');

    // Verify message input field
    const inputField = page.locator('textarea, input[placeholder*="Ask"]');
    await expect(inputField).toBeVisible();

    // Visual screenshot assertion
    await expect(page).toHaveScreenshot('empty-chat.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });

  test('clicking a suggestion chip populates the chat prompt', async ({ page }) => {
    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    const firstChip = page.locator('.suggestion-chip').first();
    await expect(firstChip).toBeVisible();
    await firstChip.click();

    // The textarea should now contain the suggestion text
    const textarea = page.locator('.chat-input-row textarea');
    await expect(textarea).toHaveValue('What do you remember about me?');
  });
});
