// Convert markdown articles to styled HTML, then to PDF via headless Chrome.
// Mermaid code blocks render as actual diagrams.
//
// Usage:  node build-pdf.js

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const { marked } = require('marked');
const puppeteer = require('puppeteer-core');

const ARTICLES = ['medium-article.md', 'linkedin-article.md'];
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

const css = `
  @page { size: A4; margin: 18mm 16mm; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    max-width: 780px; margin: 0 auto; padding: 0;
    color: #1f2937; line-height: 1.65; font-size: 11pt;
  }
  h1 { font-size: 2em; border-bottom: 3px solid #06b6d4; padding-bottom: 0.3em;
       color: #0f172a; margin-top: 0; }
  h2 { font-size: 1.45em; color: #0f172a; margin-top: 1.8em;
       border-bottom: 1px solid #e5e7eb; padding-bottom: 0.2em; }
  h3 { font-size: 1.15em; color: #334155; margin-top: 1.4em; }
  h3 em { color: #64748b; font-weight: normal; }
  p { margin: 0.7em 0; }
  code { background: #f1f5f9; padding: 0.15em 0.4em; border-radius: 3px;
         font-family: 'Consolas', 'Monaco', monospace; font-size: 0.88em; color: #be123c; }
  pre { background: #1e293b; color: #f1f5f9; padding: 1em 1.2em;
        border-radius: 6px; overflow-x: auto; font-size: 0.85em; line-height: 1.5; }
  pre code { background: none; color: inherit; padding: 0; font-size: inherit; }
  blockquote { border-left: 4px solid #06b6d4; background: #ecfeff;
               padding: 0.6em 1em; margin: 1em 0; color: #155e75;
               font-style: italic; border-radius: 0 4px 4px 0; }
  blockquote strong { color: #0e7490; font-style: normal; }
  table { border-collapse: collapse; width: 100%; margin: 1em 0; font-size: 0.92em; }
  th, td { border: 1px solid #e5e7eb; padding: 0.5em 0.8em; text-align: left;
           vertical-align: top; }
  th { background: #f8fafc; font-weight: 600; color: #0f172a; }
  a { color: #0891b2; text-decoration: none; }
  a:hover { text-decoration: underline; }
  hr { border: none; border-top: 1px solid #e5e7eb; margin: 2em 0; }
  ul, ol { margin: 0.7em 0; padding-left: 1.5em; }
  li { margin: 0.3em 0; }
  .mermaid-svg { text-align: center; margin: 1.5em 0; padding: 1.2em 0.5em;
             background: #f8fafc; border-radius: 8px; page-break-inside: avoid;
             border: 1px solid #e2e8f0; }
  .mermaid-svg svg { max-width: 100%; height: auto; display: inline-block; }
  img { max-width: 100%; height: auto; display: block;
        margin: 1.2em auto; border-radius: 6px;
        box-shadow: 0 4px 18px rgba(0,0,0,0.10); page-break-inside: avoid; }
  img + em, p > em:only-child { display: block; text-align: center;
        color: #64748b; font-size: 0.88em; margin-top: -0.5em; margin-bottom: 1.5em; }
  /* page-break controls */
  h1, h2, h3 { page-break-after: avoid; }
  pre, blockquote, table { page-break-inside: avoid; }
`;

// Pre-render all mermaid blocks in a markdown string to inline SVG.
// Returns the rewritten markdown with ```mermaid blocks replaced by HTML <div>...SVG...</div>.
async function preRenderMermaid(md, browser) {
  const blocks = [];
  const RE = /```mermaid\n([\s\S]*?)```/g;
  let m; while ((m = RE.exec(md))) blocks.push(m[1]);
  if (blocks.length === 0) return md;

  console.log(`   rendering ${blocks.length} mermaid block(s)…`);

  const page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 900, deviceScaleFactor: 1 });
  // Inline mermaid via CDN; let it load
  await page.setContent(`<!DOCTYPE html><html><body>
    <div id="container"></div>
    <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
    <script>
      mermaid.initialize({
        startOnLoad: false,
        theme: 'default',
        flowchart: { useMaxWidth: true, htmlLabels: false, curve: 'basis' }
      });
    </script>
  </body></html>`, { waitUntil: 'networkidle0' });

  const svgs = [];
  for (let i = 0; i < blocks.length; i++) {
    const svg = await page.evaluate(async (code, idx) => {
      const { svg } = await mermaid.render('mmd-' + idx, code);
      return svg;
    }, blocks[i], i);
    svgs.push(svg);
  }
  await page.close();

  let i = 0;
  return md.replace(/```mermaid\n[\s\S]*?```/g, () => {
    const svg = svgs[i++];
    return `<div class="mermaid-svg">\n\n${svg}\n\n</div>`;
  });
}

async function buildHtml(mdPath, title, browser) {
  let md = fs.readFileSync(mdPath, 'utf8');
  md = await preRenderMermaid(md, browser);
  const body = marked.parse(md, { mangle: false, headerIds: true });
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>${title}</title>
<style>${css}</style>
</head>
<body>${body}</body>
</html>`;
}

async function mdToPdf(mdFile, browser) {
  const base = path.basename(mdFile, '.md');
  const htmlFile = path.resolve(__dirname, `${base}.html`);
  const pdfFile  = path.resolve(__dirname, `${base}.pdf`);

  console.log(`-> ${base}.html`);
  const html = await buildHtml(path.resolve(__dirname, mdFile), base, browser);
  fs.writeFileSync(htmlFile, html);

  console.log(`-> ${base}.pdf`);
  // Use puppeteer's built-in PDF (better than CLI flag for our needs — and uses the same browser)
  const page = await browser.newPage();
  await page.goto('file:///' + htmlFile.replace(/\\/g, '/'), { waitUntil: 'networkidle0' });
  await page.pdf({
    path: pdfFile,
    format: 'A4',
    margin: { top: '18mm', bottom: '18mm', left: '16mm', right: '16mm' },
    printBackground: true,
    preferCSSPageSize: false
  });
  await page.close();
  const kb = Math.round(fs.statSync(pdfFile).size / 1024);
  console.log(`   ${pdfFile.split(path.sep).pop()}  (${kb} KB)`);
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--hide-scrollbars']
  });
  try {
    for (const f of ARTICLES) {
      await mdToPdf(f, browser);
    }
  } finally {
    await browser.close();
  }
  console.log('Done.');
})().catch(e => { console.error(e); process.exit(1); });
