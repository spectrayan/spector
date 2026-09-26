#!/usr/bin/env python3
#
# Copyright 2026 Spectrayan
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
CI & Pre-commit Quality Gate: MkDocs List Formatting Validator.

Validates that:
1. Markdown source files in docs/ do not contain unseparated lists.
2. Built HTML files in site/ do not contain squashed list paragraphs (<p> containing list markers).
"""

import os
import sys
import re
import argparse


def check_site_html(site_dir: str = "site") -> list[tuple[str, str]]:
    """Scans generated HTML files for squashed list paragraphs."""
    if not os.path.exists(site_dir):
        print(f"Warning: Site directory '{site_dir}' does not exist. Skipping HTML check.")
        return []
        
    broken = []
    for root, _, files in os.walk(site_dir):
        for f in files:
            if f.endswith('.html'):
                file_path = os.path.join(root, f)
                try:
                    with open(file_path, 'r', encoding='utf-8', errors='ignore') as fp:
                        html = fp.read()
                except Exception:
                    continue
                    
                p_matches = re.finditer(r'<p>(.*?)</p>', html, re.DOTALL)
                for m in p_matches:
                    content = m.group(1)
                    # Strip math blocks, code tags, and equations to avoid false positives on math subtraction or code flags
                    clean = re.sub(r'<span class=[\'"]?arithmatex[\'"]?>.*?</span>', '', content, flags=re.DOTALL)
                    clean = re.sub(r'<code>.*?</code>', '', clean, flags=re.DOTALL)
                    clean = re.sub(r'\\\(.*?\\\)', '', clean, flags=re.DOTALL)
                    clean = re.sub(r'\$\$.*?\$\$', '', clean, flags=re.DOTALL)
                    clean = re.sub(r'\$.*?\$', '', clean, flags=re.DOTALL)
                    
                    # Detect unrendered list items inside paragraph
                    bullet_matches = re.findall(r'(?:\s+-\s+|\s+\d+\.\s+)', clean)
                    if len(bullet_matches) >= 2 or re.search(r'[:.!?]\s+-\s+(?:<strong|<em|[A-Z])', clean):
                        # Ensure it's not just a parenthetical date range or em-dash
                        if re.search(r'[:.!?]\s+-\s+', clean) or len(bullet_matches) >= 2:
                            rel_path = os.path.relpath(file_path, site_dir)
                            broken.append((rel_path, content.strip()[:100]))
    return broken


def check_markdown_source(docs_dir: str = "docs") -> list[tuple[str, int, str]]:
    """Scans markdown files for unseparated list items."""
    code_fence_re = re.compile(r'^(\s*)(`{3,}|~{3,})')
    list_marker_re = re.compile(r'^(\s*)([-*+]|\d+\.)\s+')
    
    issues = []
    for root, _, files in os.walk(docs_dir):
        for f in files:
            if f.endswith('.md'):
                file_path = os.path.join(root, f)
                rel_path = os.path.relpath(file_path, docs_dir)
                try:
                    with open(file_path, 'r', encoding='utf-8', errors='ignore') as fp:
                        lines = fp.readlines()
                except Exception:
                    continue
                    
                in_code = False
                in_frontmatter = False
                for i, line in enumerate(lines):
                    line_s = line.strip()
                    if i == 0 and line_s == '---':
                        in_frontmatter = True
                        continue
                    if in_frontmatter:
                        if line_s == '---':
                            in_frontmatter = False
                        continue
                    if code_fence_re.match(line):
                        in_code = not in_code
                        continue
                    if in_code:
                        continue
                        
                    if list_marker_re.match(line):
                        if i > 0:
                            prev_s = lines[i - 1].strip()
                            prev_is_list = bool(list_marker_re.match(lines[i - 1]))
                            if prev_s != '' and not prev_is_list:
                                if prev_s.endswith(':') or prev_s.endswith('.'):
                                    issues.append((rel_path, i + 1, f"Preceded by prose without blank line: '{prev_s[:40]}'"))
    return issues


def main():
    parser = argparse.ArgumentParser(description="Validate list formatting across docs and site HTML.")
    parser.add_argument("--docs-dir", default="docs", help="Docs source directory.")
    parser.add_argument("--site-dir", default="site", help="Built site directory.")
    parser.add_argument("--skip-html", action="store_true", help="Skip built HTML scan.")
    args = parser.parse_args()
    
    has_failure = False
    
    # 1. Source check
    print(f"Auditing Markdown source files in '{args.docs_dir}'...")
    src_issues = check_markdown_source(args.docs_dir)
    if src_issues:
        print(f"❌ Found {len(src_issues)} unseparated list issues in Markdown source:")
        for path, line, msg in src_issues[:15]:
            print(f"   {path}:{line} -> {msg}")
        if len(src_issues) > 15:
            print(f"   ... and {len(src_issues) - 15} more.")
        has_failure = True
    else:
        print("✅ All Markdown source lists are correctly formatted and separated.")
        
    # 2. HTML check
    if not args.skip_html:
        print(f"\nAuditing built HTML in '{args.site_dir}'...")
        html_issues = check_site_html(args.site_dir)
        if html_issues:
            print(f"❌ Found {len(html_issues)} squashed list paragraphs in generated HTML:")
            for path, snippet in html_issues[:15]:
                print(f"   {path} -> \"{snippet}...\"")
            if len(html_issues) > 15:
                print(f"   ... and {len(html_issues) - 15} more.")
            has_failure = True
        else:
            print("✅ Zero squashed list paragraphs detected in generated HTML.")
            
    if has_failure:
        sys.exit(1)
    print("\n🎉 Quality gate PASSED.")


if __name__ == "__main__":
    main()
