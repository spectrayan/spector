/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Strips trailing forward slashes from a URL or path without regular expressions
 * to prevent polynomial ReDoS security vulnerabilities on untrusted input.
 */
export function trimTrailingSlashes(str: string): string {
  let end = str.length;
  while (end > 0 && str.charCodeAt(end - 1) === 47 /* '/' */) {
    end--;
  }
  return str.substring(0, end);
}

/**
 * Strips leading forward slashes from a URL or path without regular expressions
 * to prevent polynomial ReDoS security vulnerabilities on untrusted input.
 */
export function trimLeadingSlashes(str: string): string {
  let start = 0;
  while (start < str.length && str.charCodeAt(start) === 47 /* '/' */) {
    start++;
  }
  return str.substring(start);
}

/**
 * Safely joins a base URL with a subpath without duplicate slashes or regex evaluation.
 */
export function joinUrl(baseUrl: string, subPath: string): string {
  const cleanBase = trimTrailingSlashes(baseUrl);
  const cleanPath = trimLeadingSlashes(subPath);
  return cleanBase ? `${cleanBase}/${cleanPath}` : cleanPath;
}
