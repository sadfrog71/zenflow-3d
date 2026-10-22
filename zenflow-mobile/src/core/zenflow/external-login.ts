import * as Crypto from 'expo-crypto';
import { Platform } from 'react-native';
import { productUrl, type ZenflowConfig } from './config';
import { exchangeCode } from './oauth';
import type { ZenflowTokenPair } from '@/contracts/zenflow/identity';
export type ExternalProvider = 'google' | 'facebook' | 'apple';
export function externalConfigured(provider: ExternalProvider): boolean {
  if (Platform.OS !== 'ios') return false;
  if (provider === 'google') return !!process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_IOS_CLIENT_ID && !!process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_WEB_CLIENT_ID;
  if (provider === 'facebook') return !!process.env.EXPO_PUBLIC_ZENFLOW_META_APP_ID && !!process.env.EXPO_PUBLIC_ZENFLOW_META_CLIENT_TOKEN;
  return process.env.EXPO_PUBLIC_ZENFLOW_APPLE_LOGIN === 'true';
}
async function proof(provider: ExternalProvider, nonce: string): Promise<string | null> {
  if (provider === 'google') {
    const { GoogleSignin, isSuccessResponse, statusCodes } = require('@react-native-google-signin/google-signin') as typeof import('@react-native-google-signin/google-signin');
    GoogleSignin.configure({ iosClientId: process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_IOS_CLIENT_ID,
      webClientId: process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_WEB_CLIENT_ID, offlineAccess: true });
    // Force an explicit chooser for a new account transaction.
    await GoogleSignin.signOut();
    try {
      const result = await GoogleSignin.signIn();
      if (!isSuccessResponse(result)) return null;
      if (!result.data.serverAuthCode) throw new Error('PROVIDER_CODE_MISSING');
      return result.data.serverAuthCode;
    } catch (error) {
      if ((error as { code?: string }).code === statusCodes.SIGN_IN_CANCELLED) return null;
      throw error;
    }
  }
  if (provider === 'facebook') {
    const { LoginManager, AuthenticationToken, Settings } = require('react-native-fbsdk-next') as typeof import('react-native-fbsdk-next');
    Settings.setAppID(process.env.EXPO_PUBLIC_ZENFLOW_META_APP_ID!);
    Settings.initializeSDK();
    LoginManager.logOut();
    const result = await LoginManager.logInWithPermissions(['public_profile', 'email'], 'limited', nonce);
    if (result.isCancelled) return null;
    const token = await AuthenticationToken.getAuthenticationTokenIOS();
    if (!token?.authenticationToken) throw new Error('PROVIDER_TOKEN_MISSING');
    return token.authenticationToken;
  }
  const Apple = require('expo-apple-authentication') as typeof import('expo-apple-authentication');
  if (!await Apple.isAvailableAsync()) throw new Error('APPLE_LOGIN_UNAVAILABLE');
  try {
    const result = await Apple.signInAsync({ requestedScopes: [Apple.AppleAuthenticationScope.EMAIL, Apple.AppleAuthenticationScope.FULL_NAME], nonce });
    if (!result.identityToken) throw new Error('PROVIDER_TOKEN_MISSING');
    return result.identityToken;
  } catch (error) {
    if ((error as { code?: string }).code === 'ERR_REQUEST_CANCELED') return null;
    throw error;
  }
}
async function post(config: ZenflowConfig, path: string, body: object, bearer?: string): Promise<Record<string, string>> {
  const abort = new AbortController(); const timer = setTimeout(() => abort.abort(), 15000);
  try {
    const response = await fetch(productUrl(config, path), { method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'Zenflow', ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}) },
      body: JSON.stringify(body), signal: abort.signal });
    const result = await response.json() as Record<string, string>;
    if (!response.ok) throw new Error(result.errorCode || 'EXTERNAL_LOGIN_FAILED');
    return result;
  } finally { clearTimeout(timer); }
}
export async function externalFlow(config: ZenflowConfig, provider: ExternalProvider, current: () => boolean,
    bearer?: string): Promise<ZenflowTokenPair | 'BOUND' | null> {
  if (!externalConfigured(provider)) throw new Error('PROVIDER_NOT_CONFIGURED');
  const verifier = Array.from(await Crypto.getRandomBytesAsync(32), b => b.toString(16).padStart(2, '0')).join('');
  const state = Array.from(await Crypto.getRandomBytesAsync(24), b => b.toString(16).padStart(2, '0')).join('');
  const digest = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, verifier, { encoding: Crypto.CryptoEncoding.BASE64 });
  const codeChallenge = digest.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const start = await post(config, 'auth/external/challenge', { provider, state, codeChallenge, intent: bearer ? 'BIND' : 'LOGIN' }, bearer);
  if (start.state !== state || !start.transactionId || !start.nonce) throw new Error('INVALID_AUTH_TRANSACTION');
  if (!current()) return null;
  const credential = await proof(provider, start.nonce);
  if (!credential || !current()) return null;
  const result = await post(config, 'auth/external/complete', { transactionId: start.transactionId, proof: credential,
    locale: /^zh/i.test(Intl.DateTimeFormat().resolvedOptions().locale) ? 'zh-CN' : 'en-US' }, bearer);
  if (!current()) return null;
  if (result.state !== state) throw new Error('INVALID_OAUTH_STATE');
  if (bearer) {
    if (result.nextAction !== 'BOUND') throw new Error('INVALID_BIND_RESPONSE');
    return 'BOUND';
  }
  if (!result.code) throw new Error('OAUTH_CODE_MISSING');
  return exchangeCode(config, result.code, verifier);
}
