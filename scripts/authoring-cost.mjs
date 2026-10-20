#!/usr/bin/env node
// Measures what one part of an Authoring cost: agent tokens, dollars, and time.
//
//   node scripts/authoring-cost.mjs <project-slug> --part deck|track|other
//        [--since 2026-08-28] [--until 2026-09-01] [--label "..."] [--scope main|subagents|all]
//        [--transcript agent-a1df1994,agent-a519a70c]
//
// --since and --until take a date (whole UTC days; --until includes the day it
// names) or an exact ISO timestamp such as 2026-09-29T13:15:53Z (exclusive).
// Timestamps let a Deck and a Track built on the same day be measured apart.
// --part other costs work that is neither, e.g. template changes made during
// an Authoring, so it stays out of the two README figures.
//
// --transcript keeps only transcripts whose path contains one of the comma-separated
// substrings. Workshops built by parallel subagents share a time window, so each
// is measured from its own subagent transcript instead.
//
// Run once per part: the Deck cost and the Track cost are separate lines in the
// README index, so measure each over the window (or project slug) it was built in.
//
// <project-slug> is a directory under ~/.claude/projects, e.g.
// -Users-nikk-source-temporal-java-temporal-devdays-java.
//
// Claude Code writes the main session to <project>/<session-id>.jsonl and each
// subagent to <project>/<session-id>/subagents/agent-*.jsonl. Main-session lines
// carry isSidechain:false, subagent lines isSidechain:true — which is how work
// delegated to an agent gets costed separately from work done in the main thread.
// We therefore walk the tree, not just the top level.
//
// Reports a FLOOR, not a total: only what the agent spent. Human review, sandbox
// debugging, and image rebuilds are invisible here and must be logged by hand.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { homedir } from 'node:os';

// $ per million tokens, published first-party API rates. `read` is the published
// cache-read rate where one differs from the 0.1x default (Opus 5.5 is 0.05x,
// Fable 5.1 0.025x). Cache writes use the standard ratios (1h 2x, 5m 1.25x), not
// numbers read off a rate card, so treat cache-write dollars as approximate.
// Keys are model ids without a date suffix; see baseModel().
const RATES = {
  'claude-fable-5-1':  { in: 10, out: 50, read: 0.25 },
  'claude-fable-5':    { in: 10, out: 50 },
  'claude-opus-5-5':   { in: 4,  out: 20, read: 0.20 },
  'claude-opus-5':     { in: 5,  out: 25 },
  'claude-opus-4-8':   { in: 5,  out: 25 },
  'claude-sonnet-5-5': { in: 2,  out: 10, read: 0.20 },
  'claude-sonnet-5':   { in: 2,  out: 10 },
  'claude-haiku-4-5':  { in: 1,  out: 5  },
};
const CACHE_1H = 2.0, CACHE_5M = 1.25, CACHE_READ = 0.1;

// Claude Code records some models with a date suffix (claude-haiku-4-5-20251001).
const baseModel = (m) => m.replace(/-\d{8}$/, '');
// claude-opus-5-5 -> "Opus 5.5", the form the README's Model column uses.
const displayName = (m) => {
  const [family, ...ver] = baseModel(m).replace(/^claude-/, '').split('-');
  return `${family[0].toUpperCase()}${family.slice(1)} ${ver.join('.')}`.trim();
};

// A gap longer than this is a break, not work. Wall-clock over a multi-day Authoring
// is meaningless; summed sub-threshold gaps approximate time at the keyboard.
const IDLE_GAP_MS = 5 * 60 * 1000;

