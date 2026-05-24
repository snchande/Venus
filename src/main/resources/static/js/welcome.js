/* ════════════════════════════════════════════════════════════════
   Venus Notebooks — Welcome / User Guide / What's New
   ----------------------------------------------------------------
   • Shows automatically on first run.
   • Shows a "What's New" panel when the bundled version changes
     (i.e. after the user updates Venus from the repo).
   • Reopenable any time from the Help button in the top bar, or by
     clicking the version in the status bar.
   ════════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  // Bump this with every release. Changing it makes returning users see
  // the What's New panel on their next load.
  const VENUS_VERSION = '3.0.0';

  // Link to the full changelog (release-by-release history lives in-app below,
  // and in this file for those who want the raw detail).
  const CHANGELOG_URL = 'https://github.com/snchande/Venus/blob/master/CHANGELOG.md';

  const LS_SEEN    = 'venus.guide.seen';      // "1" once the user has seen the welcome
  const LS_VERSION = 'venus.guide.version';   // last version the user acknowledged
  const LS_MUTE    = 'venus.guide.mute';      // "1" = don't auto-show the welcome

  // ── Highlights (short) ──────────────────────────────────────────
  const HIGHLIGHTS = [
    '7 languages', 'Live output', '3 AI co-pilots',
    'Maven · npm · NuGet', 'Pipelines', 'MCP server',
    '28 tutorials', 'Local-first'
  ];

  // ── Release-by-release history (newest first) ───────────────────
  // The first entry is the current "What's New". Older entries power the
  // "See all releases" view. Keep each item short.
  const RELEASES = [
    {
      version: '3.0.0', date: '2026-05-24',
      title: 'Major release — Venus goes AI-native & agentic',
      items: [
        'AI is multi-provider and local-first: Claude, GitHub Copilot, and Gemini run as local CLIs — no API keys.',
        'Venus is now an MCP server — drive notebooks, cells, packages, and pipelines from any agent.',
        'Authentication: local (default) and OAuth2 modes.',
        'Cross-platform venus CLI: start · stop · status · welcome · docs · agents.',
        'Agentic contributor stack: AGENTS.md guardrails, the venus agent, a PR security gate, and a product brochure.',
        'Variable inspector extended to all subprocess languages (JS, TS, C#, F#, C++) + tab UX.',
        'This in-app Welcome & User Guide — reopen anytime from Help.'
      ]
    },
    {
      version: '2.1.0', date: '2026-05-10',
      title: 'TypeScript — the seventh language',
      items: [
        'TypeScript cells via Node.js built-in type-stripping (Node 22.6+); no extra runtime.',
        'Typed Venus helpers; shares npm modules with JavaScript; tsc --noEmit diagnostics optional.'
      ]
    },
    {
      version: '2.0.0', date: '2026-04-17',
      title: 'C# and F# — .NET comes to Venus',
      items: [
        'C# cells via dotnet run (C# 9+ top-level programs); F# cells via dotnet fsi.',
        'NuGet package management and pipeline dependency injection across all modes.'
      ]
    },
    {
      version: '1.2.0', date: '2026-03-29',
      title: 'Multi-runtime interactive console',
      items: [
        'Console supports three runtimes — JShell, Java, JavaScript — with code completion.'
      ]
    },
    {
      version: '1.1.0', date: '2026-03-14',
      title: 'Pipelines & multi-mode cells',
      items: [
        'PIPELINE cell type with the //@ anchor / //@ depends DSL and topological execution.',
        'Multi-mode cells (JShell / Java / JS).'
      ]
    },
    {
      version: '1.0.0', date: '2026-03-08',
      title: 'Initial release',
      items: [
        'JShell-powered notebooks, Maven + npm installers, AI assistant, real-time output.'
      ]
    }
  ];

  const WHATS_NEW = RELEASES[0];

  // ── Guided tracks ────────────────────────────────────────────────
  // Each step: { do: short instruction, where: UI location/pointer }
  const TRACKS = {
    overview: {
      label: 'Overview',
      blurb: 'New to Venus? Start here.',
      icon: 'M8 1l2 4 4 .5-3 3 .8 4.5L8 11l-3.8 2 .8-4.5-3-3L6 5z',
      steps: [
        { do: 'Write code in a cell, then run it with Ctrl+Enter.', where: 'The notebook canvas (center).' },
        { do: 'Switch a cell between 7 languages with its mode badge.', where: 'Top-right of each cell.' },
        { do: 'Ask AI to generate, explain, or convert code.', where: 'AI panel — press Ctrl+\\.' },
        { do: 'Learn by example from 28 built-in tutorials.', where: 'Notebook Browser → Venus Tutorials.' },
        { do: 'Save and Run All from the toolbar.', where: 'Notebook toolbar (top).' }
      ]
    },
    admin: {
      label: 'Admin',
      blurb: 'Run, configure, and secure Venus.',
      icon: 'M8 1.5l5.5 2.4v3.6c0 3.3-2.3 5.6-5.5 6.6-3.2-1-5.5-3.3-5.5-6.6V3.9z',
      steps: [
        { do: 'Start/stop/restart the server.', where: 'Shutdown button (top bar) · or venus start / stop / status.' },
        { do: 'Pick your AI provider — Claude, Copilot, or Gemini (local CLI, no API key).', where: 'Settings → AI Provider.' },
        { do: 'Choose auth: local (default) or OAuth2.', where: 'venus.auth.mode · data/oauth-config.json.' },
        { do: 'Install Maven, npm, and NuGet packages.', where: 'Packages and NuGet tabs.' },
        { do: 'Notebooks live in notebooks/ (.vnb); app data in data/ — never commit data/.', where: 'Project root.' }
      ]
    },
    developer: {
      label: 'Developer',
      blurb: 'Build, chain, and automate.',
      icon: 'M5 4L2 8l3 4M11 4l3 4-3 4M9 2L7 14',
      steps: [
        { do: 'Use built-in helpers: venus.table(), venus.display(), venus.html().', where: 'Any code cell.' },
        { do: 'Chain cells with //@ anchor and //@ depends — works across all 7 languages.', where: 'Pipeline cells · Run with Dependencies.' },
        { do: 'Install a package and use it immediately on the classpath.', where: 'Packages tab.' },
        { do: 'Drive Venus from your editor/agent over MCP.', where: 'GET /api/mcp/sse · POST /api/mcp/messages.' },
        { do: 'Extend Venus itself: run claude / copilot / gemini in the repo and ask the venus agent.', where: 'Terminal + AGENTS.md.' }
      ]
    },
    architecture: {
      label: 'Architecture',
      blurb: 'How Venus is built.',
      icon: 'M2 14V6l6-4 6 4v8M2 14h12M6 14V9h4v5',
      steps: [
        { do: 'One Spring Boot app on Java 21; the UI is static HTML/CSS/JS — no build step.', where: 'src/main/resources/static.' },
        { do: 'Layered backend: controller → service → shell/model.', where: 'src/main/java/com/venus.' },
        { do: 'One execution service per language; all return a unified ExecutionResult.', where: 'service/*ExecutionService.' },
        { do: 'Real-time output streams over STOMP /ws.', where: 'WebSocketConfig + the UI.' },
        { do: 'See the full picture and diagrams.', where: 'docs/ARCHITECTURE.md · brochure PDF.' }
      ]
    }
  };

  // ── DOM helpers ──────────────────────────────────────────────────
  function el(id) { return document.getElementById(id); }
  function overlay() { return el('welcome-overlay'); }

  function renderChips() {
    const c = el('welcome-chips');
    if (!c) return;
    c.innerHTML = HIGHLIGHTS.map(h => `<span class="welcome-chip">${h}</span>`).join('');
  }

  function renderTracks(active) {
    const wrap = el('welcome-tracks');
    if (!wrap) return;
    wrap.innerHTML = Object.keys(TRACKS).map(key => {
      const t = TRACKS[key];
      const on = key === active ? ' active' : '';
      return `<button class="welcome-track${on}" data-track="${key}" onclick="Welcome.selectTrack('${key}')">
        <svg viewBox="0 0 16 16" width="20" height="20" fill="none"><path d="${t.icon}" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
        <span class="welcome-track-label">${t.label}</span>
        <span class="welcome-track-blurb">${t.blurb}</span>
      </button>`;
    }).join('');
  }

  function renderTrack(key) {
    const t = TRACKS[key];
    const box = el('welcome-content');
    if (!t || !box) return;
    box.innerHTML = `
      <ol class="welcome-steps">
        ${t.steps.map(s => `<li><span class="ws-do">${s.do}</span><span class="ws-where">${s.where}</span></li>`).join('')}
      </ol>`;
  }

  function renderWhatsNew() {
    const box = el('welcome-content');
    if (!box) return;
    box.innerHTML = `
      <div class="welcome-whatsnew">
        <div class="wn-head">What's New in v${WHATS_NEW.version} <span class="wn-date">${WHATS_NEW.date}</span></div>
        <div class="wn-title">${WHATS_NEW.title}</div>
        <ul class="wn-list">${WHATS_NEW.items.map(i => `<li>${i}</li>`).join('')}</ul>
        <div class="wn-links">
          <a href="#" onclick="Welcome.openHistory();return false;">See all releases &rarr;</a>
          <a href="${CHANGELOG_URL}" target="_blank" rel="noopener">Full CHANGELOG &#8599;</a>
        </div>
      </div>`;
  }

  function renderHistory() {
    const box = el('welcome-content');
    if (!box) return;
    box.innerHTML = `
      <div class="welcome-history">
        <div class="wn-head">Release history</div>
        <div class="wh-list">
          ${RELEASES.map(r => `
            <div class="wh-rel">
              <div class="wh-rel-head">
                <span class="wh-ver">v${r.version}</span>
                <span class="wh-date">${r.date}</span>
              </div>
              <div class="wh-title">${r.title}</div>
              <ul class="wh-items">${r.items.map(i => `<li>${i}</li>`).join('')}</ul>
            </div>`).join('')}
        </div>
        <div class="wn-links">
          <a href="#" onclick="Welcome.openReleaseNotes();return false;">&larr; Back to What's New</a>
          <a href="${CHANGELOG_URL}" target="_blank" rel="noopener">Full CHANGELOG &#8599;</a>
        </div>
      </div>`;
  }

  // ── Public API ───────────────────────────────────────────────────
  const Welcome = {
    open(track) {
      const o = overlay();
      if (!o) return;
      const tag = el('welcome-version-tag');
      if (tag) tag.textContent = 'v' + VENUS_VERSION;
      const mute = el('welcome-dontshow');
      if (mute) mute.checked = localStorage.getItem(LS_MUTE) === '1';
      renderChips();
      const active = track && TRACKS[track] ? track : 'overview';
      renderTracks(active);
      renderTrack(active);
      o.classList.remove('hidden');
      document.addEventListener('keydown', escClose);
      this._markSeen();
    },

    selectTrack(key) {
      renderTracks(key);
      renderTrack(key);
    },

    openReleaseNotes() {
      const o = overlay();
      if (!o) return;
      const tag = el('welcome-version-tag');
      if (tag) tag.textContent = 'v' + VENUS_VERSION;
      renderChips();
      renderTracks(null);
      renderWhatsNew();
      o.classList.remove('hidden');
      document.addEventListener('keydown', escClose);
      this._markSeen();
    },

    openHistory() {
      const o = overlay();
      if (!o) return;
      renderChips();
      renderTracks(null);
      renderHistory();
      o.classList.remove('hidden');
      document.addEventListener('keydown', escClose);
      this._markSeen();
    },

    close() {
      const o = overlay();
      if (!o) return;
      const mute = el('welcome-dontshow');
      if (mute) localStorage.setItem(LS_MUTE, mute.checked ? '1' : '0');
      o.classList.add('hidden');
      document.removeEventListener('keydown', escClose);
    },

    _markSeen() {
      localStorage.setItem(LS_SEEN, '1');
      localStorage.setItem(LS_VERSION, VENUS_VERSION);
    },

    version: VENUS_VERSION
  };

  function escClose(e) { if (e.key === 'Escape') Welcome.close(); }

  // ── Auto-show logic ──────────────────────────────────────────────
  function autoShow() {
    const seen      = localStorage.getItem(LS_SEEN) === '1';
    const lastVer   = localStorage.getItem(LS_VERSION);
    const muted     = localStorage.getItem(LS_MUTE) === '1';

    if (!seen) {
      // First-time user → full welcome (Overview track).
      Welcome.open('overview');
      return;
    }
    if (lastVer !== VENUS_VERSION) {
      // Returning user who just updated from the repo → What's New.
      Welcome.openReleaseNotes();
      return;
    }
    // Already up to date and seen — respect the mute preference (no-op either way).
    void muted;
  }

  window.Welcome = Welcome;
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => setTimeout(autoShow, 600));
  } else {
    setTimeout(autoShow, 600);
  }
})();
