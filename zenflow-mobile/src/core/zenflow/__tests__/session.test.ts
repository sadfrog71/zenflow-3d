import { ZenflowSession } from '../session';

const config = { origin: 'https://zenflow.example', clientId: 'zenflow-ios', environment: 'local' };
const account = { userId: '42', tenantId: '8', subjectType: 'APP_USER' as const,
  publicId: 'p', email: 'a@example.com', locale: 'zh-CN' as const, memoryEnabled: false, version: 1 };

test('an OAuth result arriving after account switch cannot restore the old identity', async () => {
  let completeLogin!: (value: any) => void;
  let loginStarted!: () => void;
  const started = new Promise<void>(resolve => { loginStarted = resolve; });
  const browser = jest.fn(() => new Promise<any>(resolve => { completeLogin = resolve; loginStarted(); }));
  const storage = { load: jest.fn(async () => null), save: jest.fn(async () => {}), clear: jest.fn(async () => {}) };
  const session = new ZenflowSession(config, storage, browser);
  const first = session.signIn();
  await started;
  await session.signOut();
  completeLogin({ access_token: 'old', refresh_token: 'old-refresh', token_type: 'Bearer', expires_in: 3600 });
  await first;
  expect(session.snapshot().profile).toBeNull();
  expect(storage.save).not.toHaveBeenCalled();
});

test('canceling account switch leaves no prior refresh token on device', async () => {
  const storage = { load: jest.fn(async () => null), save: jest.fn(async () => {}), clear: jest.fn(async () => {}) };
  const session = new ZenflowSession(config, storage, async () => null);
  await session.signIn();
  expect(storage.clear).toHaveBeenCalledTimes(1);
  expect(session.snapshot().profile).toBeNull();
});

test('restored refresh token is rejected when it resolves to another subject', async () => {
  const storage = { load: jest.fn(async () => ({ refreshToken: 'r', origin: config.origin, scope: 'local:8:APP_USER:99' })),
    save: jest.fn(async () => {}), clear: jest.fn(async () => {}) };
  globalThis.fetch = jest.fn(async () => ({ ok: true, json: async () => account })) as any;
  const session = new ZenflowSession(config, storage, async () => null,
    async () => ({ access_token: 'a', refresh_token: 'r2', token_type: 'Bearer', expires_in: 3600 }));
  await session.restore();
  expect(session.snapshot().profile).toBeNull();
  expect(storage.clear).toHaveBeenCalled();
});

 test('signing out during a secure save clears the saved identity after the pending write', async () => {
  let release!: () => void; let saving!: () => void;
  const started = new Promise<void>(resolve => { saving = resolve; });
  const writes: string[] = [];
  const storage = { load: jest.fn(async () => null),
    save: jest.fn(async () => { saving(); await new Promise<void>(resolve => { release = resolve; }); writes.push('save'); }),
    clear: jest.fn(async () => { writes.push('clear'); }) };
  globalThis.fetch = jest.fn(async () => ({ ok: true, json: async () => account })) as any;
  const session = new ZenflowSession(config, storage, async () => ({ access_token: 'a', refresh_token: 'r', token_type: 'Bearer', expires_in: 3600 }));
  const login = session.signIn(); await started;
  const logout = session.signOut(); release(); await Promise.all([login, logout]);
  expect(writes).toEqual(['clear', 'save', 'clear']);
  expect(session.snapshot().profile).toBeNull();
});

test('reload renews credentials and restores the same verified account', async () => {
  const storage = { load: jest.fn(async () => ({ refreshToken: 'r', origin: config.origin, scope: 'local:8:APP_USER:42' })),
    save: jest.fn(async (_value: unknown) => {}), clear: jest.fn(async () => {}) };
  globalThis.fetch = jest.fn(async () => ({ ok: true, json: async () => account })) as any;
  const refresh = jest.fn(async () => ({ access_token: 'new-access', refresh_token: 'rotated-refresh', token_type: 'Bearer', expires_in: 3600 }));
  const session = new ZenflowSession(config, storage, async () => null, refresh);
  await session.restore();
  expect(session.snapshot().profile).toEqual(account);
  expect(refresh).toHaveBeenCalledWith(config, 'r');
  expect(storage.save).toHaveBeenCalledWith({ refreshToken: 'rotated-refresh', scope: 'local:8:APP_USER:42', origin: config.origin });
});