const [slug, ...rest] = process.argv.slice(2);
if (!slug) {
  console.error('usage: authoring-cost.mjs <project-slug> --part deck|track|other [--since YYYY-MM-DD|ISO] [--until YYYY-MM-DD|ISO] [--label "..."] [--scope main|subagents|all] [--transcript sub1,sub2]');
  process.exit(1);
}
const arg = (n) => { const i = rest.indexOf(n); return i === -1 ? null : rest[i + 1]; };
const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/;
const parseBound = (name, v) => {
  const t = Date.parse(v);
  if (!Number.isFinite(t)) { console.error(`bad ${name}: ${v}`); process.exit(1); }
  if (!DATE_ONLY.test(v) && !/(Z|[+-]\d{2}:?\d{2})$/.test(v)) {
    console.error(`${name} ${v} has no timezone; add Z or an offset so the window is unambiguous.`);
    process.exit(1);
  }
  return t;
};
const since = arg('--since') ? parseBound('--since', arg('--since')) : 0;
// A bare date in --until includes the whole day it names; a timestamp is exact.
const until = arg('--until')
  ? parseBound('--until', arg('--until')) + (DATE_ONLY.test(arg('--until')) ? 86_400_000 : 0)
  : Infinity;
const part = arg('--part');
if (!['deck', 'track', 'other'].includes(part)) { console.error('--part must be deck, track or other'); process.exit(1); }
const PART = { deck: 'Deck', track: 'Track', other: 'Supporting work' }[part];
const label = arg('--label') ?? slug;
const scope = arg('--scope') ?? 'all';
if (!['main', 'subagents', 'all'].includes(scope)) { console.error(`bad --scope: ${scope}`); process.exit(1); }
const only = arg('--transcript')?.split(',').filter(Boolean) ?? null;

const walk = (d, acc = []) => {
  for (const e of readdirSync(d)) {
    const p = join(d, e);
    if (statSync(p).isDirectory()) walk(p, acc);
    else if (e.endsWith('.jsonl')) acc.push(p);
  }
  return acc;
};

const dir = join(homedir(), '.claude', 'projects', slug);
let files;
try { files = walk(dir); } catch { console.error(`no such project dir: ${dir}`); process.exit(1); }
if (only) files = files.filter((f) => only.some((o) => f.includes(o)));
if (files.length === 0) { console.error(`no transcripts under ${dir}`); process.exit(1); }

const blank = () => ({ models: new Map(), stamps: [], files: new Set() });
const buckets = { main: blank(), subagents: blank() };

for (const f of files) {
  for (const line of readFileSync(f, 'utf8').split('\n')) {
    if (!line.trim()) continue;
    let e; try { e = JSON.parse(line); } catch { continue; }
    const u = e.message?.usage;
    if (e.type !== 'assistant' || !u) continue;
    const t = Date.parse(e.timestamp ?? '');
    if (!Number.isFinite(t) || t < since || t >= until) continue;

    const which = e.isSidechain === true ? 'subagents' : 'main';
    if (scope !== 'all' && scope !== which) continue;
    const b = buckets[which];
    b.stamps.push(t);
    b.files.add(f);

    const model = baseModel(e.message.model ?? 'unknown');
    const c = b.models.get(model) ?? { input: 0, output: 0, thinking: 0, w1h: 0, w5m: 0, read: 0, msgs: 0 };
    c.msgs++;
    c.input    += u.input_tokens ?? 0;
    c.output   += u.output_tokens ?? 0;
    c.thinking += u.output_tokens_details?.thinking_tokens ?? 0;
    c.read     += u.cache_read_input_tokens ?? 0;
    c.w1h      += u.cache_creation?.ephemeral_1h_input_tokens ?? 0;
    c.w5m      += u.cache_creation?.ephemeral_5m_input_tokens ?? 0;
    b.models.set(model, c);
  }
}

const costOf = (model, c) => {
  const r = RATES[model];
  if (!r) return null;
  return (c.input / 1e6) * r.in + (c.output / 1e6) * r.out
    + (c.w1h / 1e6) * r.in * CACHE_1H + (c.w5m / 1e6) * r.in * CACHE_5M
    + (c.read / 1e6) * (r.read ?? r.in * CACHE_READ);
};
const activeMs = (stamps) => {
  const s = [...stamps].sort((a, b) => a - b);
  let a = 0;
  for (let i = 1; i < s.length; i++) { const d = s[i] - s[i - 1]; if (d < IDLE_GAP_MS) a += d; }
  return a;
};
const hrs = (ms) => (ms / 3_600_000).toFixed(1);
const fmt = (n) => n.toLocaleString('en-US');

