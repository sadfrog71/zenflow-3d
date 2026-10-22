export type ZenflowConfig = { origin: string; clientId: string; environment: string };

export function readZenflowConfig(): ZenflowConfig | null {
  const raw = process.env.EXPO_PUBLIC_ZENFLOW_API_URL?.trim();
  const clientId = process.env.EXPO_PUBLIC_ZENFLOW_OAUTH_CLIENT_ID?.trim();
  if (!raw || !clientId) return null;
  let url: URL;
  try { url = new URL(raw); } catch { throw new Error('INVALID_ZENFLOW_API_URL'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/'
      || url.search || url.hash) throw new Error('INVALID_ZENFLOW_API_URL');
  return { origin: url.origin, clientId, environment: process.env.ZENFLOW_APP_ENV || 'local' };
}

export function productUrl(config: ZenflowConfig, path: string): string {
  return `${config.origin}/api/zenflow/v1/${path.replace(/^\/+/, '')}`;
}
export function authUrl(config: ZenflowConfig, path: string): string {
  return `${config.origin}/api/admin/oauth2/${path.replace(/^\/+/, '')}`;
}
