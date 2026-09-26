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
MkDocs build hook to automatically normalize unseparated lists and 2-space nesting
in Markdown pages at build time, preventing list-to-paragraph collapse.
"""

import sys
import os

# Ensure scripts directory is importable
SCRIPTS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "scripts"))
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

try:
    from normalize_markdown_lists import normalize_markdown_content
except ImportError:
    # Fallback inline if script directory is separated in distribution
    import re
    CODE_FENCE_PATTERN = re.compile(r'^(\s*)(`{3,}|~{3,})')
    ITEM_PATTERN = re.compile(r'^([ \t]*)([-*+]|\d+\.)[ \t]+(.*)$')

    def parse_list_marker(line: str):
        quote_prefix = ""
        content = line
        stripped = line.lstrip(" \t")
        if stripped.startswith(">"):
            idx = 0
            while idx < len(line) and line[idx] in " \t>":
                idx += 1
            quote_prefix = line[:idx]
            content = line[idx:]
        m = ITEM_PATTERN.match(content)
        if m:
            indent, marker, rest = m.groups()
            return quote_prefix, indent, marker, rest
        return None

    def normalize_markdown_content(content: str) -> str:
        lines = content.split('\n')
        new_lines = []
        in_code_block = False
        in_frontmatter = False
        indent_stack = []
        
        for i, line in enumerate(lines):
            line_stripped = line.strip()
            if i == 0 and line_stripped == '---':
                in_frontmatter = True
                new_lines.append(line)
                continue
            if in_frontmatter:
                if line_stripped == '---':
                    in_frontmatter = False
                new_lines.append(line)
                continue
            if CODE_FENCE_PATTERN.match(line):
                in_code_block = not in_code_block
                indent_stack = []
                new_lines.append(line)
                continue
            if in_code_block:
                new_lines.append(line)
                continue
            list_match = parse_list_marker(line)
            if list_match:
                quote_prefix, indent, marker, rest = list_match
                orig_indent_len = len(indent)
                if new_lines:
                    prev_line = new_lines[-1]
                    prev_stripped = prev_line.strip()
                    prev_list_match = bool(parse_list_marker(prev_line))
                    prev_is_blank = (prev_stripped == '' or prev_stripped.strip('> ') == '')
                    if not prev_is_blank and not prev_list_match:
                        indent_stack = []
                        if quote_prefix:
                            new_lines.append(quote_prefix.rstrip())
                        elif prev_stripped.startswith('|') and prev_stripped.endswith('|'):
                            new_lines.append('')
                        elif prev_stripped.startswith('#'):
                            new_lines.append('')
                        elif prev_stripped.startswith('>'):
                            q_match = re.match(r'^(\s*>+\s*)', prev_line)
                            q_pfx = q_match.group(1).rstrip() if q_match else '>'
                            new_lines.append(q_pfx)
                        elif prev_stripped.startswith('!!!') or prev_stripped.startswith('???'):
                            new_lines.append('    ')
                        else:
                            base_indent = '    ' if orig_indent_len >= 4 and line.startswith('    ') else ''
                            new_lines.append(base_indent)
                while indent_stack and indent_stack[-1][0] > orig_indent_len:
                    indent_stack.pop()
                if not indent_stack:
                    indent_stack.append((orig_indent_len, orig_indent_len))
                    target_indent_len = orig_indent_len
                else:
                    top_orig, top_new = indent_stack[-1]
                    if orig_indent_len == top_orig:
                        target_indent_len = top_new
                    elif orig_indent_len > top_orig:
                        diff = orig_indent_len - top_orig
                        new_diff = 4 if diff < 4 else diff
                        target_indent_len = top_new + new_diff
                        indent_stack.append((orig_indent_len, target_indent_len))
                    else:
                        target_indent_len = orig_indent_len
                        for o, n in reversed(indent_stack):
                            if o <= orig_indent_len:
                                target_indent_len = n
                                break
                new_indent = ' ' * target_indent_len
                line = f"{quote_prefix}{new_indent}{marker} {rest}"
            else:
                if line_stripped != '':
                    if not (line.startswith('    ') or line.startswith('\t')):
                        indent_stack = []
            new_lines.append(line)
        return '\n'.join(new_lines)


def on_page_markdown(markdown, page, config, files):
    """MkDocs hook event: normalizes list formatting before Python-Markdown parses it."""
    return normalize_markdown_content(markdown)
