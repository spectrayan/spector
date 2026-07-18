import { Pipe, PipeTransform } from '@angular/core';
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
   * Basic HTML sanitization — strips script, iframe, object, embed tags
   * and on* event handlers. For production, consider DOMPurify.
   */
  private sanitize(html: string): string {
    return html
      .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
      .replace(/<iframe\b[^>]*>.*?<\/iframe>/gi, '')
      .replace(/<object\b[^>]*>.*?<\/object>/gi, '')
      .replace(/<embed\b[^>]*\/?>/gi, '')
      .replace(/\bon\w+\s*=\s*"[^"]*"/gi, '')
      .replace(/\bon\w+\s*=\s*'[^']*'/gi, '');
  }
}
