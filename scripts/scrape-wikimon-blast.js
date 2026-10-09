/**
 * Scrapes Wikimon "Evolves From" sections for every Digimon and merges them
 * into app/src/main/assets/blast_evolution.json as universal Blast options:
 *  - plain evolutions        -> soloForms (kind EVOLUTION)
 *  - "(with Partner)" lines  -> jogress pairs
 *  - X-Antibody lines/pages  -> soloForms (kind X_ANTIBODY, target "Source X")
 *
 * Hand-curated entries (specials, kinds, sourceUris) always win on conflicts.
 * Idempotent: re-running over generated output yields the same file.
 *
 * Usage: node scripts/scrape-wikimon-blast.js [--limit N] [--offset N]
 */
const fs = require('fs');
const path = require('path');

const API = 'https://wikimon.net/api.php';
const ASSET = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'blast_evolution.json');
const BATCH = 50;
const DELAY_MS = 400;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(params, retries = 4) {
  for (let attempt = 1; ; attempt++) {
    await sleep(DELAY_MS);
    const url = API + '?' + new URLSearchParams({ format: 'json', ...params }).toString();
    try {
      const res = await fetch(url, { headers: { 'User-Agent': 'vbhelper-blast-scraper/1.0 (contact: repo maintainer)' } });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return await res.json();
    } catch (e) {
      if (attempt >= retries) throw e;
      await sleep(2000 * attempt);
    }
  }
}

