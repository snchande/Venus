/**
 * Venus Notebooks — Interactive Console
 *
 * Multi-runtime REPL: JShell, Java (compile), and JavaScript (Node.js).
 * Features: runtime selector buttons, ↑↓ history, Tab completion (JShell server-side,
 * Java/JS client-side keyword hints).
 */
const ConsoleTab = (() => {
    // ── State ─────────────────────────────────────────────────────
    let sessionId    = 'console';
    let runtime      = 'jshell';
    let history      = [];
    let historyIndex = -1;
    let completions  = [];
    let compIndex    = -1;
    let lastCompSource = '';

    const RUNTIME_CFG = {
        jshell: { label: 'JShell',       icon: '☕', placeholder: 'Java expression or snippet…  Tab = complete,  ↑↓ = history' },
        java:   { label: 'Java',          icon: '♨',  placeholder: 'Full Java class or static method body…  ↑↓ = history' },
        nodejs: { label: 'JavaScript',    icon: '⬡',  placeholder: 'JavaScript expression or require(…)…  Tab = hints,  ↑↓ = history' }
    };

    const STATIC_HINTS = {
        java: ['System.out.println(', 'System.err.println(', 'public class ', 'public static void main(String[] args)',
               'String ', 'int ', 'double ', 'boolean ', 'List.of(', 'Map.of(', 'Arrays.asList(',
               'var ', 'record ', 'interface ', 'import '],
        nodejs: ['console.log(', 'console.error(', 'require(', 'process.env.', 'JSON.stringify(',
                 'JSON.parse(', 'Array.from(', 'Object.keys(', 'Object.values(', 'Promise.all(',
                 'async function ', 'const ', 'let ', 'module.exports =']
    };

    // ── Init ──────────────────────────────────────────────────────
    function init() {
        const input      = document.getElementById('console-input');
        const runBtn     = document.getElementById('btn-console-run');
        const clearBtn   = document.getElementById('btn-console-clear');
        const restartBtn = document.getElementById('btn-console-restart');

        if (!input) return;

        bindRuntimeButtons();
        updateInputUI();

        input.addEventListener('keydown', async (e) => {
            if (e.key === 'Tab') {
                e.preventDefault();
                await handleTabComplete(input);
                return;
            }
            if (e.key !== 'Shift') resetCompletions();

            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                runCode();
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                navigateHistory(-1, input);
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                navigateHistory(1, input);
            }
        });

        runBtn?.addEventListener('click', runCode);
        clearBtn?.addEventListener('click', clearConsole);

        restartBtn?.addEventListener('click', async () => {
            const label = RUNTIME_CFG[runtime].label;
            if (!confirm(`Restart ${label} session? All variables will be cleared.`)) return;
            try {
                if (runtime === 'jshell') {
                    await Venus.api('POST', `/shell/${sessionId}/restart`);
                }
                appendEntry('system', `--- ${label} session restarted — all variables cleared ---`);
            } catch (e) {
                appendEntry('error', 'Restart failed: ' + e.message);
            }
        });

        appendEntry('system', 'Venus Console ready.  Select a runtime above and start typing.');
    }

    // ── Runtime selector ──────────────────────────────────────────
    function bindRuntimeButtons() {
        document.querySelectorAll('.console-runtime-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                runtime = btn.dataset.runtime;
                document.querySelectorAll('.console-runtime-btn').forEach(b =>
                    b.classList.toggle('active', b.dataset.runtime === runtime));
                updateInputUI();
                resetCompletions();
                appendEntry('system', `--- Switched to ${RUNTIME_CFG[runtime].label} ---`);
            });
        });
        document.querySelector(`.console-runtime-btn[data-runtime="${runtime}"]`)?.classList.add('active');
    }

    function updateInputUI() {
        const cfg = RUNTIME_CFG[runtime];
        const input = document.getElementById('console-input');
        if (input) input.placeholder = cfg.placeholder;
        const badge = document.getElementById('console-runtime-badge');
        if (badge) badge.textContent = `${cfg.icon} ${cfg.label}`;
    }

    // ── Tab completion ────────────────────────────────────────────
    async function handleTabComplete(input) {
        const source = input.value;
        const cursor = input.selectionStart ?? source.length;

        if (runtime === 'jshell') {
            // Server-side JShell completion via SourceCodeAnalysis
            if (completions.length === 0 || source !== lastCompSource) {
                try {
                    const res = await Venus.api('POST', '/shell/complete', { sessionId, source, cursor });
                    completions = (res.completions || []).filter(c => c.trim().length > 0);
                    lastCompSource = source;
                    compIndex = -1;
                } catch { completions = []; }
            }
        } else {
            // Client-side keyword hints
            const word = (source.slice(0, cursor).match(/[\w.]+$/) || [''])[0];
            if (word.length >= 1) {
                const pool = STATIC_HINTS[runtime] ?? [];
                completions = pool.filter(c => c.toLowerCase().startsWith(word.toLowerCase()));
            }
            compIndex = -1;
        }

        if (completions.length === 0) {
            hideHintBox();
            return;
        }

        compIndex = (compIndex + 1) % completions.length;
        const pick = completions[compIndex];

        // Replace the last partial token with the picked completion
        const word2 = (source.slice(0, cursor).match(/[\w.]*$/) || [''])[0];
        const before = source.slice(0, cursor - word2.length);
        const after  = source.slice(cursor);
        input.value  = before + pick + after;
        const nc = before.length + pick.length;
        input.setSelectionRange(nc, nc);

        showHintBox(input, completions, compIndex);
    }

    function showHintBox(input, list, activeIdx) {
        let box = document.getElementById('console-hint-box');
        if (!box) {
            box = document.createElement('div');
            box.id = 'console-hint-box';
            box.className = 'console-hint-box';
            input.closest('.console-input-row')?.appendChild(box);
        }
        const MAX = 10;
        const shown = list.slice(0, MAX);
        box.innerHTML = shown.map((c, i) =>
            `<div class="hint-item${i === activeIdx ? ' active' : ''}" data-idx="${i}">${esc(c)}</div>`
        ).join('') + (list.length > MAX ? `<div class="hint-more">+${list.length - MAX} more (keep pressing Tab)</div>` : '');
        box.style.display = 'block';

        box.querySelectorAll('.hint-item').forEach(item => {
            item.addEventListener('mousedown', (e) => {
                e.preventDefault();
                const picked = list[+item.dataset.idx];
                const src = input.value;
                const cur = input.selectionStart ?? src.length;
                const w = (src.slice(0, cur).match(/[\w.]*$/) || [''])[0];
                input.value = src.slice(0, cur - w.length) + picked + src.slice(cur);
                hideHintBox();
                input.focus();
            });
        });

        // Hide on outside click
        setTimeout(() => {
            document.addEventListener('click', function hider(e) {
                if (!box.contains(e.target) && e.target !== input) {
                    hideHintBox();
                    document.removeEventListener('click', hider);
                }
            });
        }, 0);
    }

    function hideHintBox() {
        const box = document.getElementById('console-hint-box');
        if (box) box.style.display = 'none';
    }

    function resetCompletions() {
        completions = [];
        compIndex = -1;
        hideHintBox();
    }

    function esc(s) { return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

    // ── Execute ───────────────────────────────────────────────────
    async function runCode() {
        const input = document.getElementById('console-input');
        if (!input) return;
        const code = input.value.trim();
        if (!code) return;

        hideHintBox();
        appendEntry('input', `[${RUNTIME_CFG[runtime].icon}] ${code}`);
        history.unshift(code);
        if (history.length > 500) history.pop();
        historyIndex = -1;
        input.value = '';
        resetCompletions();

        try {
            const result = await Venus.api('POST', '/shell/execute', {
                sessionId,
                code,
                cellId: null,
                mode: runtime
            });
            appendOutput(result);
        } catch (e) {
            appendEntry('error', 'Error: ' + e.message);
        }
    }

    function appendOutput(result) {
        if (result.output?.trim())   appendEntry('output', result.output.trimEnd());
        if (result.returnValue && result.returnValue !== 'null') appendEntry('return', result.returnValue);
        if (result.error?.trim())    appendEntry('error', result.error.trimEnd());
        if (result.executionTimeMs != null) appendEntry('system', `Completed in ${result.executionTimeMs}ms`);
    }

    // ── Output rendering ──────────────────────────────────────────
    function appendEntry(type, text) {
        const output = document.getElementById('console-output');
        if (!output) return;
        const cssMap = { input:'cout-in', output:'cout-out', return:'cout-ret', error:'cout-err', system:'cout-sys' };
        const entry  = document.createElement('div');
        entry.className = 'console-entry';

        // Support VENUS_HTML: inline charts from JavaScript cells
        if (type === 'output' && text.includes('VENUS_HTML:')) {
            const lines = text.split('\n');
            const textLines = [];
            lines.forEach(line => {
                if (line.startsWith('VENUS_HTML:')) {
                    if (textLines.length) {
                        const pre = document.createElement('pre');
                        pre.className = cssMap.output;
                        pre.textContent = textLines.join('\n');
                        entry.appendChild(pre);
                        textLines.length = 0;
                    }
                    const wrap = document.createElement('div');
                    wrap.className = 'venus-html-output';
                    wrap.innerHTML = line.slice(11);
                    entry.appendChild(wrap);
                } else {
                    textLines.push(line);
                }
            });
            if (textLines.length) {
                const pre = document.createElement('pre');
                pre.className = cssMap.output;
                pre.textContent = textLines.join('\n');
                entry.appendChild(pre);
            }
        } else {
            const inner = document.createElement('pre');
            inner.className = cssMap[type] || 'cout-sys';
            inner.textContent = text;
            entry.appendChild(inner);
        }

        output.appendChild(entry);
        output.scrollTop = output.scrollHeight;
    }

    function clearConsole() {
        const output = document.getElementById('console-output');
        if (output) output.innerHTML = '<div class="console-info">Console cleared. Ready.</div>';
        hideHintBox();
    }

    // ── History ───────────────────────────────────────────────────
    function navigateHistory(dir, input) {
        if (!history.length) return;
        historyIndex = Math.max(-1, Math.min(history.length - 1, historyIndex + dir));
        input.value  = historyIndex >= 0 ? history[historyIndex] : '';
    }

    return { init };
})();

document.addEventListener('DOMContentLoaded', () => ConsoleTab.init());
