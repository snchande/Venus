/**
 * Arima Notebooks - npm Package Manager
 * Install and manage npm packages for JavaScript cells.
 */

const NpmPackageManager = (() => {
    function init() {
        loadInstalledPackages();
        checkNodeStatus();
        bindButtons();
    }

    async function checkNodeStatus() {
        const statusEl = document.getElementById('npm-node-status');
        if (!statusEl) return;
        try {
            const data = await fetch('/api/npm/status').then(r => r.json());
            if (data.available) {
                statusEl.innerHTML = `<span class="npm-status-ok">✓ Node.js ${data.version} detected</span>`;
            } else {
                statusEl.innerHTML = `<span class="npm-status-warn">⚠ Node.js not found — install from <a href="https://nodejs.org" target="_blank">nodejs.org</a></span>`;
            }
        } catch (e) {
            statusEl.innerHTML = `<span class="npm-status-warn">Could not check Node.js status</span>`;
        }
    }

    async function loadInstalledPackages() {
        const container = document.getElementById('npm-pkg-list');
        const countBadge = document.getElementById('npm-pkg-count');
        if (!container) return;
        try {
            const packages = await Arima.api('GET', '/npm/packages');
            if (countBadge) countBadge.textContent = packages.length;
            renderPackageList(packages);
        } catch (e) {
            container.innerHTML = `<div class="text-error">Failed to load packages: ${e.message}</div>`;
        }
    }

    function renderPackageList(packages) {
        const container = document.getElementById('npm-pkg-list');
        if (!container) return;
        if (!packages || packages.length === 0) {
            container.innerHTML = '<div class="muted">No npm packages installed yet.</div>';
            return;
        }
        container.innerHTML = packages.map(pkg => `
            <div class="pkg-item" id="npm-pkg-item-${escapeHtml(pkg.name)}">
                <div class="pkg-item-info">
                    <div class="pkg-item-name">${escapeHtml(pkg.name)}
                        <span class="pkg-version">${escapeHtml(pkg.version)}</span>
                    </div>
                    <div class="pkg-item-coord">npm install ${escapeHtml(pkg.name)}</div>
                </div>
                <button class="btn-danger-sm"
                    onclick="NpmPackageManager.removePackage('${escapeHtml(pkg.name)}')">
                    Remove
                </button>
            </div>
        `).join('');
    }

    function setInstallStatus(msg, type) {
        const el = document.getElementById('npm-pkg-status');
        if (!el) return;
        el.textContent = msg;
        el.className = `pkg-status pkg-status-${type}`;
        el.classList.remove('hidden');
    }

    function bindButtons() {
        const installBtn  = document.getElementById('btn-npm-install');
        const nameInput   = document.getElementById('npm-pkg-name');
        const searchBtn   = document.getElementById('btn-npm-search');
        const searchInput = document.getElementById('npm-pkg-search');

        installBtn?.addEventListener('click', () => installPackage());
        nameInput?.addEventListener('keydown', (e) => { if (e.key === 'Enter') installPackage(); });
        searchBtn?.addEventListener('click', () => searchPackages());
        searchInput?.addEventListener('keydown', (e) => { if (e.key === 'Enter') searchPackages(); });

        // Popular package pills
        document.querySelectorAll('.npm-pill[data-pkg]').forEach(pill => {
            pill.addEventListener('click', () => {
                const pkg = pill.dataset.pkg;
                if (pkg) {
                    const input = document.getElementById('npm-pkg-name');
                    if (input) input.value = pkg;
                    installPackage();
                }
            });
        });
    }

    async function installPackage() {
        const input = document.getElementById('npm-pkg-name');
        const raw = input?.value.trim();
        if (!raw) {
            setInstallStatus('Enter a package name (e.g. simple-statistics or lodash@4.17.21)', 'error');
            return;
        }

        // Parse name@version
        const atIdx = raw.lastIndexOf('@');
        let name, version;
        if (atIdx > 0) {
            name    = raw.substring(0, atIdx);
            version = raw.substring(atIdx + 1);
        } else {
            name    = raw;
            version = 'latest';
        }

        setInstallStatus(`Installing ${name}@${version}… (this may take a moment)`, 'loading');
        Arima.setStatus('Installing npm: ' + name);

        try {
            const pkg = await Arima.api('POST', '/npm/packages/install', { name, version });
            setInstallStatus(`Installed: ${pkg.name} ${pkg.version}`, 'success');
            if (input) input.value = '';
            await loadInstalledPackages();
            Arima.setStatus('npm installed: ' + pkg.name);
        } catch (e) {
            setInstallStatus('Install failed: ' + e.message, 'error');
            Arima.setStatus('npm install failed');
        }
    }

    async function removePackage(name) {
        if (!confirm(`Remove npm package "${name}"?\n\n⚠ Warning: any JavaScript cell that calls require('${name}') will fail until the package is re-installed.`)) return;
        try {
            await Arima.api('DELETE', `/npm/packages/${encodeURIComponent(name)}`);
            await loadInstalledPackages();
            Arima.setStatus('npm removed: ' + name);
        } catch (e) {
            alert('Failed to remove: ' + e.message);
        }
    }

    async function searchPackages() {
        const input = document.getElementById('npm-pkg-search');
        const query = input?.value.trim();
        if (!query) return;

        const resultsEl = document.getElementById('npm-pkg-results');
        if (!resultsEl) return;
        resultsEl.innerHTML = '<div class="muted">Searching npm registry…</div>';
        resultsEl.classList.remove('hidden');

        try {
            const results = await Arima.api('GET', `/npm/packages/search?q=${encodeURIComponent(query)}`);
            if (!results || results.length === 0) {
                resultsEl.innerHTML = '<div class="muted">No results found.</div>';
                return;
            }
            resultsEl.innerHTML = results.map(pkg => `
                <div class="pkg-search-row">
                    <div>
                        <div class="pkg-search-name">${escapeHtml(pkg.name)}</div>
                        <div class="pkg-search-coord">${escapeHtml(pkg.description || '')}</div>
                    </div>
                    <button class="btn-secondary-sm" onclick="document.getElementById('npm-pkg-name').value='${escapeHtml(pkg.name)}@${escapeHtml(pkg.version)}'">Use</button>
                </div>`).join('');
        } catch (e) {
            resultsEl.innerHTML = `<div class="text-error">Search failed: ${e.message}</div>`;
        }
    }

    function escapeHtml(text) {
        if (!text) return '';
        return String(text)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    return { init, removePackage };
})();

document.addEventListener('DOMContentLoaded', () => NpmPackageManager.init());