// ---------- matching (must mirror BlastEvolutionRepository.normalize) ----------
function normalize(name) {
  return name.trim().toLowerCase()
    .replace(/ /g, '').replace(/-/g, '').replace(/:/g, '').replace(/'/g, '')
    .replace(/\(/g, '').replace(/\)/g, '');
}
function applyAliases(stripped, aliases) {
  return aliases[stripped] || stripped;
}
function norm(data, name) {
  return applyAliases(normalize(name), data.aliases);
}

// ---------- wikitext parsing ----------
function stripRefsAndComments(line) {
  let s = line.replace(/<!--[\s\S]*?-->/g, '');
  s = s.replace(/<ref[^>]*>[\s\S]*?<\/ref>/gi, '');
  s = s.replace(/<ref[^>]*\/>/gi, '');
  s = s.replace(/''/g, '');
  // remove innermost {{...}} templates repeatedly (links use [[ ]], unaffected)
  let prev;
  do {
    prev = s;
    s = s.replace(/\{\{[^{}]*\}\}/g, '');
  } while (s !== prev);
  return s;
}

function extractLinks(text) {
  const out = [];
  const re = /\[\[([^\]|]+)(?:\|([^\]]+))?\]/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    const display = (m[2] !== undefined ? m[2] : m[1]).replace(/'''/g, '').trim();
    if (display) out.push({ display, end: re.lastIndex });
  }
  return out;
}

function firstParen(text, fromIndex) {
  const start = text.indexOf('(', fromIndex);
  if (start < 0) return null;
  let depth = 0;
  for (let i = start; i < text.length; i++) {
    if (text[i] === '(') depth++;
    else if (text[i] === ')') {
      depth--;
      if (depth === 0) return text.slice(start + 1, i);
    }
  }
  return null; // unbalanced
}

const PARTNER_EXCLUDE = /^any\b|digimental|digitron|x-antibody|digi-egg|spirit|digivice|digicore|digisoul|pendulum|vital bracelet|d-ark|d-scanner|d-spirit|digimon from the|data\b|^(vaccine|virus|data|free)$/i;
const SKIP_LINE = /digimon card game|digimon world:\s*digital card|digixros from certain/i;

function pageUrl(title) {
  return 'https://wikimon.net/' + title.replace(/ /g, '_');
}

/**
 * Parses the ==Evolves From== lines of one page.
 * Returns { forms: [{source,target}], pairs: [{a,b}] } with raw display names.
 */
function parseEvolvesFrom(title, wikitext, stats) {
  const forms = [];
  const pairs = [];
  const lines = wikitext.split('\n');
  let start = lines.findIndex((l) => l.trim() === '==Evolves From==');
  let section = '';
  if (start >= 0) {
    let end = lines.findIndex((l, i) => i > start && /^==[^=]/.test(l.trim()));
    if (end < 0) end = lines.length;
    section = lines.slice(start + 1, end).join('\n');
  }
  const isXPage = /\(X-Antibody\)/i.test(title);
  if (start < 0) { stats.noSection++; return { forms, pairs }; }
  for (const raw of section.split('\n')) {
    const line = raw.trim();
    if (!line.startsWith('*')) continue;
    stats.lines++;
    let clean = stripRefsAndComments(line);
    if (SKIP_LINE.test(clean)) { stats.skipped++; continue; }
    const links = extractLinks(clean);
    if (links.length === 0) { stats.skipped++; continue; }
    const source = links[0].display;
    if (!source || /^any\b/i.test(source) || /card game|digixros/i.test(source)) { stats.skipped++; continue; }
    // X-Antibody target pages: every evolution into them is an X-Evolution.
    if (isXPage) {
      forms.push({ source, target: source + ' X', kind: 'X_ANTIBODY' });
      stats.xEdges++;
      continue;
    }
    const rest = clean.slice(links[0].end);
    const paren = firstParen(rest, 0);
    if (paren !== null && /^\s*with\b/i.test(paren)) {
      if (/x-antibody/i.test(paren)) {
        forms.push({ source, target: source + ' X', kind: 'X_ANTIBODY' });
        stats.xEdges++;
        continue;
      }
      const innerLinks = extractLinks(paren).map((l) => l.display).filter(Boolean);
      if (/with or without/i.test(paren) || /xros/i.test(paren)) {
        forms.push({ source, target: title, kind: 'EVOLUTION' });
        stats.plainEdges++;
        continue;
      }
      const partners = innerLinks.filter((p) => !PARTNER_EXCLUDE.test(p));
      if (innerLinks.length > 0 && partners.length === 0) {
        // every "partner" was a device/generic descriptor, not a species: skip
        stats.skipped++;
        continue;
      }
      if (partners.length === 0) {
        forms.push({ source, target: title, kind: 'EVOLUTION' });
        stats.plainEdges++;
      } else {
        for (const p of partners) {
          pairs.push({ a: source, b: p, result: title });
          stats.pairEdges++;
        }
      }
    } else {
      forms.push({ source, target: title, kind: 'EVOLUTION' });
      stats.plainEdges++;
    }
  }
  return { forms, pairs };
}

// ---------- self test ----------
function selfTest() {
  const assert = require('assert');
  const stats = { lines: 0, skipped: 0, noSection: 0, xEdges: 0, plainEdges: 0, pairEdges: 0 };
  // mirrored jogress pair entries
  let r = parseEvolvesFrom('Omegamon Alter-B',
    '==Evolves From==\n* [[Ancient Garurumon]] (with [[Ancient Greymon]]){{ref|x}}\n* [[Blitz Greymon]]<ref>y</ref>\n==Evolves To==', stats);
  assert.deepStrictEqual(r.pairs, [{ a: 'Ancient Garurumon', b: 'Ancient Greymon', result: 'Omegamon Alter-B' }]);
  assert.deepStrictEqual(r.forms, [{ source: 'Blitz Greymon', target: 'Omegamon Alter-B', kind: 'EVOLUTION' }]);
  // multi-partner list
  r = parseEvolvesFrom('T',
    '==Evolves From==\n* [[Gran Kuwagamon]] (with [[Chaos Dukemon]], [[Mastemon]], or [[Rust Tyranomon]]){{ref|z}}\n==Evolves To==', stats);
  assert.strictEqual(r.pairs.length, 3);
  // X-Antibody page
  r = parseEvolvesFrom('War Greymon (X-Antibody)',
    '==Evolves From==\n* [[War Greymon]] (with the [[X-Antibody]]){{ref|a}}\n==Evolves To==', stats);
  assert.deepStrictEqual(r.forms, [{ source: 'War Greymon', target: 'War Greymon X', kind: 'X_ANTIBODY' }]);
  // with-or-without item -> plain edge, no pair
  r = parseEvolvesFrom('T',
    '==Evolves From==\n* [[Omegamon Alter-S]] (with or without [[Black Digitron]]){{ref|a}}\n==Evolves To==', stats);
  assert.deepStrictEqual(r.pairs, []);
  assert.strictEqual(r.forms.length, 1);
  // TCG generic skipped
  r = parseEvolvesFrom('T',
    "==Evolves From==\n* [[Digimon Card Game Colors and Levels#Black Lv.6 Digimon|Any Black Lv.6 Digimon from the ''Digimon Card Game'']]{{rfc|EX4}}\n==Evolves To==", stats);
  assert.deepStrictEqual(r.pairs, []);
  assert.deepStrictEqual(r.forms, []);
  // device/generic partners are dropped, not turned into pairs or plain edges
  r = parseEvolvesFrom('Ancient Troiamon',
    '==Evolves From==\n* [[Parrotmon]] (with [[Digimon Pendulum Z II]]){{ref|x}}\n* [[Peacockmon]] (with [[Vaccine]]){{ref|y}}\n==Evolves To==', stats);
  assert.deepStrictEqual(r.pairs, []);
  assert.deepStrictEqual(r.forms, []);
  // generic "Any ..." sources skipped
  r = parseEvolvesFrom('T',
    "==Evolves From==\n* [[Digimon World: Digital Card Arena|Any Adult Nature Digimon from ''Digimon World: Digital Card Arena'']]\n==Evolves To==", stats);
  assert.deepStrictEqual(r.pairs, []);
  assert.deepStrictEqual(r.forms, []);
  console.log('self-test OK');
}

// ---------- main crawl ----------
async function main() {
  const args = process.argv.slice(2);
  const limit = parseInt((args.find((a) => a.startsWith('--limit=')) || '').split('=')[1] || '0', 10);
  const offset = parseInt((args.find((a) => a.startsWith('--offset=')) || '').split('=')[1] || '0', 10);

  const hand = JSON.parse(fs.readFileSync(ASSET, 'utf8'));

  // 1. enumerate Category:Digimon
  let titles = [];
  let cmcontinue;
  do {
    const params = {
      action: 'query', list: 'categorymembers', cmtitle: 'Category:Digimon',
      cmtype: 'page', cmlimit: '500', ...(cmcontinue ? { cmcontinue } : {}),
    };
    const data = await api(params);
    titles.push(...(data.query?.categorymembers || []).map((m) => m.title));
    cmcontinue = data.continue?.cmcontinue;
    process.stdout.write(`\rlisted ${titles.length}...`);
  } while (cmcontinue);
  console.log(`\nCategory:Digimon members: ${titles.length}`);
  if (limit > 0) titles = titles.slice(offset, offset + limit);

  // 2. batch-fetch wikitext
  const stats = { lines: 0, skipped: 0, noSection: 0, xEdges: 0, plainEdges: 0, pairEdges: 0, pages: 0, failed: 0 };
  const scrapedForms = [];
  const scrapedPairs = [];
  for (let i = 0; i < titles.length; i += BATCH) {
    const batch = titles.slice(i, i + BATCH);
    try {
      const data = await api({
        action: 'query', prop: 'revisions', rvprop: 'content', rvslots: 'main',
        formatversion: '2', redirects: '1', titles: batch.join('|'),
      });
      for (const page of data.query?.pages || []) {
        if (page.missing) continue;
        const content = page.revisions?.[0]?.slots?.main?.content;
        if (!content) continue;
        stats.pages++;
        const { forms, pairs } = parseEvolvesFrom(page.title, content, stats);
        const url = pageUrl(page.title);
        for (const f of forms) scrapedForms.push({ ...f, sourceUri: url });
        for (const p of pairs) scrapedPairs.push({ ...p, sources: ['Wikimon'] });
      }
    } catch (e) {
      stats.failed += batch.length;
      console.error(`\nbatch ${i} failed: ${e.message}`);
    }
    process.stdout.write(`\rpages ${Math.min(i + BATCH, titles.length)}/${titles.length} forms=${scrapedForms.length} pairs=${scrapedPairs.length}`);
  }
  console.log('\n', JSON.stringify(stats));

  // 3. merge (hand entries win), normalize-aware dedupe
  const norm = (s) => {
    const key = s.trim().toLowerCase().replace(/ /g, '').replace(/-/g, '').replace(/:/g, '')
      .replace(/'/g, '').replace(/\(/g, '').replace(/\)/g, '');
    return hand.aliases[key] || key;
  };
  const seenForms = new Set(hand.soloForms.map((f) => norm(f.source) + '|' + norm(f.target)));
  const seenPairs = new Set(hand.jogress.map((j) => [j.a, j.b, j.result].map(norm).sort().join('|')));
  const mergedForms = [...hand.soloForms];
  const mergedPairs = [...hand.jogress];
  let addedForms = 0, addedPairs = 0, dupForms = 0, dupPairs = 0;
  for (const f of scrapedForms) {
    if (!f.source || !f.target || norm(f.source) === norm(f.target)) continue;
    const key = norm(f.source) + '|' + norm(f.target);
    if (seenForms.has(key)) { dupForms++; continue; }
    seenForms.add(key);
    mergedForms.push({ source: f.source, target: f.target, kind: f.kind, special: null, sourceUri: f.sourceUri });
    addedForms++;
  }
  for (const p of scrapedPairs) {
    if (!p.a || !p.b || !p.result) continue;
    const key = [p.a, p.b, p.result].map(norm).sort().join('|');
    if (seenPairs.has(key)) { dupPairs++; continue; }
    seenPairs.add(key);
    mergedPairs.push({ a: p.a, b: p.b, result: p.result, resultSpecial: null, sources: p.sources });
    addedPairs++;
  }

  const out = {
    ...hand,
    version: 2,
    updatedAt: new Date().toISOString().slice(0, 10),
    notes: 'Universal Blast/Jogress table. Hand-curated entries (specials, kinds, sourceUris) win on conflicts. Bulk entries were scraped from Wikimon Evolves From sections (plain evolutions as kind EVOLUTION, "(with Partner)" lines as Jogress pairs, X-Antibody lines as X_ANTIBODY forms); TCG-generic lines excluded.',
    soloForms: mergedForms,
    jogress: mergedPairs,
  };
  fs.writeFileSync(ASSET, JSON.stringify(out, null, 2) + '\n');
  console.log(`merged: +${addedForms} forms (${dupForms} dupes), +${addedPairs} pairs (${dupPairs} dupes)`);
  console.log(`total: ${mergedForms.length} forms, ${mergedPairs.length} pairs`);
}

if (require.main === module) {
  selfTest();
  main().catch((e) => { console.error('FATAL', e); process.exit(1); });
}
