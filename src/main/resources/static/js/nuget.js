/**
 * NuGet package manager UI for Venus Notebooks.
 * Manages NuGet packages for C# and F# cells.
 *
 * Talks to:
 *   GET    /api/nuget          - list installed packages
 *   POST   /api/nuget/install  - install { packageId, version }
 *   DELETE /api/nuget/:id      - remove a package
 *   GET    /api/settings/status - dotnetAvailable / dotnetScriptAvailable
 */
const NuGetUI = (() => {
    let loaded = false;

    async function load() {
        await checkDotNetStatus();
        await renderList();
        setupListeners();
        loaded = true;
    }

    async function checkDotNetStatus() {
        const box = document.getElementById('nuget-dotnet-status');
        if (!box) return;
        try {
            const r = await fetch('/api/settings/status');
            const s = await r.json();
            const dotnet = s.dotnetAvailable;
            if (!dotnet) {
                box.style.display = '';
                box.innerHTML = '<strong>⚠ .NET SDK not found.</strong> Install from <a href="https://dot.net" target="_blank">dot.net</a> and restart Venus to use C# and F# cells.';
            } else {
                box.style.display = 'none';
            }
        } catch (e) {
            // status check is best-effort; don't block the UI
        }
    }

    async function renderList() {
        const container = document.getElementById('nuget-list');
        if (!container) return;
        try {
            const r = await fetch('/api/nuget');
            const pkgs = await r.json();
            if (!pkgs.length) {
                container.innerHTML = '<p style="color:var(--text-3);font-size:13px">No NuGet packages installed. Install one above to make it available in all C# and F# cells.</p>';
                return;
            }
            container.innerHTML = pkgs.map(p => `
                <div class="pkg-item" id="nuget-item-${CSS.escape(p.packageId)}">
                  <div class="pkg-item-info">
                    <span class="pkg-item-name">${escHtml(p.packageId)}</span>
                    <span class="pkg-item-version">${escHtml(p.version)}</span>
                  </div>
                  <button class="btn-icon btn-danger" title="Remove" onclick="NuGetUI.remove('${escAttr(p.packageId)}')">
                    <svg viewBox="0 0 16 16" fill="none" width="14" height="14"><path d="M3 4h10M6 4V2h4v2M5 4v9h6V4H5z" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
                  </button>
                </div>
            `).join('');
        } catch (e) {
            container.innerHTML = '<p style="color:var(--error);font-size:13px">Failed to load packages.</p>';
        }
    }

    function setupListeners() {
        const btn = document.getElementById('btn-nuget-install');
        if (!btn || btn.dataset.nugetBound) return;
        btn.dataset.nugetBound = '1';
        btn.addEventListener('click', installPackage);
        document.getElementById('nuget-version')
            .addEventListener('keydown', e => { if (e.key === 'Enter') installPackage(); });
    }

    async function installPackage() {
        const idEl = document.getElementById('nuget-id');
        const verEl = document.getElementById('nuget-version');
        const statusEl = document.getElementById('nuget-status');
        const packageId = idEl.value.trim();
        const version = verEl.value.trim();

        if (!packageId) { showStatus(statusEl, 'Enter a Package ID.', 'error'); return; }
        if (!version)   { showStatus(statusEl, 'Enter a version (e.g. 13.0.3).', 'error'); return; }

        showStatus(statusEl, `Installing ${packageId} ${version}…`, 'info');
        document.getElementById('btn-nuget-install').disabled = true;

        try {
            const r = await fetch('/api/nuget/install', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ packageId, version })
            });
            if (!r.ok) {
                const err = await r.json().catch(() => ({}));
                showStatus(statusEl, err.error || 'Install failed.', 'error');
            } else {
                showStatus(statusEl, `${packageId} ${version} installed. It will be loaded in your next C# / F# cell execution.`, 'success');
                idEl.value = '';
                verEl.value = '';
                await renderList();
            }
        } catch (e) {
            showStatus(statusEl, 'Network error: ' + e.message, 'error');
        } finally {
            document.getElementById('btn-nuget-install').disabled = false;
        }
    }

    async function remove(packageId) {
        if (!confirm(`Remove NuGet package "${packageId}"?\n\n⚠ Warning: any C# or F# cell that references this package will fail to compile until it is re-installed.`)) return;
        try {
            const r = await fetch(`/api/nuget/${encodeURIComponent(packageId)}`, { method: 'DELETE' });
            if (r.ok) {
                await renderList();
            } else {
                alert('Failed to remove package.');
            }
        } catch (e) {
            alert('Error: ' + e.message);
        }
    }

    function fillPopular(id, version) {
        document.getElementById('nuget-id').value = id;
        document.getElementById('nuget-version').value = version;
        document.getElementById('nuget-id').focus();
    }

    function showStatus(el, msg, type) {
        el.textContent = msg;
        el.className = 'pkg-status';
        el.classList.remove('hidden');
        if (type === 'error')   el.style.color = 'var(--error, #f87171)';
        else if (type === 'success') el.style.color = 'var(--success, #4ade80)';
        else el.style.color = 'var(--text-2)';
    }

    function escHtml(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    }
    function escAttr(s) {
        return String(s).replace(/'/g, "\\'");
    }

    return { load, remove, fillPopular };
})();
