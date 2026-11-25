// Resumen por navegador a partir de reports/playwright-results.json: total, pasaron, fallaron,
// omitidas (@fixme) y tiempo (suma de duraciones; con un worker equivale al tiempo de ejecución).
// Uso: node support/resumen.mjs [ruta.json]
import fs from 'node:fs';

const file = process.argv[2] ?? 'reports/playwright-results.json';
const report = JSON.parse(fs.readFileSync(file, 'utf-8'));
const byProject = {};
const rows = [];

const walk = (suite) => {
  for (const spec of suite.specs ?? []) {
    for (const t of spec.tests) {
      const p = (byProject[t.projectName] ??= { total: 0, pasaron: 0, fallaron: 0, omitidas: 0, ms: 0 });
      const ms = t.results.reduce((a, r) => a + (r.duration ?? 0), 0);
      p.total++;
      p.ms += ms;
      if (t.status === 'skipped') p.omitidas++;
      else if (t.status === 'expected') p.pasaron++;
      else p.fallaron++;
      const id = (spec.title.match(/@E2E-\d+/) ?? spec.tags?.map((x) => `@${x}`).filter((x) => /E2E-/.test(x)) ?? ['?'])[0];
      rows.push({ id: (spec.tags ?? []).find((x) => /E2E-/.test(x)) ?? id, project: t.projectName, status: t.status === 'expected' ? 'pasó' : t.status === 'skipped' ? 'omitida (fixme)' : 'falló', s: (ms / 1000).toFixed(1) });
    }
  }
  (suite.suites ?? []).forEach(walk);
};
report.suites.forEach(walk);

const mmss = (ms) => `${String(Math.floor(ms / 60000)).padStart(2, '0')}:${String(Math.round((ms % 60000) / 1000)).padStart(2, '0')}`;
console.log('Navegador | Total | Pasaron | Fallaron | Omitidas (@fixme) | Tiempo');
for (const [name, p] of Object.entries(byProject)) {
  console.log(`${name} | ${p.total} | ${p.pasaron} | ${p.fallaron} | ${p.omitidas} | ${mmss(p.ms)}`);
}
console.log(`\nEjecución completa: ${mmss(report.stats.duration)} (inicio ${report.stats.startTime})`);
console.log('\nID | Navegador | Resultado | s');
for (const r of rows.sort((a, b) => a.id.localeCompare(b.id) || a.project.localeCompare(b.project))) {
  console.log(`${r.id} | ${r.project} | ${r.status} | ${r.s}`);
}
