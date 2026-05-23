/**
 * Venus Notebooks — Core App
 * Tab navigation · WebSocket · REST helpers · global state
 */
const Venus = (() => {
  const state = {
    stompClient: null,
    connected:   false,
    currentNotebookId:  null,
    currentSessionId:   null,
    settings: {},
  };

  /* ── Tab navigation ─────────────────────────────── */
  function initTabs() {
    const btns   = document.querySelectorAll('.tab-btn');
    const panels = document.querySelectorAll('.tab-panel');
    const nbToolbar = document.getElementById('nb-toolbar');

    btns.forEach(btn => {
      btn.addEventListener('click', () => {
        btns.forEach(b => b.classList.remove('active'));
        panels.forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        const tab = btn.dataset.tab;
        document.getElementById(`panel-${tab}`)?.classList.add('active');
        // Only show notebook toolbar on notebook tab
        nbToolbar.toggleAttribute('data-hidden', tab !== 'notebook');
      });
    });
  }

  /* ── Right-edge slide-out overlays (AI panel + Variable Inspector) ──
     Both panels share a single backdrop. The FAB launches the AI panel
     and is itself draggable. Opening one panel closes the other so they
     never overlap. Click on the backdrop, press Esc, or click the
     panel's × button to dismiss. */
  function initAiToggle() {
    const aiSidebar = document.getElementById('ai-sidebar');
    const inspector = document.getElementById('var-inspector');
    const fab       = document.getElementById('ai-fab');
    const backdrop  = document.getElementById('ai-backdrop');
    if (!aiSidebar || !fab || !backdrop) return;

    // ── Refresh the shared backdrop + FAB + var-tab position ──────────
    // The vertical "Variables" tab stays parked just left of the AI panel
    // whenever AI is open — we expose its current width as the CSS var
    // --ai-w-current and the tab's `right:` rule reads it.
    function refreshOverlayUI() {
      const aiOpen = !aiSidebar.classList.contains('hidden');
      const inspectorOpen = inspector && !inspector.classList.contains('hidden');
      backdrop.classList.toggle('visible', aiOpen || inspectorOpen);
      fab.classList.toggle('hidden', aiOpen);
      const aiWidth = aiOpen ? aiSidebar.offsetWidth : 0;
      document.documentElement.style.setProperty('--ai-w-current', aiWidth + 'px');
    }

    function setAiOpen(open) {
      // Opening AI displaces the inspector drawer — only one drawer at a
      // time — but the var-tab stays visible (shifted left of the AI panel).
      if (open && inspector) inspector.classList.add('hidden');
      aiSidebar.classList.toggle('hidden', !open);
      refreshOverlayUI();
    }

    function setInspectorOpen(open) {
      if (!inspector) return;
      if (open) aiSidebar.classList.add('hidden');
      inspector.classList.toggle('hidden', !open);
      refreshOverlayUI();
    }

    // Keep the tab offset accurate while the user resizes the AI panel
    // via its drag handle. ResizeObserver fires on every animation frame.
    if (typeof ResizeObserver !== 'undefined') {
      new ResizeObserver(refreshOverlayUI).observe(aiSidebar);
    }
    window.addEventListener('resize', refreshOverlayUI);

    fab.addEventListener('click', () => {
      // Suppress click that immediately follows a drag
      if (fab.dataset.justDragged === '1') {
        fab.dataset.justDragged = '0';
        return;
      }
      setAiOpen(true);
    });

    backdrop.addEventListener('click', () => {
      setAiOpen(false);
      setInspectorOpen(false);
    });

    document.getElementById('btn-ai-close')?.addEventListener('click', () => setAiOpen(false));
    document.getElementById('btn-vi-close')?.addEventListener('click', () => setInspectorOpen(false));

    document.addEventListener('keydown', e => {
      if (e.key !== 'Escape') return;
      if (!aiSidebar.classList.contains('hidden')) { setAiOpen(false); return; }
      if (inspector && !inspector.classList.contains('hidden')) { setInspectorOpen(false); return; }
    });

    // Public API — other modules drive the overlays through these.
    Venus.openAi          = () => setAiOpen(true);
    Venus.closeAi         = () => setAiOpen(false);
    Venus.openInspector   = () => setInspectorOpen(true);
    Venus.closeInspector  = () => setInspectorOpen(false);

    initFabDrag(fab);
  }

  /** Make the AI FAB draggable. Position is persisted to localStorage. */
  function initFabDrag(fab) {
    // Restore saved position
    try {
      const saved = JSON.parse(localStorage.getItem('ai-fab-pos') || 'null');
      if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) {
        applyFabPos(fab, saved.x, saved.y);
      }
    } catch { /* ignore corrupt saved value */ }

    let dragging = false;
    let moved    = false;
    let startX = 0, startY = 0;
    let offsetX = 0, offsetY = 0;
    const DRAG_THRESHOLD = 4;  // px of motion before we count it as a drag

    fab.addEventListener('mousedown', (e) => {
      if (e.button !== 0) return;
      const rect = fab.getBoundingClientRect();
      offsetX = e.clientX - rect.left;
      offsetY = e.clientY - rect.top;
      startX  = e.clientX;
      startY  = e.clientY;
      dragging = true;
      moved    = false;
      e.preventDefault();
    });

    document.addEventListener('mousemove', (e) => {
      if (!dragging) return;
      const dx = e.clientX - startX, dy = e.clientY - startY;
      if (!moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
      if (!moved) { moved = true; fab.classList.add('dragging'); }
      const x = e.clientX - offsetX;
      const y = e.clientY - offsetY;
      applyFabPos(fab, x, y);
    });

    document.addEventListener('mouseup', () => {
      if (!dragging) return;
      dragging = false;
      if (moved) {
        fab.classList.remove('dragging');
        fab.dataset.justDragged = '1';   // suppress the trailing click
        // Persist position
        const rect = fab.getBoundingClientRect();
        localStorage.setItem('ai-fab-pos', JSON.stringify({ x: rect.left, y: rect.top }));
      }
    });
  }

  function applyFabPos(fab, x, y) {
    // Clamp within viewport so the FAB never escapes off-screen on resize
    const w = fab.offsetWidth  || 58;
    const h = fab.offsetHeight || 58;
    const maxX = window.innerWidth  - w - 4;
    const maxY = window.innerHeight - h - 4;
    const cx = Math.max(4, Math.min(maxX, x));
    const cy = Math.max(56, Math.min(maxY, y));  // keep below topbar
    fab.style.left   = cx + 'px';
    fab.style.top    = cy + 'px';
    fab.style.right  = 'auto';
    fab.style.bottom = 'auto';
  }

  /* ── WebSocket (STOMP / SockJS) ─────────────────── */
  function initWebSocket() {
    const client = new StompJs.Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        state.connected = true;
        state.stompClient = client;
        setWsStatus('connected');
        // Notify lifecycle module — covers reconnect after restart
        window.ServerLifecycle?.onWsConnect?.();
      },
      onDisconnect: () => {
        state.connected = false;
        setWsStatus('disconnected');
        // Notify lifecycle module — covers Ctrl+C, stop script, or UI-triggered shutdown
        window.ServerLifecycle?.onWsDisconnect?.();
      },
      onStompError: () => {
        state.connected = false;
        setWsStatus('disconnected');
        window.ServerLifecycle?.onWsDisconnect?.();
      },
    });
    client.activate();
    state.stompClient = client;
    setWsStatus('connecting');
  }

  function setWsStatus(s) {
    const dot   = document.getElementById('ws-dot');
    const label = document.getElementById('ws-label');
    if (!dot) return;
    dot.className = 'ws-dot ' + s;
    label.textContent = s;
  }

  /* ── REST helpers ───────────────────────────────── */
  async function api(method, path, body) {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body !== undefined) opts.body = JSON.stringify(body);
    const res = await fetch('/api' + path, opts);
    const text = await res.text();
    if (!res.ok) {
      const msg = (() => { try { return JSON.parse(text).error || text; } catch { return text; } })();
      throw new Error(msg || `HTTP ${res.status}`);
    }
    return text ? JSON.parse(text) : null;
  }

  /* ── Status bar ─────────────────────────────────── */
  function setStatus(msg, level) {
    const el = document.getElementById('sb-msg');
    if (el) el.textContent = msg;
  }

  function initClock() {
    const el = document.getElementById('sb-time');
    if (!el) return;
    const tick = () => { el.textContent = new Date().toLocaleTimeString(); };
    tick(); setInterval(tick, 1000);
  }

  function markDirty(dirty) {
    document.getElementById('dirty-dot')?.classList.toggle('visible', dirty);
  }

  /* ── Load initial settings ──────────────────────── */
  async function loadSettings() {
    try {
      state.settings = await api('GET', '/settings');
      const badge = document.getElementById('ai-model-badge');
      if (badge) badge.textContent = state.settings.claudeModel || 'claude-sonnet-4-6';
      // Apply theme
      if (state.settings.theme === 'light') {
        document.documentElement.setAttribute('data-theme', 'light');
      }
    } catch (e) {
      console.warn('[Venus] Could not load settings:', e.message);
    }
  }

  /* ── Keyboard shortcuts ─────────────────────────── */
  function initShortcuts() {
    document.addEventListener('keydown', e => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        document.getElementById('btn-save')?.click();
      }
      if ((e.ctrlKey || e.metaKey) && e.key === '\\') {
        e.preventDefault();
        const open = !document.getElementById('ai-sidebar')?.classList.contains('hidden');
        open ? Venus.closeAi?.() : Venus.openAi?.();
      }
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'V') {
        e.preventDefault();
        document.getElementById('btn-paste-cell')?.click();
      }
    });
  }

  /* ── Splash screen ──────────────────────────────── */
  function initSplash() {
    // Hard fallback — splash will ALWAYS disappear after 4 seconds no matter what
    setTimeout(hideSplash, 4000);

    // Scatter random stars across the starfield
    const container = document.getElementById('splash-stars');
    if (!container) return;
    for (let i = 0; i < 90; i++) {
      const s = document.createElement('div');
      const big = Math.random() < 0.12;
      s.className = 'splash-star';
      s.style.cssText = [
        `left:${(Math.random()*100).toFixed(1)}%`,
        `top:${(Math.random()*100).toFixed(1)}%`,
        `width:${big ? 2 : 1}px`,
        `height:${big ? 2 : 1}px`,
        `--delay:${(Math.random()*4).toFixed(2)}s`,
        `--dur:${(1.4 + Math.random()*2.2).toFixed(2)}s`,
        `--lo:${(0.05 + Math.random()*0.15).toFixed(2)}`,
        `--hi:${(0.5  + Math.random()*0.5).toFixed(2)}`,
      ].join(';');
      container.appendChild(s);
    }
  }

  function setSplashMsg(text) {
    const el = document.getElementById('splash-msg');
    if (el) el.textContent = text;
  }

  function hideSplash() {
    const splash = document.getElementById('venus-splash');
    if (!splash || splash.style.display === 'none') return;
    splash.style.transition = 'opacity 0.6s ease';
    splash.style.opacity = '0';
    splash.style.pointerEvents = 'none';
    setTimeout(() => { splash.style.display = 'none'; }, 650);
  }

  function showShutdownSplash() {
    const splash = document.getElementById('venus-splash');
    if (!splash) return;
    // Ensure splash is visible and styled as shutdown
    splash.style.display = '';
    splash.style.opacity = '1';
    splash.style.pointerEvents = '';
    splash.classList.remove('splash-hidden', 'splash-hiding');
    splash.classList.add('splash-shutdown');
    setSplashMsg('Shutting down…');
    // Drain the progress bar
    setTimeout(() => {
      const bar = splash.querySelector('.splash-loader-bar');
      if (bar) bar.classList.add('draining');
    }, 100);
    // After server stops, show "Venus is Off" state with restart instructions
    setTimeout(() => {
      setSplashMsg('Venus is Off');
      const brand = splash.querySelector('.splash-brand');
      if (!brand || brand.querySelector('.splash-shutdown-info')) return;

      // Restart instructions block
      const info = document.createElement('div');
      info.className = 'splash-shutdown-info';
      info.innerHTML = `
        <p class="shutdown-subtitle">The server has stopped gracefully.</p>
        <div class="shutdown-restart-box">
          <span class="shutdown-restart-label">To restart Venus Notebooks:</span>
          <code class="shutdown-cmd">scripts\\start.bat</code>
          <span class="shutdown-restart-or">or from project root:</span>
          <code class="shutdown-cmd">mvn spring-boot:run</code>
        </div>`;
      brand.appendChild(info);

      // Reconnect button that polls the server
      const btn = document.createElement('button');
      btn.className = 'splash-reconnect';
      btn.innerHTML = '<span class="reconnect-dot"></span> Reconnect';
      brand.appendChild(btn);

      let polling = false;
      btn.onclick = () => {
        if (polling) return;
        polling = true;
        btn.innerHTML = '<span class="reconnect-dot polling"></span> Waiting for server…';
        btn.style.opacity = '0.7';
        btn.style.cursor = 'default';
        const timer = setInterval(async () => {
          try {
            const r = await fetch('/api/settings/status', { cache: 'no-store' });
            if (r.ok) { clearInterval(timer); location.reload(); }
          } catch { /* server not up yet */ }
        }, 2500);
      };
    }, 2000);
  }

  /* ── Shutdown ───────────────────────────────────── */
  function initDocs() {
    document.getElementById('btn-docs')?.addEventListener('click', () => {
      DocsPanel?.show('usage');
    });
  }

  function initShutdown() {
    document.getElementById('btn-shutdown')?.addEventListener('click', async () => {
      if (!confirm('Shut down Venus Notebooks?\n\nThe server will stop and this page will go offline.')) return;

      const btn = document.getElementById('btn-shutdown');
      btn.disabled = true;
      setStatus('Shutting down…');

      showShutdownSplash();

      try {
        await api('POST', '/settings/shutdown');
      } catch { /* connection drop after shutdown is expected */ }
    });
  }

  /* ── Boot ───────────────────────────────────────── */
  async function init() {
    initSplash();
    setSplashMsg('Starting up…');

    initTabs();
    initAiToggle();
    initWebSocket();
    initClock();
    initShortcuts();
    initShutdown();
    initDocs();

    try {
      setSplashMsg('Loading settings…');
      await loadSettings();

      setSplashMsg('Signing in…');
      await UserAuth.init();

      setSplashMsg('Fetching server info…');
      try {
        const status = await api('GET', '/settings/status');
        const el = document.getElementById('sb-java');
        if (el) el.textContent = `Java ${status.javaVersion}`;
      } catch { /* ignore */ }

      setSplashMsg('Ready ✦');
    } catch (e) {
      setSplashMsg('Error — retrying…');
      console.error('[Venus] Init error:', e);
    } finally {
      // Always hide the splash — even if something above threw
      setTimeout(hideSplash, 700);
    }
  }

  /* ── WebSocket session subscription ─────────── */
  // Returns an unsubscribe function.  Call it after execution completes.
  function subscribeToSession(sessionId, callback) {
    if (state.stompClient && state.connected) {
      const sub = state.stompClient.subscribe(`/topic/shell/${sessionId}`, (msg) => {
        try { callback(JSON.parse(msg.body)); } catch(e) { /* ignore */ }
      });
      return () => { try { sub.unsubscribe(); } catch { /* ignore */ } };
    }
    // WS not ready yet — poll until connected
    let sub = null;
    const waitForWs = setInterval(() => {
      if (state.connected && state.stompClient) {
        clearInterval(waitForWs);
        sub = state.stompClient.subscribe(`/topic/shell/${sessionId}`, (msg) => {
          try { callback(JSON.parse(msg.body)); } catch(e) { /* ignore */ }
        });
      }
    }, 100);
    return () => {
      clearInterval(waitForWs);
      try { sub?.unsubscribe(); } catch { /* ignore */ }
    };
  }

  /* ── Send a message to a shell session via WebSocket ── */
  function sendToShell(sessionId, destination, payload) {
    if (!state.stompClient || !state.connected) return;
    state.stompClient.publish({
      destination: `/app/shell/${sessionId}/${destination}`,
      body: JSON.stringify(payload),
    });
  }

  return { init, state, api, setStatus, markDirty, subscribeToSession, sendToShell };
})();

