// Drive the running Arima instance via Chrome to capture real product screenshots.
// Requires Arima running at http://localhost:8585 and Chrome installed on Windows.
//
// Usage:  node capture-screenshots.js

const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const ARIMA  = 'http://localhost:8585/';
const OUT    = path.resolve(__dirname, '..', 'docs', 'screenshots');

const VIEWPORT = { width: 1600, height: 1000, deviceScaleFactor: 2 };

async function withPage(fn) {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    defaultViewport: VIEWPORT,
    args: ['--no-sandbox', '--hide-scrollbars']
  });
  try {
    const page = await browser.newPage();
    await page.setViewport(VIEWPORT);
    // Suppress the first-run Welcome / What's-New overlay so screenshots show
    // the actual app, not the popup.
    await page.evaluateOnNewDocument(() => {
      try {
        localStorage.setItem('barista.guide.seen', '1');
        localStorage.setItem('barista.guide.version', '3.1.0');
        localStorage.setItem('barista.guide.mute', '1');
      } catch (e) {}
    });
    await fn(page);
  } finally {
    await browser.close();
  }
}

async function waitForAppReady(page) {
  await page.goto(ARIMA, { waitUntil: 'networkidle2', timeout: 30000 });
  // Wait for the notebook UI to be present
  await page.waitForSelector('#notebook-selector', { timeout: 15000 });
  // Give the app a moment to initialize internal state
  await new Promise(r => setTimeout(r, 1500));
  // Defensively dismiss the welcome overlay if it slipped through
  await page.evaluate(() => { try { if (window.Welcome) window.Welcome.close(); } catch (e) {} });
  await new Promise(r => setTimeout(r, 300));
}

async function loadNotebook(page, id, isTutorial = false) {
  // Call the app's loader directly so isTutorial templates (rebranded, read-only)
  // load even when the local user has no personal copy. Fall back to the selector.
  await page.evaluate((id, isTut) => {
    if (typeof NotebookEditor !== 'undefined' && NotebookEditor.loadNotebook) {
      NotebookEditor.loadNotebook(id, isTut);
    } else {
      const sel = document.getElementById('notebook-selector');
      sel.value = id;
      sel.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }, id, isTutorial);
  // Wait for cells to render
  await new Promise(r => setTimeout(r, 3500));
}

async function shoot(page, filename) {
  const out = path.join(OUT, filename);
  await page.screenshot({ path: out, fullPage: false });
  const stat = fs.statSync(out);
  console.log(`✓ ${filename}  (${Math.round(stat.size / 1024)} KB)`);
}

(async () => {
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true });

  // -- Shot 0: Clean app shell (no notebook) — Arima hero, overlay suppressed --
  await withPage(async (page) => {
    await waitForAppReady(page);
    await shoot(page, '00-hero-raw.png');
  });

  // NOTE: load TUTORIAL templates (read-only, rebranded) via isTutorial=true and
  // use ids the local user has no personal copy of, so screenshots show the clean
  // Arima content rather than a saved local-sures copy.

  // -- Shot 1: Hero — Java functional & streams tutorial --
  await withPage(async (page) => {
    await waitForAppReady(page);
    await loadNotebook(page, 'java-401', true);
    await shoot(page, '01-hero-welcome.png');
  });

  // -- Shot 2: Multi-language demonstration — C# data & pipelines --
  await withPage(async (page) => {
    await waitForAppReady(page);
    await loadNotebook(page, 'csharp-201', true);
    await shoot(page, '02-csharp-notebook.png');
  });

  // -- Shot 3: TypeScript tutorial (intermediate) --
  await withPage(async (page) => {
    await waitForAppReady(page);
    await loadNotebook(page, 'ts-201', true);
    await shoot(page, '03-typescript-notebook.png');
  });

  // -- Shot 4: JShell tutorial showing variable persistence --
  await withPage(async (page) => {
    await waitForAppReady(page);
    await loadNotebook(page, 'jshell-201', true);
    await shoot(page, '04-jshell-tutorial.png');
  });

  // -- Shot 5: Notebook browser / tutorials grid --
  await withPage(async (page) => {
    await waitForAppReady(page);
    // Click the Tutorials button if present
    const tutBtn = await page.$('button[onclick*="tutorial"], #btn-tutorials, [data-action="tutorials"]');
    if (tutBtn) {
      await tutBtn.click();
      await new Promise(r => setTimeout(r, 1500));
    }
    await shoot(page, '05-tutorials-overview.png');
  });

  // -- Shot 6: Packages tab --
  await withPage(async (page) => {
    await waitForAppReady(page);
    await page.evaluate(() => {
      const btn = document.querySelector('[data-tab="packages"], #tab-packages, button[onclick*="packages"]');
      if (btn) btn.click();
    });
    await new Promise(r => setTimeout(r, 1500));
    await shoot(page, '06-packages-tab.png');
  });

  // -- Shot 7: Settings tab --
  await withPage(async (page) => {
    await waitForAppReady(page);
    await page.evaluate(() => {
      const btn = document.querySelector('[data-tab="settings"], #tab-settings, button[onclick*="settings"]');
      if (btn) btn.click();
    });
    await new Promise(r => setTimeout(r, 1500));
    await shoot(page, '07-settings-tab.png');
  });

  console.log('All screenshots written to:', OUT);
})().catch(e => { console.error(e); process.exit(1); });
