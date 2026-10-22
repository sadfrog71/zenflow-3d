import http from 'node:http';
import https from 'node:https';
import { createReadStream, statSync } from 'node:fs';
import { resolve, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../../dist-web-preview/', import.meta.url));
const upstream = new URL(process.env.EXPO_PUBLIC_ZENFLOW_API_URL || 'https://invelleai.com');
if (upstream.protocol !== 'https:' || upstream.username || upstream.password || upstream.pathname !== '/') throw new Error('Expected product HTTPS origin');
const types = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.ttf': 'font/ttf' };
http.createServer((req, res) => {
  if (req.url.startsWith('/api/')) {
    const proxy = https.request(new URL(req.url, upstream), { method: req.method, headers: { ...req.headers, host: upstream.host } }, response => {
      res.writeHead(response.statusCode, response.headers); response.pipe(res);
    });
    proxy.setTimeout(65000, () => proxy.destroy());
    proxy.on('error', () => { if (!res.headersSent) res.writeHead(502, { 'content-type': 'application/json' }); res.end('{"errorCode":"PREVIEW_UPSTREAM_UNAVAILABLE"}'); });
    req.pipe(proxy); return;
  }
  try {
    const path = resolve(root, '.' + decodeURIComponent(new URL(req.url, 'http://localhost').pathname));
    if (path !== root.replace(/\/$/, '') && !path.startsWith(root.endsWith(sep) ? root : root + sep)) { res.writeHead(403); res.end(); return; }
    let file = path;
    try { if (!statSync(file).isFile()) file = resolve(root, 'index.html'); }
    catch { if (extname(path)) { res.writeHead(404); res.end(); return; } file = resolve(root, 'index.html'); }
    res.writeHead(200, { 'content-type': types[extname(file)] || 'application/octet-stream', 'cache-control': 'no-store' });
    createReadStream(file).pipe(res);
  } catch { res.writeHead(400); res.end(); }
}).listen(8792, '127.0.0.1', () => console.log('Invelle Web preview: http://127.0.0.1:8792'));