// Run init — works whether DOM is already ready or still loading
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => Venus.init());
} else {
  Venus.init();
}

/* ── Variable Inspector ────────────────────────────────────────────
   Two pieces:
     1. `#var-tab` — a slim vertical pill on the right edge that appears
        after a cell runs. It carries the cell's anchor/id as a label
        and acts as the entry point to (re-)open the inspector.
     2. `#var-inspector` — the slide-out drawer with the local/global
        variable tables.
   The tab persists across opens of the AI panel; its right-offset is
   synced (via the --ai-w-current CSS var) so it stays just left of the
   AI panel and never overlaps. The cell tag in the drawer header is a
   button that scrolls to + flashes the cell it belongs to. */
const VarInspector = (() => {
  // Latest payload — kept so the tab can re-open the drawer with the
  // same data even after the user dismisses and re-opens it.
  let _payload = null;

  function escapeHtml(s) {
    return String(s ?? '')
      .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }

  function _renderTable(tableEl, rows) {
    if (!tableEl) return;
    if (!rows || rows.length === 0) {
      tableEl.innerHTML = `<tr><td colspan="3" class="vi-empty-cell" style="text-align:center;color:var(--text-3);padding:14px">—</td></tr>`;
      return;
    }
    const head = `<tr><th>Name</th><th>Type</th><th>Value</th></tr>`;
    const body = rows.map(v => {
      const value = v.value;
      const valClass = value === 'null' ? 'vi-value null'
                     : value === '<unavailable>' ? 'vi-value unavailable'
                     : 'vi-value';
      return `<tr>
        <td class="vi-name">${escapeHtml(v.name)}</td>
        <td class="vi-type">${escapeHtml(v.type)}</td>
        <td><div class="${valClass}">${escapeHtml(value)}</div></td>
      </tr>`;
    }).join('');
    tableEl.innerHTML = head + body;
  }

  function _renderPanel() {
    if (!_payload) return;
    const locals  = _payload.locals  || [];
    const globals = _payload.globals || [];
    const label   = _payload.cellAnchor ? '#' + _payload.cellAnchor : (_payload.cellId || '');

    const tag = document.getElementById('vi-cell-tag');
    if (tag) {
      tag.textContent = label;
      tag.title = label
        ? 'Scroll to & focus cell ' + label
        : '';
    }

    document.getElementById('vi-local-count').textContent  = locals.length;
    document.getElementById('vi-global-count').textContent = globals.length;
    _renderTable(document.getElementById('vi-local-table'),  locals);
    _renderTable(document.getElementById('vi-global-table'), globals);

    const empty = locals.length === 0 && globals.length === 0;
    document.getElementById('vi-empty')?.classList.toggle('hidden', !empty);
    document.getElementById('vi-local-section') ?.classList.toggle('empty', empty);
    document.getElementById('vi-global-section')?.classList.toggle('empty', empty);
  }

  function _renderTab() {
    if (!_payload) return;
    const label = _payload.cellAnchor ? '#' + _payload.cellAnchor : (_payload.cellId || '');
    const tabCellLabel = document.getElementById('vt-cell-label');
    if (tabCellLabel) tabCellLabel.textContent = label;
    const tab = document.getElementById('var-tab');
    if (tab) tab.classList.remove('hidden');
  }

  /** Push fresh variable data + show the vertical tab. Does NOT open the drawer. */
  function update(payload) {
    _payload = payload;
    _renderPanel();
    _renderTab();
  }

  /** Open the drawer with the current data. */
  function open() {
    _renderPanel();
    Venus.openInspector?.();
  }

  /** Hide both the drawer and the tab. */
  function dismissTab() {
    document.getElementById('var-tab')?.classList.add('hidden');
    Venus.closeInspector?.();
  }

  // ── Wire DOM hooks once the page is ready ─────────────────────────
  function init() {
    const tab = document.getElementById('var-tab');
    if (tab) {
      tab.addEventListener('click', (e) => {
        // Don't open when the user clicks the × inside the tab
        if (e.target.closest('#vt-close')) return;
        open();
      });
    }
    document.getElementById('vt-close')?.addEventListener('click', (e) => {
      e.stopPropagation();
      dismissTab();
    });

    // Cell-tag in the drawer header → jump to + focus the cell
    document.getElementById('vi-cell-tag')?.addEventListener('click', () => {
      if (!_payload?.cellId) return;
      const editor = window.NotebookEditor;
      if (editor?.focusCell) editor.focusCell(_payload.cellId);
      // Keep the drawer open so the user can keep cross-referencing
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else { init(); }

  return { update, open, dismissTab };
})();
window.VarInspector = VarInspector;

/* ── Error Log ─────────────────────────────────────────────────────── */
const ErrorLog = (() => {
  const entries = [];

  function add(source, message, detail) {
    const ts = new Date().toLocaleTimeString();
    entries.push({ ts, source, message, detail });
    _render();
    // Show button with count
    const btn = document.getElementById('sb-err-btn');
    if (btn) { btn.style.display = ''; document.getElementById('sb-err-count').textContent = entries.length; }
  }

  function clear() {
    entries.length = 0;
    _render();
    const btn = document.getElementById('sb-err-btn');
    if (btn) btn.style.display = 'none';
  }

  function toggle() {
    const panel = document.getElementById('error-log-panel');
    if (!panel) return;
    panel.style.display = panel.style.display === 'none' ? '' : 'none';
  }

  function _render() {
    const body  = document.getElementById('elp-body');
    const count = document.getElementById('elp-count');
    if (!body) return;
    if (count) count.textContent = entries.length + (entries.length === 1 ? ' error' : ' errors');
    body.innerHTML = entries.slice().reverse().map(e => `
      <div class="elp-entry">
        <div class="elp-entry-header">
          <span class="elp-ts">${e.ts}</span>
          <span class="elp-source">${e.source || ''}</span>
        </div>
        <div class="elp-msg">${e.message || ''}</div>
        ${e.detail ? `<pre class="elp-detail">${e.detail}</pre>` : ''}
      </div>`).join('');
  }

  return { add, clear, toggle };
})();

/* ── UserAuth ──────────────────────────────────────────────────────── */
const UserAuth = (() => {
  let _user = null;   // { id, name, firstName, email, avatarUrl, authProvider }
  let _authMode = 'local';
  let _menuOpen = false;

  /* Called once during boot — resolves identity and updates the UI */
  async function init() {
    try {
      const data = await Venus.api('GET', '/user/me');
      _authMode = data.authMode || 'local';

      if (data.authenticated) {
        _user = { id: data.id, name: data.name, firstName: data.firstName,
                  email: data.email, avatarUrl: data.avatarUrl,
                  authProvider: (data.authProvider || 'LOCAL').toUpperCase() };
        _renderWidget();
        // Prompt for email if not set (non-blocking, deferred)
        if (!_user.email) setTimeout(_maybePromptEmail, 3000);
      } else if (_authMode === 'oauth') {
        // Show login modal — user must authenticate
        showLogin();
      }
      // local + not authenticated should not happen, but handle gracefully
    } catch (e) {
      console.warn('[UserAuth] Could not load user:', e.message);
    }
  }

  function _renderWidget() {
    if (!_user) return;
    const isLocal = _user.authProvider === 'LOCAL';

    // Avatar: initials circle or provider photo
    const avatarEl = document.getElementById('user-avatar');
    if (_user.avatarUrl) {
      avatarEl.innerHTML = `<img src="${_user.avatarUrl}" alt="${_user.name}" class="user-avatar-img">`;
    } else {
      const initials = (_user.name || '?').split(' ').map(w => w[0]).slice(0,2).join('').toUpperCase();
      avatarEl.textContent = initials;
    }

    // Name + provider badge
    document.getElementById('user-name').textContent = _user.firstName || _user.name || 'User';
    const provEl = document.getElementById('user-provider');
    provEl.textContent = isLocal ? 'local' : _user.authProvider.toLowerCase();
    provEl.className = 'user-provider prov-' + (isLocal ? 'local' : _user.authProvider.toLowerCase());

    // Dropdown content
    document.getElementById('udd-greeting').textContent = greeting(_user.firstName);
    document.getElementById('udd-email').textContent = _user.email || 'No email set';
    // OAuth users get Logout + Switch Account; local users get neither (no session to clear)
    document.getElementById('udd-logout-btn').style.display  = isLocal ? 'none' : '';
    document.getElementById('udd-switch-btn').style.display  = isLocal ? 'none' : '';
    document.getElementById('udd-login-btn').style.display   = 'none';
  }

  function greeting(name) {
    const h = new Date().getHours();
    const tod = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
    return `${tod}, ${name || 'there'}!`;
  }

  /* Toggle the dropdown menu */
  function toggleMenu() {
    const dd = document.getElementById('user-dropdown');
    if (!dd) return;
    _menuOpen = !_menuOpen;
    dd.style.display = _menuOpen ? '' : 'none';
    if (_menuOpen) {
      // Close when clicking outside
      setTimeout(() => document.addEventListener('click', _closeMenuOnOutside, { once: true }), 50);
    }
  }

  function _closeMenuOnOutside(e) {
    const widget = document.getElementById('user-widget');
    if (widget && !widget.contains(e.target)) {
      _menuOpen = false;
      const dd = document.getElementById('user-dropdown');
      if (dd) dd.style.display = 'none';
    }
  }

  /* Show the OAuth login modal */
  async function showLogin() {
    const modal = document.getElementById('login-modal');
    if (!modal) return;
    await renderProviders();
    modal.style.display = 'flex';
  }

  function hideLogin() {
    const modal = document.getElementById('login-modal');
    if (modal) modal.style.display = 'none';
  }

  /* Render available OAuth provider buttons from server config */
  async function renderProviders() {
    const container = document.getElementById('login-providers');
    if (!container) return;
    try {
      const cfg = await Venus.api('GET', '/user/oauth-config');
      const providers = [
        { id: 'google',    label: 'Continue with Google',    configured: cfg.googleConfigured,
          icon: `<svg viewBox="0 0 24 24" width="20" height="20"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>` },
        { id: 'microsoft', label: 'Continue with Microsoft', configured: cfg.microsoftConfigured,
          icon: `<svg viewBox="0 0 24 24" width="20" height="20"><path d="M11.4 11.4H0V0h11.4v11.4z" fill="#F35325"/><path d="M24 11.4H12.6V0H24v11.4z" fill="#81BC06"/><path d="M11.4 24H0V12.6h11.4V24z" fill="#05A6F0"/><path d="M24 24H12.6V12.6H24V24z" fill="#FFBA08"/></svg>` },
        { id: 'facebook',  label: 'Continue with Facebook',  configured: cfg.facebookConfigured,
          icon: `<svg viewBox="0 0 24 24" width="20" height="20"><path d="M24 12.073C24 5.405 18.627 0 12 0S0 5.405 0 12.073c0 6.023 4.388 11.017 10.125 11.927V15.563H7.078v-3.49h3.047V9.43c0-3.016 1.792-4.681 4.533-4.681 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.927-1.956 1.874v2.25h3.328l-.532 3.49h-2.796v8.437C19.612 23.09 24 18.096 24 12.073z" fill="#1877F2"/></svg>` },
      ];

      container.innerHTML = providers.map(p => {
        if (p.configured) {
          return `<a class="login-provider-btn" href="/oauth2/authorization/${p.id}">
            ${p.icon}<span>${p.label}</span>
          </a>`;
        } else {
          return `<button class="login-provider-btn disabled" title="Configure credentials in Settings → Authentication" disabled>
            ${p.icon}<span>${p.label}</span>
            <span class="login-not-configured">Not configured</span>
          </button>`;
        }
      }).join('');
    } catch (e) {
      container.innerHTML = `<p class="login-loading" style="color:var(--red)">Could not load providers</p>`;
    }
  }

  /* User chose to continue without logging in (local fallback in oauth mode) */
  async function continueLocal() {
    hideLogin();
    // Reload user info which will now use anonymous local fallback
    await init();
  }

  /* Show the email capture prompt */
  function showEmailPrompt() {
    _menuOpen = false;
    const dd = document.getElementById('user-dropdown');
    if (dd) dd.style.display = 'none';
    const modal = document.getElementById('email-prompt');
    if (modal) {
      const inp = document.getElementById('email-input');
      if (inp && _user?.email) inp.value = _user.email;
      modal.style.display = 'flex';
    }
  }

  function hideEmailPrompt() {
    const modal = document.getElementById('email-prompt');
    if (modal) modal.style.display = 'none';
  }

  async function saveEmail() {
    const inp = document.getElementById('email-input');
    const email = inp?.value?.trim();
    if (!email) return;
    try {
      const resp = await Venus.api('PUT', '/user/me/email', { email });
      if (_user) _user.email = resp.email;
      document.getElementById('udd-email').textContent = resp.email;
      hideEmailPrompt();
      Venus.setStatus('Email saved ✓');
    } catch (e) {
      Venus.setStatus('Could not save email: ' + e.message);
    }
  }

  async function logout() {
    try { await Venus.api('POST', '/user/logout'); } catch { /* ignore */ }
    _user = null;
    // In oauth mode: show login modal so user can pick their account again
    // In local mode: there's no session to clear so just reload for safety
    if (_authMode === 'oauth') {
      _resetWidget();
      await showLogin();
    } else {
      window.location.reload();
    }
  }

  /** Sign out then immediately show the provider selection modal */
  async function switchAccount() {
    _menuOpen = false;
    const dd = document.getElementById('user-dropdown');
    if (dd) dd.style.display = 'none';
    try { await Venus.api('POST', '/user/logout'); } catch { /* ignore */ }
    _user = null;
    _resetWidget();
    await showLogin();
  }

  function _resetWidget() {
    const avatarEl = document.getElementById('user-avatar');
    if (avatarEl) avatarEl.textContent = '?';
    const nameEl = document.getElementById('user-name');
    if (nameEl) nameEl.textContent = '—';
    const provEl = document.getElementById('user-provider');
    if (provEl) { provEl.textContent = ''; provEl.className = 'user-provider'; }
  }

  function _maybePromptEmail() {
    // Only prompt in oauth mode when email is still missing
    if (_authMode === 'oauth' && _user && !_user.email) showEmailPrompt();
  }

  /* Expose current user to other modules */
  function getUser() { return _user; }

  return { init, getUser, showLogin, hideLogin, toggleMenu, showEmailPrompt, hideEmailPrompt, saveEmail, logout, continueLocal };
})();
