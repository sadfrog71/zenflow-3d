const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const repo = path.resolve(__dirname, '../../..');
const root = path.join(repo, 'docs/prototypes/reading-room');
const esbuild = require(path.join(repo, 'zenflow-admin/node_modules/esbuild'));
const assets = {};
const mime = { '.webp': 'image/webp', '.jpg': 'image/jpeg', '.gltf': 'model/gltf+json', '.bin': 'application/octet-stream' };
function embed(directory) {
  for (const entry of fs.readdirSync(path.join(root, directory), { withFileTypes: true })) {
    const relative = `${directory}/${entry.name}`;
    if (entry.isDirectory()) embed(relative);
    else {
      const type = mime[path.extname(relative)];
      if (!type) continue;
      assets[`/reading-room/${relative}`] = `data:${type};base64,${fs.readFileSync(path.join(root, relative)).toString('base64')}`;
    }
  }
}
embed('assets/optimized');
embed('assets/models/ceramic_vase_01');
let scene = fs.readFileSync(path.join(root, 'scene.js'), 'utf8');
const modifier = scene.split('\n').find(line => line.includes('manager.setURLModifier('));
if (!modifier) throw new Error('Reading-room asset resolver changed; update native bundler.');
scene = scene.replace(modifier, ` manager.setURLModifier(url=>{if(/^(data|blob):/.test(url))return url;const key=new URL(url,assetRoot).pathname;const asset=window.InvelleRoomAssets[key];if(!asset)throw new Error('ROOM_ASSET_NOT_BUNDLED:'+key);return asset;});`);
const common = { bundle: true, write: false, format: 'iife', target: 'safari16', minify: true, legalComments: 'inline', alias: { three: path.join(__dirname, 'three.module.min.js') } };
const sceneCode = esbuild.buildSync({ ...common, globalName: 'InvelleRoomScene', define: { 'import.meta.url': JSON.stringify('https://invelle.local/reading-room/scene.js') }, stdin: { contents: scene, resolveDir: root, loader: 'js' } }).outputFiles[0].text;
let experience = fs.readFileSync(path.join(root, 'experience/experience.js'), 'utf8');
const sceneImport = /await import\('\.\.\/scene\.js\?v=[^']+'\)/;
if (!sceneImport.test(experience)) throw new Error('Reading-room scene entry changed; update native bundler.');
experience = experience.replace(sceneImport, 'window.InvelleRoomScene')
  .replace("const entryParams=new URLSearchParams(location.search);", "const entryParams=new URLSearchParams('native=1&lang='+window.InvelleRoomLanguage);");
const experienceCode = esbuild.buildSync({ ...common, stdin: { contents: experience, resolveDir: path.join(root, 'experience'), loader: 'js' } }).outputFiles[0].text;
const script = code => `<script>${code.replace(/<\/script/gi, '<\\/script')}</script>`;
const bridge = fs.readFileSync(path.join(root, 'experience/native-navigation.js'), 'utf8').replace("if (params.get('native') !== '1') return;", 'if (!window.InvelleRoomAssets) return;');
let html = fs.readFileSync(path.join(root, 'experience/index.html'), 'utf8')
  .replace(/<link rel="stylesheet"[^>]*>/, `<style>${fs.readFileSync(path.join(root, 'experience/experience.css'), 'utf8')}</style>`)
  .replace(/<script type="importmap">[\s\S]*?<\/script>/, '')
  .replace(/<script src="native-navigation\.js[^>]*><\/script>/, '')
  .replace(/<script type="module"[^>]*><\/script>/, () => script(`window.InvelleRoomLanguage='__INVELLE_ROOM_LANGUAGE__';window.InvelleRoomAssets=${JSON.stringify(assets)};`) + script(bridge) + script(sceneCode) + script(experienceCode));
const revision = crypto.createHash('sha256').update(html).digest('hex').slice(0, 16);
const output = path.join(repo, 'zenflow-mobile/src/features/zenflow/reading-room-source.json');
fs.writeFileSync(output, JSON.stringify({ html, revision }) + '\n');
console.log(`Built local reading room ${revision}: ${Object.keys(assets).length} embedded assets, ${Buffer.byteLength(html)} HTML bytes`);
