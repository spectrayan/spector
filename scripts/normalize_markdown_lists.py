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
Deterministic Markdown List Normalizer for Spectrayan / MkDocs.

Ensures that all Markdown lists (bulleted and ordered) are preceded by a mandatory
blank line and that nested lists conform to the 4-space indentation standard required
by Python-Markdown (MkDocs), preventing list-to-paragraph collapse.
"""

import os
import sys
import re
import argparse

CODE_FENCE_PATTERN = re.compile(r'^(\s*)(`{3,}|~{3,})')
LIST_MARKER_PATTERN = re.compile(r'^((?:\s*>\s*)*)(\s*)([-*+]|\d+\.)\s+(.*)$')


def normalize_markdown_content(content: str) -> str:
    """Normalizes unseparated lists and 2-space nesting in markdown content.
    
    Preserves fenced code blocks, YAML frontmatter, HTML blocks, and tables.
    """
    lines = content.split('\n')
    new_lines = []
    
    in_code_block = False
    in_frontmatter = False
    indent_stack = []
    
    for i, line in enumerate(lines):
        line_stripped = line.strip()
        
        # 1. Handle YAML frontmatter
        if i == 0 and line_stripped == '---':
            in_frontmatter = True
            new_lines.append(line)
            continue
        if in_frontmatter:
            if line_stripped == '---':
                in_frontmatter = False
            new_lines.append(line)
            continue
            
        # 2. Handle fenced code blocks
        if CODE_FENCE_PATTERN.match(line):
            in_code_block = not in_code_block
            indent_stack = []
            new_lines.append(line)
            continue
            
        if in_code_block:
            new_lines.append(line)
            continue
            
        # 3. Check for list marker (with optional blockquote prefix)
        list_match = LIST_MARKER_PATTERN.match(line)
        if list_match:
            quote_prefix, indent, marker, rest = list_match.groups()
            orig_indent_len = len(indent)
            
            # Check preceding line in new_lines
            if new_lines:
                prev_line = new_lines[-1]
                prev_stripped = prev_line.strip()
                prev_list_match = LIST_MARKER_PATTERN.match(prev_line)
                
                # Preceding line has content and is NOT a list item
                prev_is_blank = (prev_stripped == '' or prev_stripped.strip('> ') == '')
                if not prev_is_blank and not prev_list_match:
                    indent_stack = []
                    if quote_prefix:
                        # Blockquote list: insert quote prefix
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
            
            # Handle indentation alignment for nested lists
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
            if line_stripped == '':
                pass
            else:
                if not (line.startswith('    ') or line.startswith('\t')):
                    indent_stack = []
            
        new_lines.append(line)
        
    return '\n'.join(new_lines)


def process_file(file_path: str, check_only: bool = False) -> bool:
    """Processes a single file. Returns True if file was changed / has issues."""
    try:
        with open(file_path, 'r', encoding='utf-8') as fp:
            original = fp.read()
    except Exception as e:
        print(f"Error reading {file_path}: {e}", file=sys.stderr)
        return False
        
    normalized = normalize_markdown_content(original)
    if original != normalized:
        if check_only:
            print(f"[NEEDS FIX] {file_path}")
            return True
        else:
            with open(file_path, 'w', encoding='utf-8') as fp:
                fp.write(normalized)
            print(f"[NORMALIZED] {file_path}")
            return True
    return False


SKIP_DIRS = {'node_modules', '.git', 'target', 'dist', '.angular', 'venv', '.venv', '.nx'}

def process_directory(directory: str, check_only: bool = False) -> tuple[int, int]:
    """Recursively processes all markdown files in directory, skipping dependency and build directories."""
    total_files = 0
    modified_files = 0
    for root, dirs, files in os.walk(directory):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not d.startswith('.')]
        for f in files:
            if f.endswith('.md'):
                total_files += 1
                path = os.path.join(root, f)
                if process_file(path, check_only=check_only):
                    modified_files += 1
    return total_files, modified_files


def main():
    parser = argparse.ArgumentParser(description="Normalize markdown list formatting for MkDocs.")
    parser.add_argument("paths", nargs="*", default=["docs"], help="Files or directories to process.")
    parser.add_argument("--check", action="store_true", help="Only check for issues without modifying files.")
    args = parser.parse_args()
    
    total = 0
    modified = 0
    for target in args.paths:
        if os.path.isfile(target):
            total += 1
            if process_file(target, check_only=args.check):
                modified += 1
        elif os.path.isdir(target):
            t, m = process_directory(target, check_only=args.check)
            total += t
            modified += m
        else:
            print(f"Path not found: {target}", file=sys.stderr)
            
    action = "found with formatting issues" if args.check else "normalized"
    print(f"\nSummary: {modified}/{total} markdown files {action}.")
    if args.check and modified > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
