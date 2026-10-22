import { readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const cwd = fileURLToPath(new URL('../../', import.meta.url));
const defaults = Object.fromEntries(readFileSync(new URL('../../.env.web.example', import.meta.url), 'utf8')
  .split('\n').filter(line => /^EXPO_PUBLIC_[A-Z_]+=/.test(line)).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const preview = process.argv.includes('--preview');
const env = { ...defaults, ...process.env, ZENFLOW_BUILD_TARGET: 'web', EXPO_PUBLIC_ZENFLOW_WEB_PREVIEW: String(preview) };
// Build bundled rest scenes from source before exporting either Web target.
for (const script of ['build-home.cjs', 'build-garden.cjs', 'build-pond.cjs']) {
  const resource = spawnSync(process.execPath, [`scripts/zenflow/${script}`], { cwd, env, stdio: 'inherit' });
  if (resource.error) throw resource.error;
  if (resource.status !== 0) process.exit(resource.status ?? 1);
}
const result = spawnSync(process.execPath, ['node_modules/expo/bin/cli', 'export', '--platform', 'web', '--clear', '--output-dir', preview ? 'dist-web-preview' : 'dist'], { cwd, env, stdio: 'inherit' });
if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status ?? 1);
const output = new URL(preview ? '../../dist-web-preview/index.html' : '../../dist/index.html', import.meta.url);
const html = readFileSync(output, 'utf8');
writeFileSync(output, html.replace('</head>', `<link rel="manifest" href="/manifest.json" />
<meta name="theme-color" content="#f5f4ed" />
<link rel="icon" type="image/png" href="/icons/icon-192.png" />
<link rel="apple-touch-icon" href="/icons/icon-192.png" />
<script src="/install.js" defer></script>
</head>`));
const readingRoom = spawnSync(process.execPath, ['scripts/zenflow/build-reading-room.cjs', preview ? 'dist-web-preview' : 'dist'], { cwd, env, stdio: 'inherit' });
if (readingRoom.error) throw readingRoom.error;
if (readingRoom.status !== 0) process.exit(readingRoom.status ?? 1);
