const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const repo = path.resolve(__dirname, '../../../');
const source = path.join(repo, 'docs/prototypes/reading-room');
// Reuse the repository's Vite/esbuild toolchain; no runtime dependency is added.
const esbuild = require(require.resolve('esbuild', { paths: [path.join(repo, 'zenflow-admin')] }));
const dist = path.resolve(process.argv[2] || path.join(repo, 'zenflow-mobile/dist'));
const destination = path.join(dist, 'reading-room');
const digest = crypto.createHash('sha256');
const assetDigest = crypto.createHash('sha256');
digest.update(fs.readFileSync(__filename));
digest.update(esbuild.version);

function copyFile(relative, target = relative) {
  const from = path.join(source, relative);
  const to = path.join(destination, target);
  fs.mkdirSync(path.dirname(to), { recursive: true });
  digest.update(relative).update(fs.readFileSync(from));
  if (/\.(webp|bin|gltf|jpg|png)$/.test(relative)) assetDigest.update(relative).update(fs.readFileSync(from));
  fs.copyFileSync(from, to);
}

function copyTree(relative) {
  const from = path.join(source, relative);
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    const child = path.join(relative, entry.name);
    if (entry.isDirectory()) copyTree(child);
    else copyFile(child);
  }
}

fs.rmSync(destination, { recursive: true, force: true });

for (const file of [
  'experience/index.html',
  'experience/experience.css',
  'experience/experience.js',
  'experience/native-navigation.js',
  'experience/classic-readings.js',
  'experience/paragraph-notes.js',
  'books.js',
  'scene.js',
  'vendor/GLTFLoader.js',
  'vendor/BufferGeometryUtils.js',
  'assets/white_plaster_rough_01-normal.jpg',
  'assets/rock_wall-color.jpg',
  'assets/rock_wall-normal.jpg',
]) copyFile(file);

for (const directory of [
  'assets/white-oak',
  'assets/gravelly_sand',
  'assets/mossy_rock',
  'assets/wooden_floor_01',
  'assets/models',
  'assets/optimized',
]) copyTree(directory);

const mobileAssets = path.join(repo, 'zenflow-mobile');
for (const [from, to] of [
  ['scripts/zenflow/three.module.min.js', 'zenflow-mobile/scripts/zenflow/three.module.min.js'],
  ['scripts/zenflow/three.core.min.js', 'zenflow-mobile/scripts/zenflow/three.core.min.js'],
  ['scripts/zenflow/quiet-art.js', 'zenflow-mobile/scripts/zenflow/quiet-art.js'],
  ['scripts/zenflow/vendor/Reflector.js', 'zenflow-mobile/scripts/zenflow/vendor/Reflector.js'],
  ['scripts/zenflow/vendor/THREE-LICENSE.txt', 'zenflow-mobile/scripts/zenflow/vendor/THREE-LICENSE.txt'],
  ['assets/zenflow/garden/assets/cinematic-lake-20261015.png', 'zenflow-mobile/assets/zenflow/garden/assets/cinematic-lake-20261015.png'],
]) {
  const input = path.join(mobileAssets, from);
  const output = path.join(dist, to);
  fs.mkdirSync(path.dirname(output), { recursive: true });
  let content = fs.readFileSync(input);
  // Explicit local resolution also works in embedded browsers without import-map setup.
  if (from.endsWith('/Reflector.js')) content = Buffer.from(content.toString('utf8').replace("from 'three'", "from '../three.module.min.js'"));
  digest.update(to).update(content);
  fs.writeFileSync(output, content);
}

const art = path.join(repo, 'docs/prototypes/three-scenes-art/reading-japanese-v2.png');
const artOutput = path.join(dist, 'three-scenes-art/reading-japanese-v2.png');
fs.mkdirSync(path.dirname(artOutput), { recursive: true });
digest.update('three-scenes-art/reading-japanese-v2.png').update(fs.readFileSync(art));
fs.copyFileSync(art, artOutput);

const version = digest.digest('hex').slice(0, 16);
const assetVersion = assetDigest.digest('hex').slice(0, 16);
const htmlPath = path.join(destination, 'experience/index.html');
let html = fs.readFileSync(htmlPath, 'utf8')
  .replace('../../three-scenes-art/live/', '/')
  .replace(/experience\.css\?v=[^"']+/, `experience.css?v=${version}`)
  .replace(/native-navigation\.js\?v=[^"']+/, `native-navigation.js?v=${version}`)
  .replace(/experience\.js\?v=[^"']+/, `experience.js?v=${version}`);
fs.writeFileSync(htmlPath, html);

const experiencePath = path.join(destination, 'experience/experience.js');
let experience = fs.readFileSync(experiencePath, 'utf8')
  .replace(/\.\.\/scene\.js\?v=[^'"\s]+/, `../scene.js?v=${version}`);
fs.writeFileSync(experiencePath, experience);

// Version every module edge so warm entry does not revalidate the engine tree.
const modules = [
  'reading-room/experience/experience.js', 'reading-room/books.js',
  'reading-room/experience/classic-readings.js', 'reading-room/experience/paragraph-notes.js',
  'reading-room/scene.js', 'reading-room/vendor/GLTFLoader.js', 'reading-room/vendor/BufferGeometryUtils.js',
  'zenflow-mobile/scripts/zenflow/three.module.min.js', 'zenflow-mobile/scripts/zenflow/three.core.min.js',
  'zenflow-mobile/scripts/zenflow/quiet-art.js', 'zenflow-mobile/scripts/zenflow/vendor/Reflector.js',
];
for (const relative of modules) {
  const file = path.join(dist, relative);
  let code = fs.readFileSync(file, 'utf8').replace(
    /(\bfrom\s*|\bimport\s*\(\s*)(['"])([./][^'"]+?\.js)(?:\?[^'"]*)?\2/g,
    (_, prefix, quote, url) => {
      const resolved = new URL(url, `https://invelle.invalid/${relative}`).pathname;
      let local = path.relative(path.dirname(file), path.join(dist, resolved));
      if (!local.startsWith('.')) local = './' + local;
      return `${prefix}${quote}${local}?v=${version}${quote}`;
    },
  );
  if (relative === 'reading-room/scene.js') code = code.replace(
    "const sceneVersion=new URL(import.meta.url).searchParams.get('v');",
    `const sceneVersion='${assetVersion}';`,
  );
  fs.writeFileSync(file, code);
}
// Ship two self-contained modules, avoiding the embedded browser's dependency waterfall.
for (const relative of ['reading-room/scene.js', 'reading-room/experience/experience.js']) {
  const file = path.join(dist, relative);
  const result = esbuild.buildSync({
    entryPoints: [file], bundle: true, format: 'esm', target: 'safari16',
    minify: true, write: false, legalComments: 'inline',
    external: relative.endsWith('/experience.js') ? ['../scene.js*'] : [],
  });
  fs.writeFileSync(file, result.outputFiles[0].contents);
}
html = html.replace(/<script type="importmap">[\s\S]*?<\/script>/, '');
html = html.replace('</head>', `<link rel="modulepreload" href="../scene.js?v=${version}" crossorigin></head>`);
fs.writeFileSync(htmlPath, html);

console.log(`Built reading room static assets: ${version}; textures: ${assetVersion}`);
