import { zenflowSecureSession } from '../secure-session.web';
const values = new Map<string, string>(), temporary = new Map<string, string>();
const store = (map: Map<string, string>) => ({ getItem: (k: string) => map.get(k) ?? null, setItem: (k: string, v: string) => map.set(k,v), removeItem: (k: string) => map.delete(k) });
beforeEach(() => {
  values.clear(); temporary.clear();
  (globalThis as any).window = { localStorage: store(values), sessionStorage: store(temporary) };
});
test('a new window after restart preserves renewal credentials and logout removes them', async () => {
  const session = { refreshToken: 'test-refresh', scope: 'local:8:APP_USER:42', origin: 'https://example.com' };
  await zenflowSecureSession.save({ ...session, accessToken: 'must-not-persist' } as any);
  temporary.clear();
  (globalThis as any).window = { localStorage: store(values), sessionStorage: store(temporary) };
  jest.resetModules();
  const reloaded = require('../secure-session.web').zenflowSecureSession;
  expect(await reloaded.load()).toEqual(session);
  expect([...values.values()].join('')).not.toContain('must-not-persist');
  await reloaded.clear();
  expect(await zenflowSecureSession.load()).toBeNull();
});
test.each(['null', '{', '{"refreshToken":12}'])('invalid session discarded: %s', async raw => {
  values.set('invelle.web.identity.v1', raw);
  expect(await zenflowSecureSession.load()).toBeNull();
  expect(values.size).toBe(0);
});

test('existing tab credentials migrate once into persistent storage', async () => {
 const session = {refreshToken:'legacy',scope:'web:8:APP_USER:42',origin:'https://example.com'};
 temporary.set('invelle.web.identity.v1',JSON.stringify(session));
 expect(await zenflowSecureSession.load()).toEqual(session);
 expect(temporary.size).toBe(0); expect(values.size).toBe(1);
 await zenflowSecureSession.clear(); expect(await zenflowSecureSession.load()).toBeNull();
});
test('persistent credentials take priority over a stale tab copy', async () => {
 const session = {refreshToken:'new',scope:'web:8:APP_USER:42',origin:'https://example.com'};
 values.set('invelle.web.identity.v1',JSON.stringify(session));
 temporary.set('invelle.web.identity.v1',JSON.stringify({...session,refreshToken:'old'}));
 expect(await zenflowSecureSession.load()).toEqual(session); expect(temporary.size).toBe(0);
});
