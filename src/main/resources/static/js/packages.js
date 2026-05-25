/**
 * Arima Notebooks - Package Manager
 * Install and manage Maven packages from Maven Central.
 */

const PackageManager = (() => {
    function init() {
        loadInstalledPackages();
        bindButtons();
    }

    async function loadInstalledPackages() {
        const container = document.getElementById('pkg-list');
        const countBadge = document.getElementById('pkg-count');
        if (!container) return;

        try {
            const packages = await Arima.api('GET', '/packages');
            if (countBadge) countBadge.textContent = packages.length;
            renderPackageList(packages);
        } catch (e) {
            container.innerHTML = `<div class="text-error">Failed to load packages: ${e.message}</div>`;
        }
    }

    function renderPackageList(packages) {
        const container = document.getElementById('pkg-list');
        if (!container) return;

        if (!packages || packages.length === 0) {
            container.innerHTML = '<div class="muted">No packages installed yet.</div>';
            return;
        }

        container.innerHTML = packages.map(pkg => `
            <div class="pkg-item" id="pkg-item-${pkg.artifactId}">
                <div class="pkg-item-info">
                    <div class="pkg-item-name">${escapeHtml(pkg.artifactId)}
                        <span class="pkg-version">${escapeHtml(pkg.version)}</span>
                    </div>
                    <div class="pkg-item-coord">${escapeHtml(pkg.groupId)}:${escapeHtml(pkg.artifactId)}:${escapeHtml(pkg.version)}</div>
                </div>
                <button class="btn-danger-sm"
                    onclick="PackageManager.removePackage('${escapeHtml(pkg.groupId)}','${escapeHtml(pkg.artifactId)}','${escapeHtml(pkg.version)}')">
                    Remove
                </button>
            </div>
        `).join('');
    }

    function setInstallStatus(msg, type) {
        const el = document.getElementById('pkg-status');
        if (!el) return;
        el.textContent = msg;
        el.className = `pkg-status pkg-status-${type}`;
        el.classList.remove('hidden');
    }

    function bindButtons() {
        const installBtn  = document.getElementById('btn-pkg-install');
        const coordInput  = document.getElementById('pkg-coord');
        const searchBtn   = document.getElementById('btn-pkg-search');
        const searchInput = document.getElementById('pkg-search');

        installBtn?.addEventListener('click', () => installPackage());
        coordInput?.addEventListener('keydown', (e) => { if (e.key === 'Enter') installPackage(); });

        searchBtn?.addEventListener('click', () => searchPackages());
        searchInput?.addEventListener('keydown', (e) => { if (e.key === 'Enter') searchPackages(); });

        // Popular package pills
        document.querySelectorAll('.pill[data-coord]').forEach(pill => {
            pill.addEventListener('click', () => {
                const coord = pill.dataset.coord;
                if (coord) {
                    const input = document.getElementById('pkg-coord');
                    if (input) input.value = coord;
                    installPackage();
                }
            });
        });
    }

    async function installPackage() {
        const input = document.getElementById('pkg-coord');
        const coordinate = input?.value.trim();

        if (!coordinate) {
            setInstallStatus('Enter a coordinate: groupId:artifactId:version', 'error');
            return;
        }

        setInstallStatus('Installing ' + coordinate + '…', 'loading');
        Arima.setStatus('Installing: ' + coordinate);

        try {
            const pkg = await Arima.api('POST', '/packages/install', { coordinate });
            setInstallStatus(`Installed: ${pkg.artifactId} ${pkg.version}`, 'success');
            if (input) input.value = '';
            await loadInstalledPackages();
            Arima.setStatus('Package installed: ' + coordinate);
        } catch (e) {
            setInstallStatus('Install failed: ' + e.message, 'error');
            Arima.setStatus('Install failed');
        }
    }

    async function removePackage(groupId, artifactId, version) {
        if (!confirm(`Remove ${artifactId} ${version}?\n\n⚠ Warning: any notebook or cell that uses this package will fail to execute until it is re-installed.\n\nRestart JShell sessions after removal to fully unload it.`)) return;

        try {
            await Arima.api('DELETE', `/packages/${groupId}/${artifactId}/${version}`);
            await loadInstalledPackages();
            Arima.setStatus('Package removed: ' + artifactId);
        } catch (e) {
            alert('Failed to remove: ' + e.message);
        }
    }

    async function searchPackages() {
        const input = document.getElementById('pkg-search');
        const query = input?.value.trim();
        if (!query) return;

        const resultsEl = document.getElementById('pkg-results');
        if (!resultsEl) return;
        resultsEl.innerHTML = '<div class="muted">Searching…</div>';
        resultsEl.classList.remove('hidden');

        try {
            const data = await fetch(`/api/packages/search?q=${encodeURIComponent(query)}`).then(r => r.json());
            const docs = data?.response?.docs || [];

            if (docs.length === 0) {
                resultsEl.innerHTML = '<div class="muted">No results found.</div>';
                return;
            }

            resultsEl.innerHTML = docs.slice(0, 10).map(doc => {
                const coord = `${doc.g}:${doc.a}:${doc.latestVersion || doc.v || ''}`;
                return `
                <div class="pkg-search-row">
                    <div>
                        <div class="pkg-search-name">${escapeHtml(doc.a || doc.id)}</div>
                        <div class="pkg-search-coord">${escapeHtml(coord)}</div>
                    </div>
                    <button class="btn-secondary-sm" onclick="document.getElementById('pkg-coord').value='${escapeHtml(coord)}'">Use</button>
                </div>`;
            }).join('');
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

document.addEventListener('DOMContentLoaded', () => PackageManager.init());
