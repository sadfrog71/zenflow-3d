// No cache storage: private requests and authentication remain network-only.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  // Never intercept APIs, account callbacks, admin pages or third-party requests.
  if (request.method !== 'GET' || request.mode !== 'navigate' || url.origin !== self.location.origin || url.pathname !== '/') return;
  event.respondWith(fetch(request).catch(() => new Response(`<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Invelle · Offline</title><style>body{background:#f5f4ed;color:#355a45;font:18px/1.8 system-ui;margin:0;padding:12vh 8vw}main{max-width:520px;margin:auto}h1{font:36px Georgia}a{color:inherit;display:inline-block;border:1px solid #a7b59c;border-radius:20px;padding:8px 22px;text-decoration:none}</style>
<main><h1>Invelle 此间</h1><p>网络暂时断开，连接恢复后再回来。<br>You're offline. Reconnect to continue.</p><a href="/">重新连接 · Try again</a></main></html>`, {status: 200, headers: {'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'}})));
});
