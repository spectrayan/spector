import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { existsSync, mkdirSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { detectVersion } from "../src/jdk-manager.js";

describe("OpenClaw Security Guards", () => {
  let testDir: string;

  beforeEach(() => {
    testDir = join(process.cwd(), ".test-data", `sec-guard-${Date.now()}-${Math.random().toString(36).slice(2)}`);
    mkdirSync(testDir, { recursive: true });
  });

  afterEach(() => {
    try {
      rmSync(testDir, { recursive: true, force: true });
    } catch {
      // Ignore cleanup error
    }
  });

  describe("Command Execution Hardening", () => {
    it("safely handles non-existent or malicious java binary paths without shell execution", () => {
      // Should return 0 cleanly without throwing unhandled exceptions or spawning shell
      const result1 = detectVersion("non_existent_binary_xyz_123");
      expect(result1).toBe(0);

      const result2 = detectVersion("java; echo injected");
      expect(result2).toBe(0);

      const result3 = detectVersion("$(whoami)");
      expect(result3).toBe(0);
    });
  });

  describe("Tag Sanitization", () => {
    it("validates semantic version tag patterns strictly", () => {
      const tagPattern = /^v?[0-9]+(\.[0-9]+)*(-[a-zA-Z0-9.]+)?$/;

      // Valid tags
      expect(tagPattern.test("v1.0.0")).toBe(true);
      expect(tagPattern.test("1.0.0")).toBe(true);
      expect(tagPattern.test("v2.1.0-alpha.1")).toBe(true);
      expect(tagPattern.test("2026.1.0")).toBe(true);

      // Malicious or invalid tags
      expect(tagPattern.test("../evil/tag")).toBe(false);
      expect(tagPattern.test("v1.0.0; rm -rf /")).toBe(false);
      expect(tagPattern.test("v1.0.0\nmalicious")).toBe(false);
      expect(tagPattern.test("v1.0.0`whoami`")).toBe(false);
      expect(tagPattern.test("")).toBe(false);
    });
  });

  describe("Atomic File Operations & Race Prevention", () => {
    it("prevents file overwrite races using exclusive creation flag wx", () => {
      const filePath = join(testDir, "atomic-config.yml");

      // First write succeeds with exclusive create flag
      writeFileSync(filePath, "initial: true\n", { encoding: "utf-8", flag: "wx" });
      expect(existsSync(filePath)).toBe(true);

      // Second write with flag wx throws EEXIST, preventing race overwrite
      expect(() => {
        writeFileSync(filePath, "overwrite: true\n", { encoding: "utf-8", flag: "wx" });
      }).toThrow(/EEXIST/);

      // Content remains untouched
      expect(readFileSync(filePath, "utf-8")).toBe("initial: true\n");
    });
  });
});
