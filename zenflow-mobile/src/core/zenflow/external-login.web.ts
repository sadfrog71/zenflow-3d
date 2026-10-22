import * as Crypto from 'expo-crypto';
import type { ZenflowConfig } from './config';
import { productUrl } from './config';
import { exchangeCode } from './oauth';
import type { ZenflowTokenPair } from '@/contracts/zenflow/identity';
export type ExternalProvider = 'google' | 'facebook' | 'apple';
export function externalConfigured(provider: ExternalProvider): boolean {
  return provider === 'google' && !!process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_WEB_CLIENT_ID;
}
async function post(config: ZenflowConfig, path: string, body: object): Promise<Record<string, string>> {
  const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(productUrl(config, path), { method: 'POST', signal: controller.signal,
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'Zenflow' }, body: JSON.stringify(body) });
    const result = await response.json();
    if (!response.ok) throw new Error(result.errorCode || 'EXTERNAL_LOGIN_FAILED');
    return result;
  } finally { clearTimeout(timer); }
}
export async function prepareGoogleLogin(config: ZenflowConfig) {
  if (!externalConfigured('google')) throw new Error('PROVIDER_NOT_CONFIGURED');
  const hex = (bytes: Uint8Array) => Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  const verifier = hex(await Crypto.getRandomBytesAsync(32));
  const state = hex(await Crypto.getRandomBytesAsync(24));
  const digest = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, verifier, { encoding: Crypto.CryptoEncoding.BASE64 });
  const start = await post(config, 'auth/external/challenge', { provider: 'google', channel: 'WEB', intent: 'LOGIN', state,
    codeChallenge: digest.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '') });
  if (start.state !== state || !start.transactionId || !start.nonce) throw new Error('INVALID_AUTH_TRANSACTION');
  let used = false;
  return { nonce: start.nonce, async complete(credential: string, current: () => boolean): Promise<ZenflowTokenPair | null> {
    if (!current() || used) return null;
    used = true;
    const result = await post(config, 'auth/external/complete', { transactionId: start.transactionId, proof: credential,
      locale: /^zh/i.test(Intl.DateTimeFormat().resolvedOptions().locale) ? 'zh-CN' : 'en-US' });
    if (!current()) return null;
    if (result.state !== state || !result.code) throw new Error('INVALID_AUTH_TRANSACTION');
    const tokens = await exchangeCode(config, result.code, verifier);
    return current() ? tokens : null;
  } };
}
// Web uses the Google-rendered button, which binds its credential to a server nonce.
export async function externalFlow(_config: ZenflowConfig, _provider: ExternalProvider, _current: () => boolean, _bearer?: string): Promise<ZenflowTokenPair | 'BOUND' | null> {
  throw new Error('USE_GOOGLE_WEB_BUTTON');
}
