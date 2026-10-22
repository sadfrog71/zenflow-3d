export type StoredSession = { refreshToken: string; scope: string; origin: string };
const KEY = 'invelle.web.identity.v1';
// Only renewal credentials persist; access tokens and private content remain in memory.
export const zenflowSecureSession = {
  async load(): Promise<StoredSession | null> {
    if (typeof window === 'undefined') return null;
    const raw = window.localStorage.getItem(KEY) ?? window.sessionStorage.getItem(KEY);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed.refreshToken === 'string' && parsed.refreshToken
        && typeof parsed.scope === 'string' && parsed.scope
        && typeof parsed.origin === 'string' && parsed.origin) {
        const value = { refreshToken: parsed.refreshToken, scope: parsed.scope, origin: parsed.origin };
        // Migrate an existing tab session without requiring a new sign-in.
        window.localStorage.setItem(KEY, JSON.stringify(value));
        window.sessionStorage.removeItem(KEY);
        return value;
      }
    } catch { /* Discard malformed session data. */ }
    await this.clear();
    return null;
  },
  async save(value: StoredSession): Promise<void> {
    window.localStorage.setItem(KEY, JSON.stringify({ refreshToken: value.refreshToken, scope: value.scope, origin: value.origin }));
    window.sessionStorage.removeItem(KEY);
  },
  async clear(): Promise<void> {
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(KEY);
      window.sessionStorage.removeItem(KEY);
    }
  },
  // Serialize rotating refresh credentials between the installed app and browser tabs.
  async withLock<T>(action: () => Promise<T>): Promise<T> {
    if (typeof navigator !== 'undefined' && navigator.locks) return navigator.locks.request(KEY, action);
    return action();
  },
  subscribe(listener: () => void): () => void {
    const changed = (event: StorageEvent) => {
      if (event.storageArea === window.localStorage && (event.key === KEY || event.key === null)) listener();
    };
    window.addEventListener('storage', changed);
    return () => window.removeEventListener('storage', changed);
  },
};
