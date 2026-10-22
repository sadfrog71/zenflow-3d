import * as FileSystem from 'expo-file-system/legacy';
import { Platform } from 'react-native';

export type ReaderAnchor = { bookId: string; versionId: string; language: string; chapterNo: number; paragraphNo: number };
export type ReaderState = { owner: string; rememberProgress: boolean; syncEnabled: boolean; progress: ReaderAnchor[]; bookmarks: ReaderAnchor[] };

const STATE_KEY = 'invelle-reading-state-v1';
const STATE_FILE = 'invelle-reading-state-v1.json';
const defaults = (owner: string): ReaderState => ({ owner, rememberProgress: false, syncEnabled: false, progress: [], bookmarks: [] });

function validAnchor(value: unknown): value is ReaderAnchor {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<ReaderAnchor>;
  return typeof item.bookId === 'string' && typeof item.versionId === 'string' && typeof item.language === 'string'
    && Number.isInteger(item.chapterNo) && Number.isInteger(item.paragraphNo);
}

function normalize(value: any, owner: string): ReaderState {
  if (!value || value.owner !== owner) return defaults(owner);
  return {
    owner,
    rememberProgress: value.rememberProgress === true,
    syncEnabled: value.syncEnabled === true,
    progress: Array.isArray(value.progress) ? value.progress.filter(validAnchor).slice(-100) : [],
    bookmarks: Array.isArray(value.bookmarks) ? value.bookmarks.filter(validAnchor).slice(-1000) : [],
  };
}

export async function loadReaderState(owner: string): Promise<ReaderState> {
  try {
    const raw = Platform.OS === 'web'
      ? globalThis.localStorage?.getItem(STATE_KEY)
      : FileSystem.documentDirectory ? await FileSystem.readAsStringAsync(`${FileSystem.documentDirectory}${STATE_FILE}`) : null;
    return raw ? normalize(JSON.parse(raw), owner) : defaults(owner);
  } catch { return defaults(owner); }
}

let writes: Promise<void> = Promise.resolve();
export function saveReaderState(value: ReaderState): Promise<void> {
  const raw = JSON.stringify(value);
  const action = async () => {
    if (Platform.OS === 'web') { globalThis.localStorage?.setItem(STATE_KEY, raw); return; }
    if (!FileSystem.documentDirectory) return;
    await FileSystem.writeAsStringAsync(`${FileSystem.documentDirectory}${STATE_FILE}`, raw);
  };
  writes = writes.then(action, action);
  return writes;
}

export function clearReaderState(): Promise<void> {
  const action = async () => {
    if (Platform.OS === 'web') { globalThis.localStorage?.removeItem(STATE_KEY); return; }
    if (FileSystem.documentDirectory) await FileSystem.deleteAsync(`${FileSystem.documentDirectory}${STATE_FILE}`, { idempotent: true });
  };
  writes = writes.then(action, action);
  return writes;
}

type CachedVersion = { id: string; versionId: string; versionKey: string; language: string; chapters: any[] } & Record<string, any>;
const publishedVersions = new Map<string, CachedVersion>();
export function versionCacheKey(bookId: string, versionId: string, language: string) { return `${bookId}:${versionId}:${language}`; }
export function getCachedVersion(bookId: string, versionId: string, language: string): CachedVersion | undefined {
  return publishedVersions.get(versionCacheKey(bookId, versionId, language));
}
export function removeCachedVersion(bookId: string, versionId: string, language: string): void {
  publishedVersions.delete(versionCacheKey(bookId, versionId, language));
}
export function cachePublishedVersion(value: CachedVersion): void {
  publishedVersions.set(versionCacheKey(value.id, value.versionId, value.language), value);
}
export function pruneVersionCache(allowed: Set<string>): void {
  for (const key of publishedVersions.keys()) if (!allowed.has(key)) publishedVersions.delete(key);
}
