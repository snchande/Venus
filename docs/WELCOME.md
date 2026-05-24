# Welcome to Venus Notebooks

> This is the **canonical welcome**. The `venus welcome` CLI command and the **venus** AI agent both deliver this same experience — so whether you arrive through the terminal or through an AI co-pilot, you get one consistent starting point.

Venus is a local, browser-based notebook for **seven languages** (JShell · Java · JavaScript · TypeScript · C# · F# · C++) with three AI co-pilots wired in and the **whole system exposed over MCP**. Everything runs on your machine.

---

## Pick how you want to work

There are three ways to work with Venus. You can mix them freely.

### 1 · Open the UI (the full notebook experience)

The richest way to use Venus — cells, live output, package managers, the AI panel, tutorials.

```bash
venus start          # builds if needed, starts the server, opens the browser
# already running?   ->  venus open
```

Then visit **http://localhost:8585**. In the UI you can press **Ctrl+\\** for the AI panel and open the in-app **docs overlay** for guidance without leaving the page.

### 2 · Drive Venus from the CLI via MCP (operate & automate)

Venus runs an **MCP (Model Context Protocol) server** while it's up, so any MCP-aware client — Claude Code, Claude Desktop, or a custom agent — can drive it programmatically:

- **SSE stream:** `GET  http://localhost:8585/api/mcp/sse`
- **Messages:**   `POST http://localhost:8585/api/mcp/messages`

Available MCP tools: `venus_execute_code`, `venus_list_notebooks`, `venus_read_notebook`,
`venus_run_pipeline`, `venus_search_cells`, `venus_load_module`, `venus_create_notebook`,
`venus_append_cell`.

The plain **`venus` command line** manages the lifecycle (`start` · `stop` · `status` · `open` · `logs`) and shows you how to connect. **It does not modify Venus's own code** — it operates Venus, it doesn't personalize it.

### 3 · Personalize & extend Venus (only via an agentic CLI)

This is the part a plain CLI can't do. Run your favorite **agentic CLI** inside the repo —
`claude`, `copilot`, or `gemini` — and the **venus** agent helps you change and grow Venus itself:
add a language, tweak the theme, write a tutorial, fix a bug. It follows the architecture
guardrails in [`AGENTS.md`](../AGENTS.md), verifies locally, runs the security check, and can
package the change as a PR.

```bash
claude        # or: copilot   /   gemini   — run from the repo root
```

> **The one difference that matters:** the plain `venus` CLI lets you **operate and automate** Venus (including over MCP). An **agentic CLI** lets you do all of that **and personalize/extend** Venus — because it can write code, and Venus is built to be reshaped by the people who use it.

---

## Documentation

| What | Where | Open it |
|------|-------|---------|
| Product brochure (12-page PDF) | `docs/brochure/venus-brochure.pdf` | `venus docs` |
| Getting started + features | `README.md` | `venus docs` |
| Architecture (diagrams, layers) | `docs/ARCHITECTURE.md` | `venus docs` |
| API + MCP reference | `docs/API.md` | `venus docs` |
| AI-contributor guardrails | `AGENTS.md` | `venus docs` |
| In-app cheat sheet | `docs/cheatsheet.html` | open in browser |
| Live, in-UI docs overlay | the running app | press the docs button in the UI |

`venus docs` opens the brochure and prints links to the rest. In an agentic CLI, just ask:
*"open the Venus architecture docs"* or *"show me how MCP works in Venus"* and the venus agent
will surface the right file.

---

## Quick reference

```
venus welcome     show this welcome and your options
venus start       start the server + open the UI
venus open        open the UI (server already running)
venus status      server state + detected runtimes + AI co-pilots
venus agents      AI co-pilots, skills & the venus agent wired into this repo
venus docs        open the brochure and list the docs
venus stop        stop the server
```

Need to extend Venus? Start an agentic CLI in this folder and say *"help me get started with Venus."*
