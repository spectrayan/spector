import { Directive, inject, input, TemplateRef, ViewContainerRef, effect } from '@angular/core';
import { FeatureFlagService } from '../../core/services/feature-flag.service';

/**
 * Structural directive that conditionally renders content based on a feature flag.
 *
 * Usage:
 * ```html
 * <div *featureGate="'chatEnabled'">Only visible when chat is enabled</div>
 * ```
 *
 * The directive is reactive — if flags are reloaded and the value changes,
 * the view is automatically created or destroyed.
 */
@Directive({
  selector: '[featureGate]',
  standalone: true,
})
export class FeatureGateDirective {
  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly featureFlags = inject(FeatureFlagService);

  /** The feature flag name to gate on. */
  readonly featureGate = input.required<string>();

  private hasView = false;

  constructor() {
    effect(() => {
      const enabled = this.featureFlags.isEnabled(this.featureGate());

      if (enabled && !this.hasView) {
        this.viewContainer.createEmbeddedView(this.templateRef);
        this.hasView = true;
      } else if (!enabled && this.hasView) {
        this.viewContainer.clear();
        this.hasView = false;
      }
    });
  }
}
