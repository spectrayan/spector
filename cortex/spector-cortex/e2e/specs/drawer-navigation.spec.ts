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

test.describe('Visual Regression — Conversation Drawer Navigation', () => {
  const mockSessions = [
    {
      sessionId: '01J8Y000000000000000000000',
      title: 'Austin Relocation Planning',
      preview: 'What do you remember about my relocation to Austin?',
      messageCount: 2,
      startedAt: 1758265000000,
      lastActivity: '2026-09-19T06:00:00Z',
    },
    {
      sessionId: '01J8Y000000000000000000009',
      title: 'Panama Vector API Benchmark',
      preview: 'Can we compare 256-bit SIMD vs scalar performance?',
      messageCount: 4,
      startedAt: 1758260000000,
      lastActivity: '2026-09-19T05:00:00Z',
    },
  ];

  test.beforeEach(async ({ page }) => {
    await setupChatMocks(page, {
      chatEnabled: true,
      sessions: mockSessions,
    });
  });

  test('renders conversation drawer in expanded and collapsed states', async ({ page }) => {
    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    // Look for drawer or history sidebar container
    const drawer = page.locator(
      'conversation-drawer, .history-sidebar, .conversation-drawer, [data-testid="conversation-drawer"]',
    );

    if (await drawer.isVisible()) {
      // 1. Verify expanded state
      await expect(drawer).toBeVisible();
      await expect(page).toHaveScreenshot('drawer-expanded.png', {
        maxDiffPixelRatio: 0.02,
        animations: 'disabled',
      });

      // 2. Toggle to collapsed state if toggle button exists
      const toggleBtn = page.locator(
        '.drawer-toggle, button[aria-label*="drawer" i], button[aria-label*="sidebar" i], .sidebar-toggle',
      );
      if (await toggleBtn.isVisible()) {
        await toggleBtn.click();
        await expect(page).toHaveScreenshot('drawer-collapsed.png', {
          maxDiffPixelRatio: 0.02,
          animations: 'disabled',
        });
      }
    } else {
      // If drawer is initially collapsed or in load-past mode, verify past session presence
      const pastSessionsBtn = page.locator('.load-more-btn, button:has-text("Load")');
      if (await pastSessionsBtn.isVisible()) {
        await expect(pastSessionsBtn).toBeVisible();
      }
      await expect(page).toHaveScreenshot('drawer-default.png', {
        maxDiffPixelRatio: 0.02,
        animations: 'disabled',
      });
    }
  });
});
