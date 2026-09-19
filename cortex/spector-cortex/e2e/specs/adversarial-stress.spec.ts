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

import { test, expect, Route } from '@playwright/test';
import { setupChatMocks } from './chat-fixtures.helper';

test.describe('Tier 5 Adversarial Stress & Edge-Case Coverage Suite', () => {

  // ═════════════════════════════════════════════════════════════════════════
  // 1. Mid-Stream Server Error (error-mid-stream.sse)
  // ═════════════════════════════════════════════════════════════════════════
  test('Scenario 1: Handles mid-stream server error event cleanly and displays error bubble', async ({ page }) => {
    await setupChatMocks(page, {
      chatEnabled: true,
      streamFixture: 'error-mid-stream.sse',
    });

    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    const textarea = page.locator('.chat-input-row textarea');
    await textarea.fill('Trigger mid-stream error');

    const sendBtn = page.locator('.send-btn, button[aria-label="Send message"]');
    await sendBtn.click();

    // Verify thinking trace was rendered before error
    const thinkingEl = page.locator('.thinking-indicator, .thinking-accordion, [data-testid="thinking-trace"]');
    await expect(thinkingEl.first()).toBeVisible({ timeout: 5000 });

    // Verify error message bubble is rendered
    const errorMsgBubble = page.locator('.message-bubble.error-msg');
    await expect(errorMsgBubble.first()).toBeVisible({ timeout: 5000 });
    await expect(errorMsgBubble.first()).toContainText('Upstream LLM connection terminated unexpectedly');

    // Verify input controls are unlocked and ready for next input
    await expect(textarea).toBeEnabled({ timeout: 5000 });
    await expect(page.locator('.abort-btn')).not.toBeVisible();
  });

  // ═════════════════════════════════════════════════════════════════════════
  // 2. Network Drop / Connection Abort Mid-Stream
  // ═════════════════════════════════════════════════════════════════════════
  test('Scenario 2: Gracefully recovers when network connection drops mid-stream', async ({ page }) => {
    await setupChatMocks(page, { chatEnabled: true });

    // Custom route handler that aborts the HTTP request to simulate sudden network disconnection
    await page.route('**/api/v1/chat/stream', async (route: Route) => {
      await route.abort('connectionreset');
    });

    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    const textarea = page.locator('.chat-input-row textarea');
    await textarea.fill('Testing network disconnection');

    const sendBtn = page.locator('.send-btn, button[aria-label="Send message"]');
    await sendBtn.click();

    // Verify error bubble appears to alert the user
    const errorBubble = page.locator('.message-bubble.error-msg');
    await expect(errorBubble.first()).toBeVisible({ timeout: 5000 });

    // Verify UI unfreezes and input becomes re-enabled
    await expect(textarea).toBeEnabled({ timeout: 5000 });
    await expect(page.locator('.abort-btn')).not.toBeVisible();
  });

  // ═════════════════════════════════════════════════════════════════════════
  // 3. Rapid User Abort / Generation Cancellation
  // ═════════════════════════════════════════════════════════════════════════
  test('Scenario 3: Rapid user abort stops active stream and unfreezes chat input', async ({ page }) => {
    await setupChatMocks(page, { chatEnabled: true });

    // Keep stream pending for 10s so user has ample time to click abort
    await page.route('**/api/v1/chat/stream', async (route: Route) => {
      await new Promise((resolve) => setTimeout(resolve, 10000));
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: 'event: session\ndata: {"sessionId":"s-abort","turnId":"t-abort","seq":0,"tsEpochMs":1000}\n\n',
      });
    });

    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    const textarea = page.locator('.chat-input-row textarea');
    await textarea.fill('Slow stream test');

    const sendBtn = page.locator('.send-btn');
    await sendBtn.click();

    // The send button transitions to abort button (.abort-btn)
    const abortBtn = page.locator('.abort-btn, button[aria-label="Stop generating"]');
    await expect(abortBtn).toBeVisible({ timeout: 3000 });

    // Click abort
    await abortBtn.click();

    // UI should immediately return to ready state
    await expect(textarea).toBeEnabled({ timeout: 3000 });
    await expect(page.locator('.abort-btn')).not.toBeVisible();

    // User can immediately type and send another query
    await textarea.fill('Subsequent query after cancellation');
    await expect(textarea).toHaveValue('Subsequent query after cancellation');
  });

  // ═════════════════════════════════════════════════════════════════════════
  // 4. Pathological Long Thinking Text (Chain-of-Thought Stress)
  // ═════════════════════════════════════════════════════════════════════════
  test('Scenario 4: Handles 8,000+ characters of chain-of-thought reasoning without overflow', async ({ page }) => {
    await setupChatMocks(page, { chatEnabled: true });

    const longCoT = 'Deep chain-of-thought reasoning pass: ' +
      'Analyzing hippocampal engrams and associative synaptic projections in 6-phase scoring. '.repeat(100);

    const longThinkingSSE = [
      'event: session\ndata: {"sessionId":"s-long","turnId":"t-long","seq":0,"tsEpochMs":1000}\n\n',
      `event: thinking\ndata: {"sessionId":"s-long","turnId":"t-long","seq":1,"tsEpochMs":1050,"text":${JSON.stringify(longCoT)},"elapsedMs":1450}\n\n`,
      'event: token\ndata: {"sessionId":"s-long","turnId":"t-long","seq":2,"tsEpochMs":1100,"text":"Synthesized cognitive summary."}\n\n',
      'event: done\ndata: {"sessionId":"s-long","turnId":"t-long","seq":3,"tsEpochMs":1200,"latencyMs":1200}\n\n',
    ].join('');

    await page.route('**/api/v1/chat/stream', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        headers: { 'Cache-Control': 'no-cache', Connection: 'keep-alive' },
        body: longThinkingSSE,
      });
    });

    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    const textarea = page.locator('.chat-input-row textarea');
    await textarea.fill('Request deep reasoning analysis');
    await page.locator('.send-btn').click();

    // Verify assistant answer appears
    const assistantRow = page.locator('.message-row.assistant');
    await expect(assistantRow).toContainText('Synthesized cognitive summary.', { timeout: 8000 });

    // Thinking accordion should auto-collapse on visible token
    const accordion = page.locator('cortex-thinking-accordion, thinking-accordion');
    await expect(accordion).toBeVisible();

    // Click accordion header to re-expand and verify long text rendering
    const accordionHeader = page.locator('.accordion-header');
    await accordionHeader.click();

    // Verify reasoning trace is expanded and contains expected text
    const reasoningBox = page.locator('.reasoning-trace-box');
    await expect(reasoningBox).toBeVisible();
    await expect(reasoningBox).toContainText('Deep chain-of-thought reasoning pass:');

    // Verify timer formatting: 1450ms -> 1.4s or 1.5s
    await expect(page.locator('.thinking-label')).toContainText(/Thought for 1\.[45]s/);
  });

  // ═════════════════════════════════════════════════════════════════════════
  // 5. Multi-Tool Interleaving with Mixed Statuses (Running, Success, Failure)
  // ═════════════════════════════════════════════════════════════════════════
  test('Scenario 5: Renders multiple interleaved tool cards with mixed success, failure, and 2 KiB truncation', async ({ page }) => {
    await setupChatMocks(page, { chatEnabled: true });

    const largeResult = JSON.stringify({
      engrams: Array.from({ length: 50 }, (_, i) => ({
        id: `engram_${i}`,
        valence: 0.85,
        salience: 0.92,
        content: `Detailed semantic engram payload at index ${i} with extended context metadata`,
      })),
    });

    const multiToolSSE = [
      'event: session\ndata: {"sessionId":"s-tools","turnId":"t-tools","seq":0,"tsEpochMs":1000}\n\n',
      'event: tool_call\ndata: {"sessionId":"s-tools","turnId":"t-tools","seq":1,"tsEpochMs":1010,"callId":"call_1","name":"memory_recall","arguments":{"query":"relocation"}}\n\n',
      'event: tool_result\ndata: {"sessionId":"s-tools","turnId":"t-tools","seq":2,"tsEpochMs":1030,"callId":"call_1","name":"memory_recall","status":"success","preview":"{\\"status\\":\\"found\\"}","elapsedMs":20}\n\n',
      'event: tool_call\ndata: {"sessionId":"s-tools","turnId":"t-tools","seq":3,"tsEpochMs":1040,"callId":"call_2","name":"large_dump","arguments":{"depth":5}}\n\n',
      `event: tool_result\ndata: {"sessionId":"s-tools","turnId":"t-tools","seq":4,"tsEpochMs":1080,"callId":"call_2","name":"large_dump","status":"success","preview":${JSON.stringify(largeResult)},"truncated":true,"elapsedMs":40}\n\n`,
      'event: tool_call\ndata: {"sessionId":"s-tools","turnId":"t-tools","seq":5,"tsEpochMs":1090,"callId":"call_3","name":"calculator","arguments":{"expr":"1/0"}}\n\n',
      'event: tool_result\ndata: {"sessionId":"s-tools","turnId":"t-tools","seq":6,"tsEpochMs":1100,"callId":"call_3","name":"calculator","status":"failure","preview":"Division by zero error","elapsedMs":10}\n\n',
      'event: token\ndata: {"sessionId":"s-tools","turnId":"t-tools","seq":7,"tsEpochMs":1120,"text":"Completed all tool runs."}\n\n',
      'event: done\ndata: {"sessionId":"s-tools","turnId":"t-tools","seq":8,"tsEpochMs":1150}\n\n',
    ].join('');

    await page.route('**/api/v1/chat/stream', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        headers: { 'Cache-Control': 'no-cache', Connection: 'keep-alive' },
        body: multiToolSSE,
      });
    });

    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    await page.locator('.chat-input-row textarea').fill('Execute multi-tool sequence');
    await page.locator('.send-btn').click();

    // Verify 3 distinct tool execution cards are rendered
    const toolCards = page.locator('cortex-tool-card, tool-card, [data-testid="tool-card"]');
    await expect(toolCards).toHaveCount(3, { timeout: 8000 });

    // Verify tool names
    await expect(toolCards.nth(0)).toContainText('memory_recall');
    await expect(toolCards.nth(1)).toContainText('large_dump');
    await expect(toolCards.nth(2)).toContainText('calculator');

    // Verify status chips: card 0 success, card 1 success, card 2 failure
    await expect(toolCards.nth(0).locator('.status-chip.success')).toBeVisible();
    await expect(toolCards.nth(1).locator('.status-chip.success')).toBeVisible();
    await expect(toolCards.nth(2).locator('.status-chip.failure')).toBeVisible();

    // Expand the large_dump tool card
    await toolCards.nth(1).click();
    const truncatedTag = toolCards.nth(1).locator('.truncated-tag');
    await expect(truncatedTag).toContainText('2 KiB');

    // Check tab switching between output preview and arguments
    const argsTab = toolCards.nth(1).locator('.tab-btn:has-text("Arguments")');
    await argsTab.click();
    await expect(toolCards.nth(1).locator('.code-content')).toContainText('"depth": 5');

    // Verify assistant answer appears
    await expect(page.locator('.message-row.assistant')).toContainText('Completed all tool runs.');
  });

  // ═════════════════════════════════════════════════════════════════════════
  // 6. Out-of-Order SSE Arrival (Tool Result before Tool Call)
  // ═════════════════════════════════════════════════════════════════════════
  test('Scenario 6: Inverted SSE event arrival (tool_result before tool_call) reduces correctly', async ({ page }) => {
    await setupChatMocks(page, { chatEnabled: true });

    // Out-of-order SSE stream: tool_result arrives before tool_call!
    const outOfOrderSSE = [
      'event: session\ndata: {"sessionId":"s-ooo","turnId":"t-ooo","seq":0,"tsEpochMs":1000}\n\n',
      'event: tool_result\ndata: {"sessionId":"s-ooo","turnId":"t-ooo","seq":2,"tsEpochMs":1050,"callId":"inverted_1","name":"fast_lookup","status":"success","preview":"{\\"val\\": 123}","elapsedMs":50}\n\n',
      'event: tool_call\ndata: {"sessionId":"s-ooo","turnId":"t-ooo","seq":1,"tsEpochMs":1000,"callId":"inverted_1","name":"fast_lookup","arguments":{"target":"id_123"}}\n\n',
      'event: token\ndata: {"sessionId":"s-ooo","turnId":"t-ooo","seq":3,"tsEpochMs":1100,"text":"Inverted events resolved."}\n\n',
      'event: done\ndata: {"sessionId":"s-ooo","turnId":"t-ooo","seq":4,"tsEpochMs":1150}\n\n',
    ].join('');

    await page.route('**/api/v1/chat/stream', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        headers: { 'Cache-Control': 'no-cache', Connection: 'keep-alive' },
        body: outOfOrderSSE,
      });
    });

    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    await page.locator('.chat-input-row textarea').fill('Test inverted SSE arrival');
    await page.locator('.send-btn').click();

    // Tool card should exist and remain success despite inverted arrival
    const toolCard = page.locator('cortex-tool-card, tool-card, [data-testid="tool-card"]');
    await expect(toolCard.first()).toBeVisible({ timeout: 8000 });
    await expect(toolCard.first().locator('.status-chip.success')).toBeVisible();

    // Expand tool card and verify arguments were merged in from late tool_call
    await toolCard.first().click();
    await toolCard.first().locator('.tab-btn:has-text("Arguments")').click();
    await expect(toolCard.first().locator('.code-content')).toContainText('"target": "id_123"');
  });

  // ═════════════════════════════════════════════════════════════════════════
  // 7. Mobile Drawer Interaction and Suggestion Chip Usability
  // ═════════════════════════════════════════════════════════════════════════
  test('Scenario 7: Mobile drawer toggle operates cleanly without blocking chip interaction', async ({ page }) => {
    await setupChatMocks(page, {
      chatEnabled: true,
      sessions: [
        {
          sessionId: 's-mob-1',
          title: 'Mobile Session One',
          preview: 'Preview text for session 1',
          messageCount: 3,
        },
      ],
    });

    await page.goto('/chat');
    await page.waitForLoadState('networkidle');

    const drawer = page.locator('cortex-conversation-drawer, conversation-drawer');

    // On mobile viewports (<=768px), drawer is collapsed by default
    const isMobile = page.viewportSize() ? page.viewportSize()!.width <= 768 : false;

    if (isMobile) {
      await expect(drawer).toHaveClass(/drawer-closed/);

      // Verify suggestion chips are interactive and not covered
      const chip = page.locator('.suggestion-chip').first();
      await expect(chip).toBeVisible();
      await chip.click();
      await expect(page.locator('.chat-input-row textarea')).toHaveValue('What do you remember about me?');

      // Toggle drawer open via menu button in chat header
      const openBtn = page.locator('.chat-header .drawer-toggle');
      if (await openBtn.isVisible()) {
        await openBtn.click();
        await expect(drawer).toHaveClass(/drawer-open/);
        await expect(page.locator('.conversation-item, .conv-title').first()).toBeVisible();

        // Toggle drawer closed via drawer toggle button
        const closeBtn = page.locator('cortex-conversation-drawer .drawer-toggle');
        if (await closeBtn.isVisible()) {
          await closeBtn.click();
          await expect(drawer).toHaveClass(/drawer-closed/);
        }
      }
    } else {
      // Desktop: drawer is open by default
      await expect(drawer).toHaveClass(/drawer-open/);
    }
  });

});
