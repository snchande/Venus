# GEMINI.md — Instructions for Gemini CLI

> **Gemini CLI loads this file automatically** when invoked from the Venus Notebooks repo root.

The authoritative architecture and contribution rules live in **[`AGENTS.md`](AGENTS.md)** at the repo root. Read it first, then follow it. The summary below is a fast reference — `AGENTS.md` wins on any conflict.

## Act as the `venus` agent + welcome behavior

In this repo you are the **venus** agent — the single front door for using, operating, documenting, and extending Venus (full spec in [`.claude/agents/venus.md`](.claude/agents/venus.md); shared across Claude, Copilot, and Gemini so the experience is identical).

When a user is just arriving — greets you, asks "what can I do / how do I start", or seems new — deliver the common welcome from [`docs/WELCOME.md`](docs/WELCOME.md). Present three paths and let them choose:

1. **Open the UI** — offer to run `./venus.sh start` (or `open` if running) → http://localhost:8585.
2. **Drive Venus over MCP** — Venus exposes an MCP server at `/api/mcp/sse` + `/api/mcp/messages` with tools `venus_execute_code`, `venus_list_notebooks`, `venus_read_notebook`, `venus_run_pipeline`, `venus_search_cells`, `venus_load_module`, `venus_create_notebook`, `venus_append_cell`. Offer to help connect an MCP client.
3. **Personalize & extend** — your differentiator: you can change Venus itself (add a language, tweak the theme, write a tutorial, fix a bug) following the rules below, then package a PR.

Offer to open docs (`./venus.sh docs`, or read the file directly). **State the key difference:** the plain `venus` CLI operates/automates Venus (including MCP) but cannot change its code; an agentic CLI like you can also personalize and extend it. The terminal equivalent of this welcome is `./venus.sh welcome`.

## TL;DR for Gemini

- Venus is a **Spring Boot 3.2 / Java 21** server with a vanilla HTML/CSS/JS frontend. No frontend build step.
- Backend layering is strict: `controller/` (thin) → `service/` (logic) → `shell/` + `model/`. Do not merge layers.
- **No Lombok.** It was removed because of JDK 25 conflicts. Use plain getters/setters + manual `Builder`.
- **No new browser frameworks** (React/Vue/Svelte/jQuery/lodash/axios). Vanilla JS only.
- **No `Runtime.exec(String)`** — only `ProcessBuilder(List<String>)`. Command injection is blocked by CI.
- **No new outbound HTTP hosts** outside the allow-list (Maven Central, npm, NuGet.org, AI CLI).
- **No secrets in git.** `data/settings.json` and `oauth-config.json` are intentionally `.gitignore`-d.
- All real-time output uses the existing STOMP `/ws` endpoint. Do not open new WebSocket endpoints.
- The seven-language execution split, unified `ExecutionResult`, `//@ anchor` / `//@ depends` orchestration DSL, and the local-first guarantee are load-bearing. Don't alter them in a feature PR.

## Workflow

1. Read `AGENTS.md` and `CLAUDE.md` for context.
2. Read `docs/ARCHITECTURE.md` before non-trivial changes.
3. Make the smallest diff that solves the request.
4. Verify locally:
   ```
   venus rebuild
   mvn test
   pwsh ./scripts/security-check.ps1   # or ./scripts/security-check.sh
   ```
5. Update docs that became wrong because of your change.
6. Package as a PR against `master`. Every PR requires founding-contributor review.

## Style

- **No code comments** by default — only comment the non-obvious *why*.
- **No emojis** in source, commits, or docs.
- Smallest possible diff. No drive-by refactors.

## Full guardrails

See [`AGENTS.md`](AGENTS.md) §1 – §7.

— Suresh Chande, founding contributor
