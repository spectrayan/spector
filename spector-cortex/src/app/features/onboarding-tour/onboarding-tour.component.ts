// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Smart Onboarding Tour Component
// ═══════════════════════════════════════════════════════════════════════
// Spotlight overlay with glassmorphic tooltip, step counter, and
// confetti celebration on final step.

import {
  Component,
  inject,
  effect,
  signal,
  OnDestroy,
  AfterViewInit,
  ChangeDetectionStrategy,
  ElementRef,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { OnboardingTourService, TourStep } from '../../core/services/onboarding-tour.service';

interface SpotlightRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

@Component({
  selector: 'cortex-onboarding-tour',
  standalone: true,
  imports: [MatIconModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (tour.isActive()) {
      <div class="onboarding-backdrop" (click)="tour.skipTour()">
        <!-- Spotlight cut-out -->
        <div class="onboarding-spotlight"
             [style.left.px]="spotlight().x"
             [style.top.px]="spotlight().y"
             [style.width.px]="spotlight().width"
             [style.height.px]="spotlight().height">
        </div>
      </div>

      <!-- Tooltip Card -->
      @if (tour.currentStep(); as step) {
        <div class="onboarding-card"
             [class.card-top]="step.position === 'top'"
             [class.card-bottom]="step.position === 'bottom'"
             [class.card-left]="step.position === 'left'"
             [class.card-right]="step.position === 'right'"
             [style.left.px]="cardPosition().x"
             [style.top.px]="cardPosition().y"
             (click)="$event.stopPropagation()">

          <!-- Step icon + title -->
          <div class="onboarding-header">
            @if (step.icon) {
              <mat-icon class="onboarding-icon">{{ step.icon }}</mat-icon>
            }
            <h3 class="onboarding-title">{{ step.title }}</h3>
          </div>

          <p class="onboarding-description">{{ step.description }}</p>

          <!-- Progress dots -->
          <div class="onboarding-progress">
            @for (s of tour.steps; track s.id; let i = $index) {
              <div class="progress-dot"
                   [class.dot-active]="i === tour.currentStepIndex()"
                   [class.dot-done]="i < tour.currentStepIndex()">
              </div>
            }
          </div>

          <!-- Navigation -->
          <div class="onboarding-footer">
            <button class="onboarding-skip" (click)="tour.skipTour()">Skip Tour</button>
            <div class="onboarding-nav">
              @if (tour.currentStepIndex() > 0) {
                <button mat-icon-button class="nav-btn" (click)="tour.prevStep()">
                  <mat-icon>chevron_left</mat-icon>
                </button>
              }
              <span class="step-counter cortex-mono">
                {{ tour.currentStepIndex() + 1 }} / {{ tour.totalSteps() }}
              </span>
              <button mat-icon-button class="nav-btn nav-next" (click)="tour.nextStep()">
                <mat-icon>{{ isLastStep() ? 'check' : 'chevron_right' }}</mat-icon>
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Confetti on final step -->
      @if (showConfetti()) {
        <div class="confetti-container">
          @for (i of confettiPieces; track i) {
            <div class="confetti-piece"
                 [style.--confetti-delay]="i * 30 + 'ms'"
                 [style.--confetti-x]="confettiX(i) + 'vw'"
                 [style.--confetti-color]="confettiColor(i)">
            </div>
          }
        </div>
      }
    }
  `,
  styleUrl: './onboarding-tour.component.scss',
})
export class OnboardingTourComponent implements AfterViewInit, OnDestroy {

  readonly tour = inject(OnboardingTourService);
  private readonly el = inject(ElementRef);

  readonly spotlight = signal<SpotlightRect>({ x: 0, y: 0, width: 0, height: 0 });
  readonly cardPosition = signal<{ x: number; y: number }>({ x: 0, y: 0 });
  readonly showConfetti = signal(false);

  readonly confettiPieces = Array.from({ length: 40 }, (_, i) => i);
  private spotlightTimeout: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Reposition spotlight whenever the step changes
    effect(() => {
      const step = this.tour.currentStep();
      if (step && this.tour.isActive()) {
        const delay = step.delayMs ?? 100;
        if (this.spotlightTimeout) clearTimeout(this.spotlightTimeout);
        this.spotlightTimeout = setTimeout(() => this.positionSpotlight(step), delay);
      }
    });

    // Confetti on final step
    effect(() => {
      const step = this.tour.currentStep();
      this.showConfetti.set(step?.id === 'finish');
    });
  }

  ngAfterViewInit(): void {
    // Auto-start if first visit
    if (this.tour.shouldAutoStart()) {
      setTimeout(() => this.tour.startTour(), 1500);
    }
  }

  ngOnDestroy(): void {
    if (this.spotlightTimeout) clearTimeout(this.spotlightTimeout);
  }

  isLastStep(): boolean {
    return this.tour.currentStepIndex() === this.tour.steps.length - 1;
  }

  confettiX(i: number): number {
    return Math.random() * 100;
  }

  confettiColor(i: number): string {
    const colors = ['#00ffcc', '#42a5f5', '#ab47bc', '#ffc107', '#ff5722', '#e040fb'];
    return colors[i % colors.length];
  }

  private positionSpotlight(step: TourStep): void {
    const target = document.querySelector(step.targetSelector);
    if (!target) {
      // Fallback: center of screen
      this.spotlight.set({
        x: window.innerWidth / 2 - 150,
        y: window.innerHeight / 2 - 100,
        width: 300,
        height: 200,
      });
      this.cardPosition.set({
        x: window.innerWidth / 2 - 180,
        y: window.innerHeight / 2 + 120,
      });
      return;
    }

    const rect = target.getBoundingClientRect();
    const padding = step.spotlightPadding ?? 12;

    this.spotlight.set({
      x: rect.left - padding,
      y: rect.top - padding,
      width: rect.width + padding * 2,
      height: rect.height + padding * 2,
    });

    // Position card based on step.position
    const cardWidth = 360;
    const cardHeight = 240;
    let cardX = 0;
    let cardY = 0;

    switch (step.position) {
      case 'bottom':
        cardX = rect.left + rect.width / 2 - cardWidth / 2;
        cardY = rect.bottom + padding + 16;
        break;
      case 'top':
        cardX = rect.left + rect.width / 2 - cardWidth / 2;
        cardY = rect.top - padding - cardHeight - 16;
        break;
      case 'right':
        cardX = rect.right + padding + 16;
        cardY = rect.top + rect.height / 2 - cardHeight / 2;
        break;
      case 'left':
        cardX = rect.left - padding - cardWidth - 16;
        cardY = rect.top + rect.height / 2 - cardHeight / 2;
        break;
    }

    // Clamp to viewport
    cardX = Math.max(16, Math.min(cardX, window.innerWidth - cardWidth - 16));
    cardY = Math.max(16, Math.min(cardY, window.innerHeight - cardHeight - 16));

    this.cardPosition.set({ x: cardX, y: cardY });
  }
}
