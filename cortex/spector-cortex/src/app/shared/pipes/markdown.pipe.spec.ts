import { describe, expect, it } from 'vitest';
import { MarkdownPipe } from './markdown.pipe';

describe('MarkdownPipe', () => {
  it('removes executable HTML from rendered markdown', () => {
    const html = new MarkdownPipe().transform(
      '<img src=x onerror=alert(1)><script>alert(1)</script><iframe src="evil"></iframe>',
    );

    expect(html).not.toContain('onerror');
    expect(html).not.toContain('<script');
    expect(html).not.toContain('<iframe');
  });
});
