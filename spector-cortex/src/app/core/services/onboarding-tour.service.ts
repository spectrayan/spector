// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Smart Onboarding Tour Service
// ═══════════════════════════════════════════════════════════════════════
// Signal-based guided tour engine with step definitions, spotlight
// positioning, and localStorage persistence.

import { Injectable, signal, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

/** Position of the tooltip relative to the spotlight */
export type TooltipPosition = 'top' | 'bottom' | 'left' | 'right';

/** A single onboarding step definition */
export interface TourStep {
  readonly id: string;
  readonly title: string;
  readonly description: string;
  readonly targetSelector: string;     // CSS selector to spotlight
  readonly position: TooltipPosition;
  readonly spotlightPadding?: number;  // px padding around target
  readonly route?: string;             // navigate to this route before showing
  readonly icon?: string;              // Material icon
  readonly delayMs?: number;           // delay before showing (allows route transition)
}

const ONBOARDING_KEY = 'spector.onboarding.completed';
const ONBOARDING_DISMISSED_KEY = 'spector.onboarding.dismissed';

@Injectable({ providedIn: 'root' })
export class OnboardingTourService {

  private readonly router = inject(Router);

  // ── State ─────────────────────────────────────────────────
  readonly isActive = signal(false);
  readonly currentStepIndex = signal(0);
  readonly isCompleted = signal(this.checkCompleted());

  readonly steps: TourStep[] = [
    {
      id: 'welcome',
      title: 'Welcome to Cortex',
      description: 'Your cognitive memory engine\'s neural interface. Let\'s take a quick tour of the key features.',
      targetSelector: '.cortex-header',
      position: 'bottom',
      icon: 'emoji_objects',
      route: '/graph',
    },
    {
      id: 'graph-explorer',
      title: 'Neural Graph',
      description: 'This 3D graph visualizes your entire memory network. Each star is a memory. Lines are synaptic connections — Hebbian, Temporal, and Entity edges.',
      targetSelector: '.graph-canvas-container',
      position: 'right',
      icon: 'hub',
      spotlightPadding: 0,
      route: '/graph',
    },
    {
      id: 'graph-toolbar',
      title: 'Graph Controls',
      description: 'Screenshot, share, filter, and control your graph view from here. Try the layer toggles to show/hide different edge types.',
      targetSelector: '.explorer-toolbar',
      position: 'bottom',
      icon: 'tune',
      route: '/graph',
    },
    {
      id: 'dashboard',
      title: 'Real-Time Dashboard',
      description: 'Live telemetry from your cognitive engine — query throughput, memory tiers, SIMD lanes, and neural habituation meters.',
      targetSelector: '.shell-content',
      position: 'right',
      icon: 'dashboard',
      route: '/dashboard',
      delayMs: 500,
    },
    {
      id: 'memories',
      title: 'Memory Table',
      description: 'Browse, search, and manage all stored memories. Filter by tier, importance, timestamp, and more.',
      targetSelector: '.shell-content',
      position: 'right',
      icon: 'psychology',
      route: '/memories',
      delayMs: 500,
    },
    {
      id: 'query',
      title: 'Recall Query',
      description: 'Ask your memory anything. The cognitive engine runs decay gating, bloom filtering, SIMD scoring, and graph boosting to find the most relevant memories.',
      targetSelector: '.shell-content',
      position: 'right',
      icon: 'search',
      route: '/query',
      delayMs: 500,
    },
    {
      id: 'command-palette',
      title: 'Command Palette',
      description: 'Press ⌘K (or Ctrl+K) anytime for instant access to navigation, graph commands, theme toggle, and memory actions.',
      targetSelector: '.cortex-header',
      position: 'bottom',
      icon: 'keyboard',
    },
    {
      id: 'finish',
      title: 'You\'re Ready!',
      description: 'You now know the essentials. Explore the graph, run recall queries, and watch your cognitive engine in action. 🚀',
      targetSelector: '.cortex-header',
      position: 'bottom',
      icon: 'celebration',
    },
  ];

  readonly currentStep = computed(() => this.steps[this.currentStepIndex()] ?? null);
  readonly totalSteps = computed(() => this.steps.length);
  readonly progress = computed(() => (this.currentStepIndex() + 1) / this.steps.length);

  // ── Controls ────────────────────────────────────────────

  /** Start the onboarding tour */
  startTour(): void {
    this.currentStepIndex.set(0);
    this.isActive.set(true);
    this.navigateToStep(0);
  }

  /** Go to next step */
  nextStep(): void {
    const nextIdx = this.currentStepIndex() + 1;
    if (nextIdx >= this.steps.length) {
      this.completeTour();
    } else {
      this.currentStepIndex.set(nextIdx);
      this.navigateToStep(nextIdx);
    }
  }

  /** Go to previous step */
  prevStep(): void {
    const prevIdx = this.currentStepIndex() - 1;
    if (prevIdx >= 0) {
      this.currentStepIndex.set(prevIdx);
      this.navigateToStep(prevIdx);
    }
  }

  /** Skip / dismiss the tour entirely */
  skipTour(): void {
    this.isActive.set(false);
    this.currentStepIndex.set(0);
    try { localStorage.setItem(ONBOARDING_DISMISSED_KEY, 'true'); } catch {}
  }

  /** Mark tour as completed */
  completeTour(): void {
    this.isActive.set(false);
    this.isCompleted.set(true);
    try { localStorage.setItem(ONBOARDING_KEY, 'true'); } catch {}
  }

  /** Reset tour state (for re-running from settings) */
  resetTour(): void {
    this.isCompleted.set(false);
    try {
      localStorage.removeItem(ONBOARDING_KEY);
      localStorage.removeItem(ONBOARDING_DISMISSED_KEY);
    } catch {}
  }

  /** Check if the user should see the tour on first visit */
  shouldAutoStart(): boolean {
    try {
      const completed = localStorage.getItem(ONBOARDING_KEY);
      const dismissed = localStorage.getItem(ONBOARDING_DISMISSED_KEY);
      return !completed && !dismissed;
    } catch {
      return false;
    }
  }

  // ── Internal ────────────────────────────────────────────

  private checkCompleted(): boolean {
    try { return localStorage.getItem(ONBOARDING_KEY) === 'true'; } catch { return false; }
  }

  private navigateToStep(index: number): void {
    const step = this.steps[index];
    if (step?.route) {
      this.router.navigate([step.route]);
    }
  }
}
