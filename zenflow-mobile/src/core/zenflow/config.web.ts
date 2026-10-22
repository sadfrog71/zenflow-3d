export type ZenflowConfig = { origin: string; clientId: string; environment: string };
export function readZenflowConfig(): ZenflowConfig | null {
  const raw = process.env.EXPO_PUBLIC_ZENFLOW_API_URL?.trim();
  const clientId = process.env.EXPO_PUBLIC_ZENFLOW_OAUTH_CLIENT_ID?.trim();
  if (!raw || !clientId) return null;
  const url = new URL(raw);
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) throw new Error('INVALID_ZENFLOW_API_URL');
  const preview = process.env.EXPO_PUBLIC_ZENFLOW_WEB_PREVIEW === 'true';
  if (preview && (typeof window === 'undefined' || !['localhost', '127.0.0.1'].includes(window.location.hostname))) throw new Error('WEB_PREVIEW_REQUIRES_LOCALHOST');
  return { origin: preview ? window.location.origin : url.origin, clientId, environment: preview ? 'web-preview' : 'web' };
}
export function productUrl(config: ZenflowConfig, path: string): string { return `${config.origin}/api/zenflow/v1/${path.replace(/^\/+/, '')}`; }
export function authUrl(config: ZenflowConfig, path: string): string { return `${config.origin}/api/admin/oauth2/${path.replace(/^\/+/, '')}`; }
