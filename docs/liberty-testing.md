# Testing the Liberty Language Server Integration

This guide walks through verifying that both Liberty language servers work correctly
after being integrated into MCP Language Tools.

---

## What we're testing

Two components were integrated:

| Component | Files | What it validates |
|-----------|-------|-------------------|
| **Liberty Config LS** (`liberty-ls`) | `bootstrap.properties`, `server.env` | Invalid property values, out-of-range ports, empty values, whitespace errors |
| **LemMinX + liberty-lemminx plugin** | `server.xml` | Invalid feature names, missing required attributes, schema violations |

---

## Prerequisites

- MCP Language Tools running: `cd dev && ../mvnw quarkus:dev`
- Bob or Claude configured with `http://localhost:7654/mcp` (see [getting-started.md](./getting-started.md))
- Admin UI accessible at `http://localhost:7654/admin`

---

## Step 1 — Open the test project

A ready-made test project with intentional errors lives at `test-projects/liberty-config/`.

In your MCP client terminal:

```shell
cd test-projects/liberty-config
bob
```

or

```shell
cd test-projects/liberty-config
claude
```

---

## Step 2 — Verify servers started

Open `http://localhost:7654/admin` → **Workspaces** tab.

You should see:

```
Workspace: file:///…/test-projects/liberty-config
  🟢 liberty-ls   (Liberty Config Language Server)
  🟢 lemminx      (LemMinX XML Language Server)
```

> **If `liberty-ls` shows 🔵 Installing** — it is copying the bundled JAR on first use.
> Wait a few seconds for it to turn 🟢 Running before sending any requests.

---

## Step 3 — Validate `bootstrap.properties`

Ask your MCP client:

```
Validate src/main/liberty/config/bootstrap.properties using MCP diagnostics
```

**Expected response — 3 diagnostics:**

```
❌ Error   Line 5:  The value `DEVx` is not valid for the property
                   `com.ibm.ws.logging.console.format`.
                   Valid values: SIMPLE, ENHANCED, JSON, TBASIC

⚠️  Warning Line 8:  The value is empty for the property
                   `com.ibm.ws.logging.console.source`.
                   Check whether a value should be specified.

❌ Error   Line 11: The value `0` is not within the valid range
                   `[1..65535]` for the property `default.http.port`.
```

---

## Step 4 — Validate `server.env`

```
Validate src/main/liberty/config/server.env using MCP diagnostics
```

**Expected response — 4 diagnostics:**

```
❌ Error   Line 5:  The value `badvalue` is not valid for the variable
                   `WLP_LOGGING_CONSOLE_FORMAT`.

❌ Error   Line 8:  The value `Message` is not valid for the variable
                   `WLP_LOGGING_CONSOLE_SOURCE`.
                   (values are case-sensitive: message, trace, accessLog, ffdc, audit)

❌ Error   Line 11: The value `99999` is not within the valid range
                   `[1..65535]` for the variable `WLP_DEBUG_ADDRESS`.

⚠️  Warning Line 14: The value is empty for the variable `WLP_DEBUG_SUSPEND`.
                   Check whether a value should be specified.
```

---

## Step 5 — Validate `server.xml`

```
Validate src/main/liberty/config/server.xml using MCP diagnostics
```

**Expected response — 1 diagnostic:**

```
❌ Error   Line 5:  The feature `servlet-6.X` is not a valid Liberty feature.
```

> **Note:** LemMinX uses cached Liberty schema data (version 25.0.0.6) bundled inside
> `lemminx-liberty`. The feature list is pre-loaded so this works without a Liberty
> runtime installed.

---

## Step 6 — Check LSP traces in Admin UI

To confirm requests are flowing correctly:

1. Open `http://localhost:7654/admin` → **Workspaces** tab
2. Click your workspace
3. Click **liberty-ls** in the server list
4. The console on the right shows the LSP exchange:

```
→  textDocument/didOpen   { uri: "…/bootstrap.properties" }
←  textDocument/publishDiagnostics   { diagnostics: [ … ] }
```

5. Click **lemminx** and repeat — you should see the `server.xml` diagnostic published.

---

## Step 7 — Fix and re-validate

Ask your MCP client to fix an error:

```
Fix the invalid value on line 5 of bootstrap.properties
```

Then re-validate:

```
Validate bootstrap.properties again
```

The fixed line should no longer appear in the diagnostics.

---

## Troubleshooting

### `liberty-ls` not listed in Admin UI

The server only starts when a matching file is opened. Explicitly ask:
```
Open src/main/liberty/config/bootstrap.properties and get diagnostics
```

### Zero diagnostics returned on first request

`liberty-ls` initialises asynchronously. Wait 2–3 seconds and ask again.
This is a known POC limitation shared with JDT.LS.

### `server.xml` shows no diagnostics

Confirm the LemMinX server is 🟢 Running in Admin UI. If it shows
🔵 Installing, the liberty plugin JAR is still being copied — wait for it
to finish, then retry.

### LemMinX command uses wrong classpath separator on Windows

On Windows the `-cp` separator is `;` not `:`. The current `server.json` uses
`:` which works on macOS/Linux. If running on Windows, update
`extensions/lemminx/src/main/resources/lsp/lemminx/server.json`:

```json
"command": "java -cp \"${serverHome}/lemminx.jar;${serverHome}/plugins/*\" org.eclipse.lemminx.XMLServerLauncher"
```

---

## What the test project contains

```
test-projects/liberty-config/
└── src/main/liberty/config/
    ├── bootstrap.properties   ← 3 intentional errors
    ├── server.env             ← 4 intentional errors
    └── server.xml             ← 1 intentional error
```

All errors are taken directly from the Liberty Config Language Server's own
test suite (`liberty-ls/src/test/resources/workspace/diagnostic/`), so the
expected diagnostics are authoritative.
