import { CoreApi, EventFrames } from '../core';
import { ZenflowSession } from '../session';
const config = { origin: 'https://invelle.example', clientId: 'zenflow-ios', environment: 'local' };
const profile = { userId: '42', tenantId: '1', subjectType: 'APP_USER' as const, publicId: 'public', email: 'owner@example.com', locale: 'en-US' as const, memoryEnabled: false, version: 1 };
const token = { access_token: 'access', refresh_token: 'refresh', token_type: 'Bearer', expires_in: 3600 };
function storage() { return { load: jest.fn(async () => null), save: jest.fn(async () => {}), clear: jest.fn(async () => {}) }; }
function response(value: unknown, status = 200) { return { ok: status < 400, status, json: async () => value }; }
afterEach(() => jest.restoreAllMocks());
test('fragmented SSE including CRLF boundaries and heartbeat reassembles literal private text', () => {
  const frames = new EventFrames();
  expect(frames.push(':heartbeat\r')).toEqual([]);expect(frames.push('\n\r')).toEqual([]);expect(frames.push('\n')).toEqual([]);
  expect(frames.push('data: {"runId":"r","seq":"1","type":"RESPONSE_DELTA","data":{"delta":"感受\\n空隙"}}\r')).toEqual([]);
  expect(frames.push('\n\r\n')).toEqual([{ runId: 'r', seq: 1, type: 'RESPONSE_DELTA', data: { delta: '感受\n空隙' } }]);
});
test('same run event ordering is checked and replay duplicates ignored', async () => {
  const session = { stream: jest.fn(async (_: string, receive: (text: string) => void) => { receive('data: {"runId":"r","seq":1,"type":"RUN_ACCEPTED","data":{}}\n\ndata: {"runId":"r","seq":1,"type":"RUN_ACCEPTED","data":{}}\n\n');receive('data: {"runId":"r","seq":3,"type":"RUN_FAILED","data":{}}\n\n'); }) };
  const api = new CoreApi(session as unknown as ZenflowSession); const receive = jest.fn();
  await expect(api.events('r', 0, new AbortController().signal, receive)).rejects.toThrow('STREAM_GAP');expect(receive).toHaveBeenCalledTimes(1);
});
test('private core response arriving after logout is discarded', async () => {
  const session = new ZenflowSession(config, storage(), async () => token); globalThis.fetch = jest.fn(async () => response(profile)) as any;await session.signIn();
  let finish!: (value: any) => void;
  globalThis.fetch = jest.fn((url: string) => url.endsWith('/notes') ? new Promise(resolve => { finish = resolve; }) : Promise.resolve(response({}))) as any;
  const result = session.request('notes');await session.signOut();finish(response({ code: 0, data: { items: [{ text: 'old private body' }] } }));await expect(result).rejects.toThrow('STALE_IDENTITY');
});
test('concurrent expired core requests share one refresh and preserve product identity', async () => {
  const refresh = jest.fn(async () => ({ ...token, access_token: 'new', refresh_token: 'rotated' })); const session = new ZenflowSession(config, storage(), async () => token, refresh);
  globalThis.fetch = jest.fn(async () => response(profile)) as any;await session.signIn();
  globalThis.fetch = jest.fn(async (url: string, options: any) => url.endsWith('/profile') ? response(profile) : options.headers.Authorization === 'Bearer new' ? response({ code: 0, data: { items: [] } }) : response({}, 401)) as any;
  await expect(Promise.all([session.request('notes'),session.request('conversations')])).resolves.toEqual([{ items: [] }, { items: [] }]);expect(refresh).toHaveBeenCalledTimes(1);
});
test('explicit server error code is kept and an envelope is not shown as successful data', async () => {
  const session = new ZenflowSession(config, storage(), async () => token); globalThis.fetch = jest.fn(async () => response(profile)) as any;await session.signIn();
  globalThis.fetch = jest.fn(async () => response({ code: 1, data: { errorCode: 'CONVERSATION_BUSY' } },409)) as any;await expect(session.request('conversations/id/messages','POST',{})).rejects.toThrow('CONVERSATION_BUSY');
});