test('a transient startup failure preserves credentials for a later restore', async () => {
 const saved = {refreshToken:'r',origin:config.origin,scope:'local:8:APP_USER:42'};
 const storage = {load:jest.fn(async()=>saved), save:jest.fn(async()=>{}),clear:jest.fn(async()=>{})};
 const refresh = jest.fn().mockRejectedValueOnce(new TypeError('Network unavailable')).mockResolvedValueOnce({access_token:'a',refresh_token:'r2',token_type:'Bearer',expires_in:3600});
 const session = new ZenflowSession(config,storage,async()=>null,refresh);
 await session.restore(); expect(storage.clear).not.toHaveBeenCalled(); expect(session.snapshot().profile).toBeNull();
 globalThis.fetch = jest.fn(async()=>({ok:true,json:async()=>account})) as any;
 await session.restore(); expect(session.snapshot().profile).toEqual(account);
});
test('an expired refresh credential is removed rather than retried forever', async () => {
 const storage = {load:jest.fn(async()=>({refreshToken:'r',origin:config.origin,scope:'local:8:APP_USER:42'})), save:jest.fn(async()=>{}),clear:jest.fn(async()=>{})};
 const session = new ZenflowSession(config,storage,async()=>null,async()=>{throw new Error('OAUTH_SESSION_EXPIRED')});
 await session.restore(); expect(storage.clear).toHaveBeenCalledTimes(1);
});
test('rotated credentials survive a temporary profile request failure', async () => {
 let saved = {refreshToken:'r',origin:config.origin,scope:'local:8:APP_USER:42'};
 const storage = {load:async()=>saved,save:async(v:typeof saved)=>{saved=v},clear:jest.fn(async()=>{})};
 const refresh = jest.fn(async()=>({access_token:'a',refresh_token:'r2',token_type:'Bearer',expires_in:3600}));
 globalThis.fetch=jest.fn(async()=>{throw new TypeError('offline')}) as any;
 await new ZenflowSession(config,storage,async()=>null,refresh).restore();
 expect(saved.refreshToken).toBe('r2'); expect(storage.clear).not.toHaveBeenCalled();
});
test('logout in another window invalidates the active private identity', async () => {
 let saved: any = {refreshToken:'r',origin:config.origin,scope:'local:8:APP_USER:42'}, listener!:()=>void;
 const storage = {load:async()=>saved,save:async(v:unknown)=>{saved=v},clear:async()=>{saved=null},subscribe:(fn:()=>void)=>{listener=fn;return ()=>{}}};
 globalThis.fetch=jest.fn(async()=>({ok:true,json:async()=>account})) as any;
 const session=new ZenflowSession(config,storage,async()=>null,async()=>({access_token:'a',refresh_token:'r2',token_type:'Bearer',expires_in:3600}));
 session.watchStoredIdentity(); await session.restore(); expect(session.snapshot().profile).toEqual(account);
 saved=null; listener(); await Promise.resolve(); await Promise.resolve(); expect(session.snapshot().profile).toBeNull();
});

test('two app windows serialize refresh rotation and use the latest saved credential', async () => {
 let saved = {refreshToken:'r0',origin:config.origin,scope:'local:8:APP_USER:42'};
 let lock = Promise.resolve();
 const storage = {load:async()=>saved,save:async(v:typeof saved)=>{saved=v},clear:async()=>{},
  withLock:<T,>(action:()=>Promise<T>):Promise<T>=>{const result=lock.then(action);lock=result.then(()=>{},()=>{});return result}};
 let current='r0', sequence=0;
 const refresh=jest.fn(async (_config:unknown,token:string)=>{expect(token).toBe(current);await Promise.resolve();current='r'+(++sequence);return {access_token:'a',refresh_token:current,token_type:'Bearer',expires_in:3600}});
 globalThis.fetch=jest.fn(async()=>({ok:true,json:async()=>account})) as any;
 const first=new ZenflowSession(config,storage,async()=>null,refresh), second=new ZenflowSession(config,storage,async()=>null,refresh);
 await Promise.all([first.restore(),second.restore()]);
 expect(refresh.mock.calls.map(x=>x[1])).toEqual(['r0','r1']);
 expect(first.snapshot().profile).toEqual(account);expect(second.snapshot().profile).toEqual(account);
});
