import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
const source = readFileSync(new URL('../../public/sw.js', import.meta.url),'utf8');
function worker(fetch) {
  const handlers = {};
  vm.runInNewContext(source, { self: {location:{origin:'https://invelleai.com'},addEventListener:(name,fn)=>handlers[name]=fn},URL,Response,fetch });
  return handlers.fetch;
}
test('API, admin, callbacks, private writes and third party requests are never intercepted', () => {
  const handle = worker(() => {throw Error('must not fetch')});
  for (const [url,method,mode] of [
    ['/api/zenflow/v1/notes','GET','cors'], ['/api/zenflow/v1/notes','POST','cors'],
    ['/manage/','GET','navigate'], ['/auth/callback?code=private','GET','navigate'],
    ['https://accounts.google.com/','GET','navigate'], ['/?code=private','POST','navigate'],
  ]) {
    handle({request:{url:new URL(url,'https://invelleai.com').href,method,mode},respondWith:()=>assert.fail('intercepted private or unrelated request')});
  }
});
test('offline root navigation shows a reconnect page, without caching private data', async () => {
  let response;
  worker(async()=>{throw Error('offline')})({request:{url:'https://invelleai.com/',method:'GET',mode:'navigate'},respondWith:p=>response=p});
  const result=await response;
  assert.equal(result.headers.get('cache-control'),'no-store');
  assert.match(await result.text(),/重新连接/);
});
test('online app navigation passes the server response through', async () => {
  const result = new Response('fresh app');let response;
  worker(async()=>result)({request:{url:'https://invelleai.com/',method:'GET',mode:'navigate'},respondWith:p=>response=p});
  assert.equal(await response,result);
});
