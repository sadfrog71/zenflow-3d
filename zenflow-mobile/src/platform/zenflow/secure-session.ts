import * as SecureStore from 'expo-secure-store';

export type StoredSession = { refreshToken: string; scope: string; origin: string };
const KEY = 'zenflow.identity.v1';

export const zenflowSecureSession = {
  async load(): Promise<StoredSession | null> {
    const raw = await SecureStore.getItemAsync(KEY);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw) as StoredSession;
      return typeof parsed.refreshToken === 'string' && typeof parsed.scope === 'string'
        && typeof parsed.origin === 'string' ? parsed : null;
    } catch { return null; }
  },
  async save(value: StoredSession): Promise<void> { await SecureStore.setItemAsync(KEY, JSON.stringify(value)); },
  async clear(): Promise<void> { await SecureStore.deleteItemAsync(KEY); },
};
