// Renders docs/mascot/hanga.svg in every mood, for quick visual checks.
//   node tools/render-mascot.js [out.png]
// Needs Playwright (any recent version with Chromium).
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const svg = fs.readFileSync(path.join(__dirname, '..', 'docs', 'mascot', 'hanga.svg'), 'utf8');
const layers = ['eyes_open', 'eyes_happy', 'eyes_closed', 'brows_soft', 'brows_worried',
  'mouth_smile', 'mouth_open', 'mouth_o', 'mouth_hum'];
const moods = {
  idle: ['eyes_open', 'brows_soft', 'mouth_smile'],
  working: ['eyes_closed', 'brows_soft', 'mouth_hum'],
  happy: ['eyes_happy', 'brows_soft', 'mouth_open'],
  oops: ['eyes_open', 'brows_worried', 'mouth_o'],
  waiting: ['eyes_closed', 'brows_soft', 'mouth_smile'],
};

function variant(name, visible, size) {
  const hidden = layers.filter((l) => !visible.includes(l));
  const css = hidden.map((l) => `.${name} #${l}{display:none}`).join('\n');
  // Ids must stay unique per copy for gradients: prefix them.
  const body = svg
    .replace(/id="([^"]+)"/g, `id="${name}-$1"`)
    .replace(/url\(#([^)]+)\)/g, `url(#${name}-$1)`)
    .replace('<svg ', `<svg class="${name}" width="${size}" height="${size}" `);
  const scoped = css.replace(new RegExp(`\\.${name} #`, 'g'), `.${name} #${name}-`);
  return { body, css: scoped };
}

(async () => {
  const out = process.argv[2] || path.join(__dirname, '..', 'docs', 'mascot', 'moods.png');
  const big = variant('hero', moods.idle, 480);
  const small = Object.entries(moods).map(([name, visible]) => ({ name, ...variant(name, visible, 220) }));
  const html = `<!doctype html><html><head><style>
    body{margin:0;padding:24px;background:#FFF7FA;font-family:sans-serif;color:#5A3150}
    .row{display:flex;gap:16px;flex-wrap:wrap;align-items:flex-end}
    figure{margin:0;text-align:center} figcaption{font-size:14px;margin-top:4px}
    .dark{background:#1E1520;padding:16px;border-radius:24px;margin-top:16px}
    ${big.css}${small.map((s) => s.css).join('\n')}
  </style></head><body>
    <div class="row"><figure>${big.body}<figcaption>Hanga</figcaption></figure>
    <div class="row" style="flex-direction:column;align-items:flex-start">
      <div class="row">${small.slice(0, 3).map((s) => `<figure>${s.body}<figcaption>${s.name}</figcaption></figure>`).join('')}</div>
      <div class="row">${small.slice(3).map((s) => `<figure>${s.body}<figcaption>${s.name}</figcaption></figure>`).join('')}</div>
    </div></div>
  </body></html>`;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 560 }, deviceScaleFactor: 1.5 });
  await page.setContent(html);
  await page.screenshot({ path: out, fullPage: true });
  await browser.close();
  console.log('wrote', out);
})();
