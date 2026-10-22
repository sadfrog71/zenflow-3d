import { Platform } from 'react-native';
import * as Crypto from 'expo-crypto';
import * as WebBrowser from 'expo-web-browser';
import type { ZenflowTokenPair } from '@/contracts/zenflow/identity';
import { authUrl, type ZenflowConfig } from './config';

WebBrowser.maybeCompleteAuthSession();
const redirectUri = () => Platform.OS === 'web' ? new URL('/auth/callback', window.location.origin).href : 'zenflow://auth/callback';
function hex(bytes: Uint8Array): string { return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join(''); }

export async function signInWithBrowser(config: ZenflowConfig): Promise<ZenflowTokenPair | null> {
  const REDIRECT = redirectUri();
  const verifier = hex(await Crypto.getRandomBytesAsync(32));
  const digest = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, verifier,
    { encoding: Crypto.CryptoEncoding.BASE64 });
  const challenge = digest.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const state = hex(await Crypto.getRandomBytesAsync(20));
  const authorize = new URL(authUrl(config, 'authorize'));
  authorize.searchParams.set('response_type', 'code');
  authorize.searchParams.set('client_id', config.clientId);
  authorize.searchParams.set('redirect_uri', REDIRECT);
  authorize.searchParams.set('scope', 'zenflow');
  authorize.searchParams.set('code_challenge', challenge);
  authorize.searchParams.set('code_challenge_method', 'S256');
  authorize.searchParams.set('state', state);
  authorize.searchParams.set('prompt', 'login');
  const result = await WebBrowser.openAuthSessionAsync(authorize.toString(), REDIRECT);
  if (result.type !== 'success') return null;
  const callback = new URL(result.url);
  if (callback.origin !== new URL(REDIRECT).origin || callback.protocol !== new URL(REDIRECT).protocol || callback.hostname !== new URL(REDIRECT).hostname || callback.pathname !== new URL(REDIRECT).pathname) {
    throw new Error('INVALID_CALLBACK');
  }
  if (callback.searchParams.get('state') !== state) throw new Error('INVALID_OAUTH_STATE');
  const code = callback.searchParams.get('code');
  if (!code) throw new Error('OAUTH_CODE_MISSING');
  const body = new URLSearchParams({ grant_type: 'authorization_code', client_id: config.clientId,
    code, redirect_uri: REDIRECT, code_verifier: verifier });
  return exchange(config, body);
}

export async function refreshToken(config: ZenflowConfig, token: string): Promise<ZenflowTokenPair> {
  return exchange(config, new URLSearchParams({ grant_type: 'refresh_token', client_id: config.clientId, refresh_token: token }));
}
async function exchange(config: ZenflowConfig, body: URLSearchParams): Promise<ZenflowTokenPair> {
  const response = await fetch(authUrl(config, 'token'), { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body.toString() });
  if (!response.ok) throw new Error(body.get('grant_type') === 'refresh_token' && (response.status === 400 || response.status === 401) ? 'OAUTH_SESSION_EXPIRED' : 'OAUTH_EXCHANGE_FAILED');
  const tokens = await response.json() as ZenflowTokenPair;
  if (!tokens.access_token || tokens.token_type.toLowerCase() !== 'bearer') throw new Error('INVALID_TOKEN_RESPONSE');
  return tokens;
}

export async function exchangeCode(config: ZenflowConfig, code: string, verifier: string): Promise<ZenflowTokenPair> {
  return exchange(config, new URLSearchParams({ grant_type: 'authorization_code', client_id: config.clientId,
    code, redirect_uri: redirectUri(), code_verifier: verifier }));
}