const summarize = (b) => {
  if (b.stamps.length === 0) return null;
  let cost = 0;
  for (const [m, c] of b.models) {
    const x = costOf(m, c);
    if (x === null) console.error(`warning: no rate for ${m}; its tokens are excluded from the cost. Add it to RATES.`);
    else cost += x;
  }
  const s = [...b.stamps].sort((a, z) => a - z);
  return { cost, active: activeMs(b.stamps), msgs: b.stamps.length, first: s[0], last: s.at(-1), files: b.files.size };
};
const mainS = summarize(buckets.main);
const subS  = summarize(buckets.subagents);
if (!mainS && !subS) { console.error('no assistant messages matched'); process.exit(1); }

const all = [...buckets.main.stamps, ...buckets.subagents.stamps].sort((a, b) => a - b);
const out = [];
out.push(`# ${PART} cost: ${label}`, '');
out.push(`Measured ${new Date(all[0]).toISOString().slice(0, 10)} to ${new Date(all.at(-1)).toISOString().slice(0, 10)}.`, '');

// The README's Model column: every model that spent tokens, heaviest share of
// dollars first. An unpriced model is named, not dropped, so a missing RATES
// entry shows up in the index instead of silently shrinking the total.
const byModel = new Map();
for (const b of [buckets.main, buckets.subagents]) {
  for (const [m, c] of b.models) {
    if (c.input + c.output + c.read + c.w1h + c.w5m === 0) continue;
    const x = costOf(m, c);
    const prev = byModel.get(m);
    byModel.set(m, x === null || prev === null ? null : (prev ?? 0) + x);
  }
}
const pricedTotal = [...byModel.values()].reduce((a, x) => a + (x ?? 0), 0);
const modelCol = [...byModel]
  .sort((a, z) => (z[1] ?? -1) - (a[1] ?? -1))
  .map(([m, x]) => x === null
    ? `${displayName(m)} (unpriced)`
    : `${displayName(m)} (${pricedTotal ? Math.round((x / pricedTotal) * 100) : 0}%)`)
  .join(', ');
out.push(`README model: ${modelCol}`, '');
out.push('| Scope | Cost | Active | Messages | Transcripts |');
out.push('|---|--:|--:|--:|--:|');
const row = (n, s) => s && out.push(`| ${n} | $${s.cost.toFixed(2)} | ${hrs(s.active)} h | ${fmt(s.msgs)} | ${s.files} |`);
row('Main session', mainS);
row('Subagents', subS);
if (mainS && subS) {
  out.push(`| **Total** | **$${(mainS.cost + subS.cost).toFixed(2)}** | — | ${fmt(mainS.msgs + subS.msgs)} | ${mainS.files + subS.files} |`);
}
out.push('', '> Active time is summed gaps under 5 minutes, so an idle session does not inflate it.',
  '> Main and subagent time overlap when they run concurrently — do not add the two.', '');

for (const [name, b] of [['Main session', buckets.main], ['Subagents', buckets.subagents]]) {
  if (b.models.size === 0) continue;
  out.push(`## ${name} — by model`, '');
  out.push('| Model | Msgs | Input | Output | Thinking | Cache write | Cache read | Cost |');
  out.push('|---|--:|--:|--:|--:|--:|--:|--:|');
  for (const [m, c] of [...b.models].sort((a, z) => z[1].output - a[1].output)) {
    const x = costOf(m, c);
    out.push(`| \`${m}\` | ${fmt(c.msgs)} | ${fmt(c.input)} | ${fmt(c.output)} | ${fmt(c.thinking)} `
      + `| ${fmt(c.w1h + c.w5m)} | ${fmt(c.read)} | ${x === null ? 'n/a' : '$' + x.toFixed(2)} |`);
  }
  out.push('');
}
out.push('## Human time', '', '_Not measured. Fill in by hand._', '',
  '| Activity | Hours |', '|---|--:|', '| Review and correction | |',
  ...(part === 'deck' ? ['| Deck rehearsal | |'] : ['| Sandbox / Instruqt debugging | |']), '',
  `> The agent figures above are a **floor**, not the ${PART} cost.`);
console.log(out.join('\n'));
