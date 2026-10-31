// Renders a layered mascot SVG in every mood, for visual checks.
//   node tools/render-mascot.js <mascot.svg> <out.png> [moods.json]
// The SVG's top-level <g id="..."> elements are layers; a mood is the list of
// layers that are visible. The default moods are Dalma's (tools/moods.json).
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const svgPath = process.argv[2] || path.join(__dirname, '..', 'docs', 'mascot', 'dalma.svg');
const out = process.argv[3] || svgPath.replace(/\.svg$/, '.png');
const moodsPath = process.argv[4] || path.join(__dirname, 'moods.json');

const svg = fs.readFileSync(svgPath, 'utf8');
const moods = JSON.parse(fs.readFileSync(moodsPath, 'utf8'));
const always = moods.always || [];
const layers = [...new Set(svg.match(/<g id="([^"]+)"/g).map((m) => m.slice(7, -1)))];

function variant(name, visible, size) {
  const hidden = layers.filter((l) => !visible.includes(l) && !always.includes(l));
  const prefix = name.replace(/[^a-z0-9]/gi, '');
  const css = hidden.map((l) => `#${prefix}-${l}{display:none}`).join('\n');
  const body = svg
    .replace(/id="([^"]+)"/g, `id="${prefix}-$1"`)
    .replace(/url\(#([^)]+)\)/g, `url(#${prefix}-$1)`)
    .replace(/href="#([^"]+)"/g, `href="#${prefix}-$1"`)
    .replace(/<svg([^>]*?)(\s+width="[^"]*")?(\s+height="[^"]*")?/, `<svg$1`)
    .replace('<svg ', `<svg width="${size}" height="${size}" `);
  return { body, css };
}

(async () => {
  const names = Object.keys(moods.moods);
  const hero = variant('hero', moods.moods[names[0]], 480);
  const small = names.map((name) => ({ name, ...variant(name, moods.moods[name], 200) }));
  const html = `<!doctype html><html><head><style>
    body{margin:0;padding:24px;background:${moods.background || '#0E1B19'};font-family:sans-serif;color:#F4FBF8}
    .row{display:flex;gap:16px;flex-wrap:wrap;align-items:flex-end}
    figure{margin:0;text-align:center;background:${moods.card || '#16302B'};border-radius:20px;padding:8px}
    figcaption{font-size:14px;margin-top:4px}
    .light{background:#F4FBF8;color:#24564B}
    ${hero.css}${small.map((s) => s.css).join('\n')}
  </style></head><body>
    <div class="row">
      <figure>${hero.body}<figcaption>${names[0]} (480px)</figcaption></figure>
      <figure class="light">${variant('light', moods.moods[names[0]], 160).body}<figcaption>on light</figcaption></figure>
      <figure>${variant('tiny', moods.moods[names[0]], 64).body}<figcaption>64px</figcaption></figure>
    </div>
    <div class="row" style="margin-top:16px">${small.map((s) => `<figure>${s.body}<figcaption>${s.name}</figcaption></figure>`).join('')}</div>
    ${variant('light', moods.moods[names[0]], 160).css}${variant('tiny', moods.moods[names[0]], 64).css}
  </body></html>`;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 }, deviceScaleFactor: 1.5 });
  await page.setContent(html);
  await page.screenshot({ path: out, fullPage: true });
  await browser.close();
  console.log('layers:', layers.join(', '));
  console.log('wrote', out);
})();
