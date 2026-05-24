// Render the HTML mockups in articles/mockups/ to PNG images in docs/screenshots/
// Uses headless Chrome with viewport sized per mockup.

const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const OUT = path.resolve(__dirname, '..', 'docs', 'screenshots');

const MOCKUPS = [
  { html: 'medium-cover.html',    out: '00-cover-medium.png',     w: 1600, h: 900 },
  { html: 'linkedin-banner.html', out: '00-banner-linkedin.png',  w: 1584, h: 396 },
  { html: 'mcp-claude-mock.html', out: '08-mcp-claude.png',       w: 1600, h: 1000 },
  { html: 'concept-loop.html',    out: '09-agentic-loop.png',     w: 1600, h: 900 },
];

(async () => {
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true });

  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--hide-scrollbars']
  });

  for (const m of MOCKUPS) {
    const page = await browser.newPage();
    await page.setViewport({ width: m.w, height: m.h, deviceScaleFactor: 2 });
    const url = 'file:///' + path.resolve(__dirname, 'mockups', m.html).replace(/\\/g, '/');
    await page.goto(url, { waitUntil: 'networkidle0', timeout: 15000 });
    const outPath = path.join(OUT, m.out);
    await page.screenshot({ path: outPath, fullPage: false,
      clip: { x: 0, y: 0, width: m.w, height: m.h } });
    const kb = Math.round(fs.statSync(outPath).size / 1024);
    console.log(`✓ ${m.out}  (${kb} KB)  ${m.w}×${m.h}`);
    await page.close();
  }

  await browser.close();
  console.log('Mockups rendered to:', OUT);
})().catch(e => { console.error(e); process.exit(1); });
