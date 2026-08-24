import { Pipe, PipeTransform } from '@angular/core';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

/**
 * Pipe that converts markdown text to sanitized HTML.
 * Uses `marked` for parsing. Strips dangerous tags via a basic
 * allowlist approach (no script/iframe/object).
 *
 * Usage: `[innerHTML]="text | markdown"`
 */
@Pipe({ name: 'markdown', standalone: true, pure: true })
export class MarkdownPipe implements PipeTransform {
  constructor() {
    marked.setOptions({
      breaks: true,
      gfm: true,
    });
  }

  transform(value: string | null | undefined): string {
    if (!value) return '';
    try {
      const raw = marked.parse(value) as string;
      return this.sanitize(raw);
    } catch {
      return value;
    }
  }

  /**
   * Sanitize rendered Markdown with the project's trusted HTML sanitizer.
   * Regex-based removal is unsafe for multi-character tags and attributes
   * because it can leave an exploitable remainder in the HTML.
   */
  private sanitize(html: string): string {
    return DOMPurify.sanitize(html);
  }
}
