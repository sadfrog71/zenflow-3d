import { prepareGoogleLogin } from '../external-login.web';
import { exchangeCode } from '../oauth';
jest.mock('expo-crypto', () => ({ getRandomBytesAsync: async (n: number) => new Uint8Array(n).fill(1),
  digestStringAsync: async () => 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=',
  CryptoDigestAlgorithm: { SHA256: 'SHA256' }, CryptoEncoding: { BASE64: 'base64' } }));
jest.mock('../oauth', () => ({ exchangeCode: jest.fn(async () => ({ access_token: 'product-token', token_type: 'Bearer' })) }));
const config = { origin: 'https://product.example', clientId: 'zenflow-ios', environment: 'web' };
const state = '01'.repeat(24);
const start = { transactionId: 'fixture', nonce: 'server-nonce', state };
beforeEach(() => { jest.clearAllMocks(); process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_WEB_CLIENT_ID = 'web-fixture'; });
test('browser login binds server nonce and consumes a credential only once', async () => {
  globalThis.fetch = jest.fn().mockResolvedValueOnce({ ok: true, json: async () => start })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ code: 'product-code', state }) });
  const transaction = await prepareGoogleLogin(config);
  expect(transaction.nonce).toBe('server-nonce');
  expect(JSON.parse((fetch as jest.Mock).mock.calls[0][1].body)).toMatchObject({ channel: 'WEB', provider: 'google', intent: 'LOGIN' });
  await transaction.complete('nonce-bound-google-credential', () => true);
  expect(await transaction.complete('duplicate-credential', () => true)).toBeNull();
  expect(exchangeCode).toHaveBeenCalledTimes(1);
  expect(exchangeCode).toHaveBeenCalledWith(config, 'product-code', expect.any(String));
});
test('late Google credential after cancellation or account change never completes the transaction', async () => {
  globalThis.fetch = jest.fn(async () => ({ ok: true, json: async () => start })) as any;
  const transaction = await prepareGoogleLogin(config);
  expect(await transaction.complete('late-credential', () => false)).toBeNull();
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(exchangeCode).not.toHaveBeenCalled();
});
test('wrong returned state never exchanges a product token', async () => {
  globalThis.fetch = jest.fn().mockResolvedValueOnce({ ok: true, json: async () => start })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ code: 'product-code', state: 'wrong' }) });
  const transaction = await prepareGoogleLogin(config);
  await expect(transaction.complete('credential', () => true)).rejects.toThrow('INVALID_AUTH_TRANSACTION');
  expect(exchangeCode).not.toHaveBeenCalled();
});
