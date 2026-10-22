import { externalFlow } from '../external-login';
import { GoogleSignin } from '@react-native-google-signin/google-signin';
import { exchangeCode } from '../oauth';

jest.mock('expo-crypto', () => ({ getRandomBytesAsync: async (n: number) => new Uint8Array(n).fill(1),
  digestStringAsync: async () => 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=',
  CryptoDigestAlgorithm: { SHA256: 'SHA256' }, CryptoEncoding: { BASE64: 'base64' } }));
jest.mock('@react-native-google-signin/google-signin', () => ({ GoogleSignin: {
  configure: jest.fn(), signOut: jest.fn(), signIn: jest.fn() },
  isSuccessResponse: (r: any) => r.type === 'success', statusCodes: { SIGN_IN_CANCELLED: 'CANCELLED' } }));
jest.mock('../oauth', () => ({ exchangeCode: jest.fn(async () => ({ access_token: 'product-token', token_type: 'Bearer' })) }));
const config = { origin: 'https://product.example', clientId: 'zenflow-ios', environment: 'local' };
const state = '01'.repeat(24);
const start = { transactionId: 'fixture', nonce: 'server-nonce', state };
beforeEach(() => {
  jest.clearAllMocks();
  process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_IOS_CLIENT_ID = 'ios-fixture';
  process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_WEB_CLIENT_ID = 'web-fixture';
});
test('wrong transaction state stops before provider SDK', async () => {
  globalThis.fetch = jest.fn(async () => ({ ok: true, json: async () => ({ ...start, state: 'wrong' }) })) as any;
  await expect(externalFlow(config, 'google', () => true)).rejects.toThrow('INVALID_AUTH_TRANSACTION');
  expect(GoogleSignin.signIn).not.toHaveBeenCalled();
});
test('Google passes single-use server code, never reusable ID token, then exchanges product code', async () => {
  (GoogleSignin.signIn as jest.Mock).mockResolvedValue({ type: 'success', data: { serverAuthCode: 'one-use-code', idToken: 'must-not-send' } });
  globalThis.fetch = jest.fn().mockResolvedValueOnce({ ok: true, json: async () => start })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ code: 'product-code', state }) });
  await externalFlow(config, 'google', () => true);
  const submitted = JSON.parse((globalThis.fetch as jest.Mock).mock.calls[1][1].body);
  expect(submitted.proof).toBe('one-use-code');
  expect(JSON.stringify(submitted)).not.toContain('must-not-send');
  expect(exchangeCode).toHaveBeenCalledWith(config, 'product-code', expect.any(String));
});
test('provider result after account switch cannot complete server transaction', async () => {
  let current = true;
  (GoogleSignin.signIn as jest.Mock).mockImplementation(async () => {
    current = false; return { type: 'success', data: { serverAuthCode: 'old-code' } };
  });
  globalThis.fetch = jest.fn(async () => ({ ok: true, json: async () => start })) as any;
  expect(await externalFlow(config, 'google', () => current)).toBeNull();
  expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  expect(exchangeCode).not.toHaveBeenCalled();
});
test('cancel creates no product token or account completion', async () => {
  (GoogleSignin.signIn as jest.Mock).mockResolvedValue({ type: 'cancelled' });
  globalThis.fetch = jest.fn(async () => ({ ok: true, json: async () => start })) as any;
  expect(await externalFlow(config, 'google', () => true)).toBeNull();
  expect(globalThis.fetch).toHaveBeenCalledTimes(1);
});
