# Spector Zero-Install MCP & CLI Runner (`@spectrayan/spector`)

> **The fastest path to connecting Claude Desktop, Cursor, and AI agents to Spector Cognitive Memory & Vector Search.**

`@spectrayan/spector` is a lightweight, zero-install Node runner. It auto-detects running local Spector Synapse instances (on `:7070` or `:7700`) to bridge stdio JSON-RPC without delay, or automatically downloads and launches `spector.jar` with Project Panama and Vector API SIMD flags enabled.

---

## 30-Second MCP Setup

### 1. Claude Desktop (`claude_desktop_config.json`)
```json
{
  "mcpServers": {
    "spector": {
      "command": "npx",
      "args": ["-y", "@spectrayan/spector", "mcp"]
    }
  }
}
```

### 2. Cursor (`~/.cursor/mcp.json`)
```json
{
  "mcpServers": {
    "spector": {
      "command": "npx",
      "args": ["-y", "@spectrayan/spector", "mcp"]
    }
  }
}
```

### 3. Windsurf (`~/.codeium/windsurf/mcp_config.json`)
```json
{
  "mcpServers": {
    "spector": {
      "command": "npx",
      "args": ["-y", "@spectrayan/spector", "mcp"]
    }
  }
}
```

---

## CLI Usage

You can also run any Spector CLI command without installation:

```bash
# Check local environment and connectivity
npx -y @spectrayan/spector doctor

# Generate starter configuration (~/.spector/spector.yml)
npx -y @spectrayan/spector init

# Query memory
npx -y @spectrayan/spector recall "user preferences"
```

---

## License

Apache License, Version 2.0 — see [LICENSE](../../../LICENSE) for details.
