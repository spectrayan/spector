import {
  Component,
  OnDestroy,
  OnInit,
  ElementRef,
  ViewChild,
  ChangeDetectionStrategy,
  NgZone,
  signal,
  input,
  effect,
} from '@angular/core';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

/**
 * Standalone Markdown Preview component.
 *
 * Renders raw markdown text as sanitized HTML with:
 * - GitHub-flavored markdown (headings, code, tables, lists, etc.)
 * - Debounced re-render (300ms) for live typing performance
 * - Theme-aware styling via CSS variables (dark/light)
 * - Scrollable container matching editor height
 *
 * Usage:
 * ```html
 * <cortex-markdown-preview
 *   [content]="markdownText"
 *   [height]="'400px'"
 * />
 * ```
 */
@Component({
  selector: 'cortex-markdown-preview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './markdown-preview.component.html',
  styleUrls: ['./markdown-preview.component.scss'],
})
export class MarkdownPreviewComponent implements OnInit, OnDestroy {
  @ViewChild('previewContainer', { static: true }) previewContainer!: ElementRef<HTMLDivElement>;

  /** Raw markdown content to render. */
  readonly content = input('');

  /** CSS min-height of the preview container (should match editor height). */
  readonly height = input('400px');

  /** Whether to show the top preview toolbar. */
  readonly showToolbar = input(false);

  /** Rendered & sanitized HTML output. */
  readonly renderedHtml = signal('');

  private _debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private _themeObserver: MutationObserver | null = null;

  constructor(private ngZone: NgZone) {
    // Re-render when content input signal changes
    effect(() => {
      const _ = this.content();  // track the signal
      this._debouncedRender();
    });
  }

  ngOnInit(): void {
    this.renderContent();
    this._observeTheme();
  }

  ngOnDestroy(): void {
    if (this._debounceTimer) {
      clearTimeout(this._debounceTimer);
    }
    this._themeObserver?.disconnect();
  }

  // ═══════════════════════════════════════════════════════════════════
  // Rendering Pipeline
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Debounce rendering to 300ms so rapid typing doesn't thrash the DOM.
   */
  private _debouncedRender(): void {
    if (this._debounceTimer) {
      clearTimeout(this._debounceTimer);
    }
    this._debounceTimer = setTimeout(() => {
      this.renderContent();
    }, 300);
  }

  /**
   * Full render pipeline: markdown → HTML → sanitize → signal update.
   * Runs outside Angular zone for performance.
   */
  private renderContent(): void {
    const raw = this.content();
    if (!raw?.trim()) {
      this.renderedHtml.set('<p class="preview-empty">Nothing to preview</p>');
      return;
    }

    this.ngZone.runOutsideAngular(async () => {
      try {
        // Normalize literal escaped newlines (\\n) that may come from
        // AI-generated content or JSON serialization artifacts
        const normalizedContent = raw
          .replace(/\\n/g, '\n')
          .replace(/\\t/g, '\t');

        const rawHtml = await marked.parse(normalizedContent, {
          gfm: true,
          breaks: true,
        });

        const cleanHtml = DOMPurify.sanitize(rawHtml, {
          ADD_ATTR: ['class', 'id', 'target', 'rel'],
          ADD_TAGS: ['details', 'summary'],
        });

        this.ngZone.run(() => {
          this.renderedHtml.set(cleanHtml);
        });
      } catch {
        this.ngZone.run(() => {
          this.renderedHtml.set('<p class="preview-error">Failed to render markdown</p>');
        });
      }
    });
  }

  /**
   * Watch for theme changes to update code block styling.
   */
  private _observeTheme(): void {
    this._themeObserver = new MutationObserver(() => {
      // Theme change may affect styling; trigger re-render of CSS classes
      // No action needed since styles use CSS variables
    });
    this._themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme'],
    });
  }
}
