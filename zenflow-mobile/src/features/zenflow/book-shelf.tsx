import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, AppState, Linking, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { productUrl, type ZenflowConfig } from '@/core/zenflow/config';
import { ZenflowSession } from '@/core/zenflow/session';
import { cachePublishedVersion, getCachedVersion, loadReaderState, pruneVersionCache, removeCachedVersion, saveReaderState, versionCacheKey, type ReaderAnchor, type ReaderState } from './book-storage';

type Release = { id: string; sourceVersionId: string; sourceVersionNo: number; targetLanguage: string; revisionNo: number; isExcerpt: boolean; excerptLabel: string; availableLanguages: string[]; versionKey: string };
type Book = { id: string; title: string; author: string; publisher: string; sourceLanguage: string; sourceVersion: string; category: string; summary: string; releases: Release[] };
type AnnotationTone = 'word' | 'clause' | 'variant';
type BookAnnotation = { phrase: string; note: string; tone?: AnnotationTone };
type AnnotationRange = BookAnnotation & { start: number; end: number; id: number };
type AnnotatedUnit = { original: string; note?: string; tone?: AnnotationTone };
export type BookVersion = {
  id: string; versionId: string; versionKey: string; versionLabel: string; sourceVersionNo: number; revisionNo: number;
  title: string; author: string; publisher: string; category: string; summary: string; sourceLanguage: string; targetLanguage: string;
  language: string; isExcerpt: boolean; excerptLabel: string; sourceUrl: string; licenseName: string; licenseHolder: string;
  licenseTerm: string; licenseExpiresOn: string; translationNote: string; editorNote: string; translationOrigin: string; modelNote: string; translatorNote: string;
  chapterSummaries?: { id: string; chapterNo: number; title: string }[];
  chapters: { id: string; chapterNo: number; title: string; sourceText: string; translationText: string; text: string; commentary: string; annotations: BookAnnotation[]; annotationLayer?: 'source' | 'translation'; loaded?: boolean }[];
};
type ShelfScreen = 'shelf' | 'detail' | 'reader' | 'bookmarks';
type Props = { english: boolean; config: ZenflowConfig | null; session: ZenflowSession | null; ownerId: string | null; onExit: () => void; onSignIn: () => void };

const textFor = (english: boolean, en: string, zh: string) => english ? en : zh;
const unwrap = (value: any) => value && Object.prototype.hasOwnProperty.call(value, 'code') && Object.prototype.hasOwnProperty.call(value, 'data') ? value.data : value;
const languageName = (language: string, english: boolean) => language === 'en-US' ? (english ? 'English' : '英语') : (english ? 'Simplified Chinese' : '简体中文');
const categoryName = (category: string, english: boolean) => ({ classics: textFor(english, 'Classics', '经典'), guides: textFor(english, 'Getting started', '入门'), stories: textFor(english, 'In daily life', '生活中的佛法') }[category] || category);
const anchorKey = (anchor: ReaderAnchor) => `${anchor.bookId}:${anchor.versionId}:${anchor.language}:${anchor.chapterNo}:${anchor.paragraphNo}`;
const annotationColor = (tone?: AnnotationTone) => tone === 'clause' ? '#d84a42' : tone === 'variant' ? '#b7790b' : '#27988a';
const isHanAnnotatedBook = (book: Book) => book.category === 'classics' && book.sourceLanguage === 'zh-CN';

// Reading titles are localized separately from source bibliographic metadata.
const englishReadingTitle = (title: string) => {
  const titles: [string, string][] = [
    ['中论', 'The Middle Treatise'], ['坛经', 'The Platform Sutra'],
    ['六入处', 'Samyukta Agama: The Six Sense Bases'], ['因缘', 'Samyukta Agama: Dependent Arising'],
    ['蕴相应', 'Samyukta Agama: The Aggregates'], ['心经', 'The Heart Sutra'], ['金刚经', 'The Diamond Sutra'],
  ];
  return /\p{Script=Han}/u.test(title)
    ? titles.find(([name]) => title.includes(name))?.[1] || 'English reading edition'
    : title;
};

function annotationUnitWidth(unit: AnnotatedUnit, fontSize: number) {
  const length = unit.note?.length || 0;
  const columns = Math.ceil(length / Math.max(1, Math.ceil(length / 6)));
  const noteSize = Math.max(12, Math.round(fontSize * .62 + 1));
  return Math.max(32, unit.original.length * (fontSize + 2) + 4, columns * noteSize + 6);
}

function mergeChapterResponse(value: BookVersion, cached?: BookVersion): BookVersion {
  if (!value.chapterSummaries) return value;
  const chapters = value.chapterSummaries.map(summary => {
    const full = value.chapters.find(chapter => chapter.chapterNo === summary.chapterNo)
      || cached?.chapters.find(chapter => chapter.chapterNo === summary.chapterNo && chapter.loaded !== false);
    return full ? { ...full, loaded: true } : { ...summary, sourceText: '', translationText: '', text: '', commentary: '', annotations: [], loaded: false };
  });
  return { ...value, chapters };
}

export function annotationUnits(text: string, matches: AnnotationRange[], start = 0): AnnotatedUnit[] {
  const units: AnnotatedUnit[] = [];
  let cursor = start, prefix = '';
  const appendGap = (gap: string) => {
    if (!gap) return;
    if (/^[\s\p{P}]*$/u.test(gap)) {
      const opening = gap.match(/[「『“‘（(《〈【\[]+$/u)?.[0] || '';
      const closing = gap.slice(0, gap.length - opening.length);
      if (units.length) units[units.length - 1].original += closing;
      else prefix += closing;
      prefix += opening;
    } else {
      // Keep closing punctuation with the preceding annotated phrase in English too.
      const closing = gap.match(/^[\s]*[，。；：？！、,.!?;:…」』”’）)》〉】\]]+/u)?.[0] || '';
      if (closing && units.length) {
        units[units.length - 1].original += closing;
        gap = gap.slice(closing.length);
      }
      if (gap) { units.push({ original: prefix + gap }); prefix = ''; }
    }
  };
  for (const match of matches) {
    appendGap(text.slice(cursor - start, match.start - start));
    units.push({ original: prefix + match.phrase, note: match.note, tone: match.tone });
    prefix = ''; cursor = match.end;
  }
  appendGap(text.slice(cursor - start));
  if (prefix) {
    if (units.length) units[units.length - 1].original += prefix;
    else units.push({ original: prefix });
  }
  return units;
}

async function fetchBookJson(url: string, controller: AbortController) {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let onAbort = () => {};
  try {
    return await Promise.race([
      (async () => {
        const response = await fetch(url, { signal: controller.signal, headers: { Accept: 'application/json' } });
        const raw = await response.json(), body = unwrap(raw);
        if (!response.ok || (typeof raw?.code === 'number' && raw.code !== 0)) throw new Error(body?.errorCode || raw?.errorCode || 'BOOK_REQUEST_FAILED');
        return body;
      })(),
      new Promise<never>((_, reject) => {
        onAbort = () => reject(Object.assign(new Error('Book request cancelled'), { name: 'AbortError' }));
        if (controller.signal.aborted) { onAbort(); return; }
        controller.signal.addEventListener('abort', onAbort, { once: true });
        timer = setTimeout(() => { reject(new Error('BOOK_REQUEST_TIMEOUT')); controller.abort(); }, 20000);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
    controller.signal.removeEventListener('abort', onAbort);
  }
}

function findAnnotationRanges(text: string, annotations: BookAnnotation[] = [], independentOrder = false): AnnotationRange[] {
  const ranges: AnnotationRange[] = [];
  let cursor = 0;
  const phraseOffsets = new Map<string, number>();
  for (const annotation of annotations) {
    if (!annotation.phrase) continue;
    let start = text.indexOf(annotation.phrase, independentOrder ? phraseOffsets.get(annotation.phrase) || 0 : cursor);
    if (independentOrder) {
      // Full annotations can repeat a short phrase already contained in an earlier unit.
      // Advance to its next unused occurrence instead of silently dropping its note.
      while (start >= 0) {
        const occupied = ranges.find(range => start < range.end && start + annotation.phrase.length > range.start);
        if (!occupied) break;
        start = text.indexOf(annotation.phrase, occupied.end);
      }
    }
    if (start < 0) continue;
    ranges.push({ ...annotation, start, end: start + annotation.phrase.length, id: ranges.length });
    cursor = start + annotation.phrase.length;
    phraseOffsets.set(annotation.phrase, cursor);
  }
  if (!independentOrder) return ranges;
  const ordered: AnnotationRange[] = [];
  for (const range of ranges.sort((a, b) => a.start - b.start || b.end - a.end)) {
    if (ordered.length && range.start < ordered[ordered.length - 1].end) continue;
    ordered.push({ ...range, id: ordered.length });
  }
  return ordered;
}

function splitParagraphOffsets(text: string) {
  const paragraphs: { text: string; start: number }[] = [];
  let cursor = 0;
  for (const raw of text.split(/\n\s*\n/)) {
    const paragraph = raw.trim();
    if (!paragraph) continue;
    const start = text.indexOf(paragraph, cursor);
    paragraphs.push({ text: paragraph, start });
    cursor = start + paragraph.length;
  }
  return paragraphs;
}

export function BookShelf({ english, config, session, ownerId, onExit, onSignIn }: Props) {
  const owner = ownerId ? `account:${ownerId}` : 'guest';
  const [screen, setScreen] = useState<ShelfScreen>('shelf');
  const [books, setBooks] = useState<Book[]>([]);
  const [activeBook, setActiveBook] = useState<Book | null>(null);
  const [language, setLanguage] = useState(english ? 'en-US' : 'zh-CN');
  const [version, setVersion] = useState<BookVersion | null>(null);
  const [chapterIndex, setChapterIndex] = useState(0);
  const [activeParagraph, setActiveParagraph] = useState(0);
  const [fontSize, setFontSize] = useState(16);
  const [filter, setFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  const [catalogBusy, setCatalogBusy] = useState(false);
  const [catalogError, setCatalogError] = useState('');
  const [readerError, setReaderError] = useState('');
  const [readerState, setReaderState] = useState<ReaderState>({ owner, rememberProgress: false, syncEnabled: false, progress: [], bookmarks: [] });
  const [stateLoaded, setStateLoaded] = useState(false);
  const [syncPrompt, setSyncPrompt] = useState<'enable' | 'disable' | null>(null);
  const [sourceOpen, setSourceOpen] = useState(false);
  const [selectedAnnotationId, setSelectedAnnotationId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const request = useRef<AbortController | null>(null), catalogRequest = useRef<AbortController | null>(null);
  const versionRef = useRef<BookVersion | null>(null), bookRef = useRef<Book | null>(null), paragraphOffsets = useRef<number[]>([]);
  versionRef.current = version; bookRef.current = activeBook;
  const identityGeneration = session?.snapshot().generation ?? 0;
  const availableLanguages = useMemo(() => activeBook ? Array.from(new Set(activeBook.releases.flatMap(item => item.availableLanguages))) : [], [activeBook]);
  const readerEnglish = /^en(?:-|$)/i.test(version?.language || language);
  const interfaceEnglish = screen === 'reader' ? readerEnglish : english;
  const currentChapter = version?.chapters[chapterIndex]?.loaded === false ? undefined : version?.chapters[chapterIndex];
  const paragraphs = useMemo(() => currentChapter ? splitParagraphOffsets(currentChapter.text) : [], [currentChapter]);
  const annotationRanges = useMemo(() => currentChapter ? findAnnotationRanges(currentChapter.text, currentChapter.annotations, readerEnglish) : [], [currentChapter, readerEnglish]);
  const currentAnchor = (paragraphNo: number): ReaderAnchor | null => version ? { bookId: version.id, versionId: version.versionId, language: version.language, chapterNo: currentChapter?.chapterNo || 1, paragraphNo } : null;

  async function refreshCatalog() {
    if (!config) { setCatalogError(textFor(english, 'The book service is not configured.', '书架服务尚未配置。')); return; }
    catalogRequest.current?.abort();
    const controller = new AbortController(); catalogRequest.current = controller;
    setCatalogBusy(true);
    try {
      const body = await fetchBookJson(productUrl(config, 'books'), controller);
      if (catalogRequest.current !== controller || controller.signal.aborted) return;
      const items = Array.isArray(body?.items) ? [...body.items as Book[]].sort((a, b) => Number(isHanAnnotatedBook(b)) - Number(isHanAnnotatedBook(a))) : [];
      setBooks(items); setCatalogError('');
      const allowed = new Set<string>(), liveAnchors = new Set<string>();
      for (const book of items) for (const release of book.releases || []) for (const itemLanguage of release.availableLanguages || []) {
        allowed.add(versionCacheKey(book.id, release.id, itemLanguage));
        liveAnchors.add(`${book.id}:${release.id}:${itemLanguage}`);
      }
      pruneVersionCache(allowed);
      const currentVersion = versionRef.current;
      if (currentVersion && !allowed.has(versionCacheKey(currentVersion.id, currentVersion.versionId, currentVersion.language))) {
        request.current?.abort();
        setVersion(null);
        setReaderError(textFor(english, 'This edition was withdrawn or its permission expired. Its cached copy has been cleared.', '此版本已下架或许可失效，当前缓存已清除。'));
      }
      const currentBook = bookRef.current;
      if (currentBook && !items.some(item => item.id === currentBook.id)) { setActiveBook(null); setScreen('shelf'); }
      setReaderState(previous => ({ ...previous,
        progress: previous.progress.filter(item => liveAnchors.has(`${item.bookId}:${item.versionId}:${item.language}`)),
        bookmarks: previous.bookmarks.filter(item => liveAnchors.has(`${item.bookId}:${item.versionId}:${item.language}`)),
      }));
    } catch (error) {
      if (catalogRequest.current === controller && (error as Error)?.name !== 'AbortError') setCatalogError(textFor(english, 'Could not refresh the shelf. Previously opened editions stay available for this session.', '书架刷新失败；本次已打开的版本仍可在当前会话中查看。'));
    } finally {
      if (catalogRequest.current === controller) setCatalogBusy(false);
    }
  }

  useEffect(() => {
    void refreshCatalog();
    const subscription = AppState.addEventListener('change', state => { if (state === 'active') void refreshCatalog(); });
    return () => { catalogRequest.current?.abort(); catalogRequest.current = null; subscription.remove(); };
  }, [config, english]);
  useEffect(() => () => { request.current?.abort(); }, []);

  useEffect(() => {
    let active = true; setStateLoaded(false);
    setReaderState({ owner, rememberProgress: false, syncEnabled: false, progress: [], bookmarks: [] });
    void (async () => {
      const local = await loadReaderState(owner);
      if (!active) return;
      let next = local;
      if (ownerId && session?.snapshot().profile?.userId === ownerId) {
        try {
          const remote = await session.request<{ syncEnabled: boolean; progress: ReaderAnchor[]; bookmarks: ReaderAnchor[] }>('books/state', 'GET');
          if (remote.syncEnabled) next = { ...local, syncEnabled: true, progress: remote.progress || [], bookmarks: remote.bookmarks || [] };
        } catch { /* The local reader remains available if account sync cannot be reached. */ }
      }
      if (active) { setReaderState(next); setStateLoaded(true); }
    })();
    return () => { active = false; };
  }, [owner, ownerId, identityGeneration, session]);

  useEffect(() => {
    if (!stateLoaded || readerState.owner !== owner) return;
    void saveReaderState(readerState);
  }, [readerState, owner, stateLoaded]);

  useEffect(() => {
    if (!stateLoaded || !readerState.syncEnabled || !ownerId || !session || session.snapshot().profile?.userId !== ownerId) return;
    const timer = setTimeout(() => {
      const input = { syncEnabled: true, progress: readerState.progress.slice(-100), bookmarks: readerState.bookmarks.slice(-1000) };
      void session.request('books/state', 'POST', input).catch(() => {});
    }, 350);
    return () => clearTimeout(timer);
  }, [readerState.progress, readerState.bookmarks, readerState.syncEnabled, stateLoaded, ownerId, identityGeneration, session]);

  function selectBook(book: Book) {
    setActiveBook(book); setVersion(null); setReaderError('');
    const desired = english ? 'en-US' : 'zh-CN';
    const nextLanguage = book.releases.some(release => release.availableLanguages.includes(desired)) ? desired : (book.releases[0]?.availableLanguages[0] || book.sourceLanguage);
    setLanguage(nextLanguage); setScreen('detail');
  }
  function releaseFor(book: Book, selectedLanguage: string): Release | undefined {
    const candidates = (book.releases || []).filter(item => item.availableLanguages.includes(selectedLanguage));
    return candidates.find(item => item.targetLanguage === selectedLanguage) || candidates[0];
  }
  async function openReader(book = activeBook, selectedLanguage = language, anchor?: ReaderAnchor) {
    if (!book) return;
    const release = releaseFor(book, selectedLanguage);
    if (!release) { setReaderError(textFor(english, 'This language edition is not available yet.', '这个语言版本暂未上架。')); return; }
    setActiveBook(book); setLanguage(selectedLanguage); setScreen('reader'); setReaderError(''); setSourceOpen(false);
    const cached = getCachedVersion(book.id, release.id, selectedLanguage) as BookVersion | undefined;
    const progress = anchor || readerState.progress.find(item => item.bookId === book.id && item.versionId === release.id && item.language === selectedLanguage);
    if (cached) {
      setVersion(cached);
      setChapterIndex(Math.max(0, (cached.chapters || []).findIndex(item => item.chapterNo === progress?.chapterNo)));
      setActiveParagraph(progress?.paragraphNo || 0);
    } else { setVersion(null); setChapterIndex(0); setActiveParagraph(0); }
    if (!config) { if (!cached) setReaderError(textFor(english, 'The book service is not configured.', '书架服务尚未配置。')); return; }
    request.current?.abort(); const controller = new AbortController(); request.current = controller; setLoading(true);
    try {
      const url = `${productUrl(config, `books/${book.id}/versions/${release.id}`)}?language=${encodeURIComponent(selectedLanguage)}&chapterNo=${progress?.chapterNo || 1}`;
      const body = await fetchBookJson(url, controller);
      if (request.current !== controller || controller.signal.aborted) return;
      const value = mergeChapterResponse(body as BookVersion, cached); cachePublishedVersion(value); setVersion(value);
      setChapterIndex(Math.max(0, value.chapters.findIndex(item => item.chapterNo === progress?.chapterNo)));
      setActiveParagraph(progress?.paragraphNo || 0);
    } catch (error) {
      if (request.current === controller && (error as Error)?.name !== 'AbortError') {
        if ((error as Error)?.message === 'BOOK_VERSION_NOT_AVAILABLE') { removeCachedVersion(book.id, release.id, selectedLanguage); setVersion(null); }
        setReaderError((error as Error)?.message === 'BOOK_REQUEST_TIMEOUT'
          ? textFor(english, 'Loading timed out. Check your connection and retry.', '读取超时，请检查网络后重试。')
          : (error as Error)?.message === 'BOOK_VERSION_NOT_AVAILABLE'
          ? textFor(english, 'This edition has been withdrawn or its permission has expired.', '这个版本已下架或许可已失效。')
          : textFor(english, 'Could not load this edition. Try again when you are online.', '暂时无法读取该版本，请联网后重试。'));
      }
    } finally { if (request.current === controller) setLoading(false); }
  }

  function saveProgress(chapterNo: number, paragraphNo: number) {
    if (!version || !readerState.rememberProgress) return;
    const anchor = { bookId: version.id, versionId: version.versionId, language: version.language, chapterNo, paragraphNo };
    setReaderState(previous => ({ ...previous, progress: [...previous.progress.filter(item => !(item.bookId === anchor.bookId && item.versionId === anchor.versionId && item.language === anchor.language)), anchor].slice(-100) }));
  }
  function openChapter(index: number, paragraphNo = 0) {
    const chapter = version?.chapters[index];
    if (!version || !chapter || !activeBook) return;
    saveProgress(chapter.chapterNo, paragraphNo);
    void openReader(activeBook, language, { bookId: version.id, versionId: version.versionId, language: version.language, chapterNo: chapter.chapterNo, paragraphNo });
  }
  function toggleBookmark(anchor: ReaderAnchor) {
    setReaderState(previous => {
      const exists = previous.bookmarks.some(item => anchorKey(item) === anchorKey(anchor));
      const bookmarks = exists ? previous.bookmarks.filter(item => anchorKey(item) !== anchorKey(anchor)) : [...previous.bookmarks, anchor].slice(-1000);
      return { ...previous, bookmarks };
    });
  }
  function toggleRememberProgress() {
    setReaderState(previous => ({ ...previous, rememberProgress: !previous.rememberProgress }));
  }
  async function enableSync() {
    if (!session || !ownerId || session.snapshot().profile?.userId !== ownerId) { setSyncPrompt(null); onSignIn(); return; }
    setBusy(true);
    try {
      const remote = await session.request<{ syncEnabled: boolean; progress: ReaderAnchor[]; bookmarks: ReaderAnchor[] }>('books/state', 'GET');
      const mergedProgress = new Map<string, ReaderAnchor>();
      for (const item of [...(remote.progress || []), ...readerState.progress]) mergedProgress.set(`${item.bookId}:${item.versionId}:${item.language}`, item);
      const mergedBookmarks = new Map<string, ReaderAnchor>();
      for (const item of [...(remote.bookmarks || []), ...readerState.bookmarks]) mergedBookmarks.set(anchorKey(item), item);
      const next = { ...readerState, syncEnabled: true, progress: [...mergedProgress.values()].slice(-100), bookmarks: [...mergedBookmarks.values()].slice(-1000) };
      const saved = await session.request<{ syncEnabled: boolean; progress: ReaderAnchor[]; bookmarks: ReaderAnchor[] }>('books/state', 'POST', { syncEnabled: true, progress: next.progress, bookmarks: next.bookmarks });
      setReaderState({ ...next, progress: saved.progress || next.progress, bookmarks: saved.bookmarks || next.bookmarks });
      setSyncPrompt(null);
    } catch { Alert.alert(textFor(english, 'Sync unavailable', '同步暂不可用'), textFor(english, 'Your reading position and bookmarks remain on this device.', '阅读位置和书签仍保留在本机。')); }
    finally { setBusy(false); }
  }
  async function disableSync() {
    if (!session || !ownerId || session.snapshot().profile?.userId !== ownerId) { setReaderState(previous => ({ ...previous, syncEnabled: false })); return; }
    setBusy(true);
    try { await session.request('books/state', 'POST', { syncEnabled: false, progress: [], bookmarks: [] }); setReaderState(previous => ({ ...previous, syncEnabled: false })); }
    catch { Alert.alert(textFor(english, 'Could not disable sync', '关闭同步失败'), textFor(english, 'Please try again when the service is available.', '服务恢复后请重试。')); }
    finally { setBusy(false); }
  }
  async function clearState() {
    if (Platform.OS === 'web') {
      const accepted = globalThis.confirm?.(textFor(english, 'Clear this device’s reading position and bookmarks?', '清除本机的阅读位置和书签？'));
      if (!accepted) return;
    } else {
      const accepted = await new Promise<boolean>(resolve => Alert.alert(textFor(english, 'Clear reading data?', '清除阅读数据？'), textFor(english, 'This removes saved positions and bookmarks from this device and, when sync is on, from your account.', '这会删除本机阅读位置和书签；若已开启同步，也会删除账号中的副本。'), [
        { text: textFor(english, 'Cancel', '取消'), style: 'cancel', onPress: () => resolve(false) },
        { text: textFor(english, 'Clear', '清除'), style: 'destructive', onPress: () => resolve(true) },
      ]));
      if (!accepted) return;
    }
    setReaderState(previous => ({ ...previous, progress: [], bookmarks: [] }));
    if (readerState.syncEnabled) {
      try { await session?.request('books/state', 'POST', { syncEnabled: true, progress: [], bookmarks: [] }); } catch { /* The local data is still cleared. */ }
    }
  }

  function jumpBookmark(anchor: ReaderAnchor) {
    const book = books.find(item => item.id === anchor.bookId);
    if (!book) return;
    setActiveBook(book); setLanguage(anchor.language); void openReader(book, anchor.language, anchor);
  }
  function updateProgressFromScroll(event: any) {
    const visibleY = event.nativeEvent.contentOffset.y + Math.max(80, event.nativeEvent.layoutMeasurement.height * 0.28);
    let index = 0;
    for (let i = 0; i < paragraphOffsets.current.length; i++) if (paragraphOffsets.current[i] <= visibleY) index = i;
    const paragraphNo = index + 1;
    if (paragraphNo !== activeParagraph) {
      setActiveParagraph(paragraphNo);
      if (currentChapter) saveProgress(currentChapter.chapterNo, paragraphNo);
    }
  }

  useEffect(() => { paragraphOffsets.current = []; setSelectedAnnotationId(null); }, [version?.versionId, version?.language, chapterIndex]);

  return <View style={styles.root}>
    <View style={styles.topBar}>
      <Pressable onPress={() => { request.current?.abort(); request.current = null; setLoading(false); if (screen === 'shelf') onExit(); else setScreen('shelf'); }} style={styles.backButton}><Text style={styles.backText}>{screen === 'shelf' ? textFor(interfaceEnglish, '← Home', '← 返回首页') : textFor(interfaceEnglish, '← Shelf', '← 返回书架')}</Text></Pressable>
      <Text style={styles.brand}>{screen === 'reader' ? textFor(interfaceEnglish, 'Reading', '阅读') : screen === 'bookmarks' ? textFor(interfaceEnglish, 'Bookmarks', '我的书签') : textFor(interfaceEnglish, 'The bookshelf', '书架')}</Text>
      {screen !== 'reader' && <Pressable onPress={() => setScreen('bookmarks')} style={styles.topLink}><Text style={styles.topLinkText}>{textFor(interfaceEnglish, `Bookmarks (${readerState.bookmarks.length})`, `书签 ${readerState.bookmarks.length}`)}</Text></Pressable>}
      {screen === 'reader' && <View style={styles.readerTools}><Pressable onPress={() => setFontSize(value => Math.max(14, value - 1))}><Text style={styles.topLinkText}>A−</Text></Pressable><Pressable onPress={() => setFontSize(value => Math.min(22, value + 1))}><Text style={styles.topLinkText}>A+</Text></Pressable><Pressable onPress={() => setSourceOpen(value => !value)}><Text style={styles.topLinkText}>{textFor(interfaceEnglish, 'Edition', '版本')}</Text></Pressable></View>}
    </View>
    {screen === 'shelf' && <ScrollView contentContainerStyle={styles.content}>
      <View style={styles.intro}><Text style={styles.kicker}>INVELLE · READING ROOM</Text><Text style={styles.title}>{textFor(english, 'Find a page to stay with.', '找一段文字，慢慢读。')}</Text><Text style={styles.subtitle}>{textFor(english, 'Read at your own pace. A note can help, and it can also wait.', '按自己的速度读。注释可以帮忙，也可以先放在一旁。')}</Text></View>
      <View style={styles.filterRow}>{[['all', textFor(english, 'All', '全部')], ['classics', categoryName('classics', english)], ['guides', categoryName('guides', english)], ['stories', categoryName('stories', english)]].map(([value, label]) => <Pressable key={value} onPress={() => setFilter(value)} style={[styles.filter, filter === value && styles.filterActive]}><Text style={[styles.filterText, filter === value && styles.filterTextActive]}>{label}</Text></Pressable>)}</View>
      {catalogError ? <Text accessibilityRole="alert" style={styles.notice}>{catalogError}</Text> : null}
      {catalogError && !catalogBusy ? <Pressable style={styles.primaryButton} onPress={() => void refreshCatalog()}><Text style={styles.primaryButtonText}>{textFor(english, 'Retry', '重新加载')}</Text></Pressable> : null}
      {catalogBusy && !books.length ? <ActivityIndicator color="#687963" /> : null}
      {!books.filter(book => filter === 'all' || book.category === filter).length && !catalogError && !catalogBusy && <Text style={styles.empty}>{textFor(english, 'The shelf is being prepared.', '书架正在整理中。')}</Text>}
      {books.filter(book => filter === 'all' || book.category === filter).map((book, index) => <Pressable key={book.id} onPress={() => selectBook(book)} style={styles.bookCard} accessibilityRole="button">
        <View style={[styles.cover, { backgroundColor: ['#8b927d', '#ab9c86', '#788c82', '#a28f83'][index % 4] }]}><Text style={styles.coverMark}>○</Text><Text style={styles.coverLabel}>{categoryName(book.category, english)}</Text></View>
        <View style={styles.bookCardText}><Text style={styles.bookTitle}>{book.title}</Text>{book.category === 'classics' && <Text style={styles.editionLine}>{isHanAnnotatedBook(book) ? textFor(english, 'Classical text · inline notes · modern rendering', '汉文原文 · 词句夹注 · 白话译文') : textFor(english, 'Earlier edition · selected phrase notes', '旧版 · 词句夹注')}</Text>}<Text style={styles.byline}>{book.author || book.publisher || ''}</Text><Text style={styles.bookSummary}>{book.summary}</Text><Text style={styles.editionLine}>{textFor(english, 'Available', '可读语言')} · {Array.from(new Set(book.releases.flatMap(item => item.availableLanguages))).map(item => languageName(item, english)).join(' · ')}</Text></View>
        <Text style={styles.arrow}>↗</Text>
      </Pressable>)}
      <View style={styles.privacyBox}><Text style={styles.sectionTitle}>{textFor(english, 'Reading memory', '阅读记忆')}</Text><Text style={styles.smallCopy}>{textFor(english, 'Positions and bookmarks stay on this device by default. You can turn on account sync separately.', '阅读位置和书签默认只保存在本机。跨设备同步需要单独开启。')}</Text>
        <Pressable style={styles.settingRow} onPress={toggleRememberProgress}><Text style={styles.settingText}>{textFor(english, 'Remember my position', '记住阅读位置')}</Text><Text style={[styles.switch, readerState.rememberProgress && styles.switchOn]}>{readerState.rememberProgress ? 'ON' : 'OFF'}</Text></Pressable>
        <Pressable style={styles.settingRow} onPress={() => setSyncPrompt(readerState.syncEnabled ? 'disable' : 'enable')} disabled={busy}><Text style={styles.settingText}>{textFor(english, 'Sync across devices', '跨设备同步')}</Text><Text style={[styles.switch, readerState.syncEnabled && styles.switchOn]}>{readerState.syncEnabled ? 'ON' : 'OFF'}</Text></Pressable>
        {(readerState.progress.length || readerState.bookmarks.length) ? <Pressable onPress={() => void clearState()}><Text style={styles.clearText}>{textFor(english, 'Clear reading positions and bookmarks', '清除阅读位置和书签')}</Text></Pressable> : null}
      </View>
    </ScrollView>}

    {screen === 'detail' && activeBook && <ScrollView contentContainerStyle={styles.content}>
      <Pressable style={styles.detailCover} onPress={() => {}}><Text style={styles.coverMark}>○</Text><Text style={styles.detailCoverTitle}>{activeBook.title}</Text><Text style={styles.coverLabel}>{categoryName(activeBook.category, english)}</Text></Pressable>
      <Text style={styles.detailByline}>{activeBook.author || activeBook.publisher || ''} · {activeBook.sourceVersion}</Text><Text style={styles.detailSummary}>{activeBook.summary}</Text>
      <Text style={styles.sectionTitle}>{textFor(english, 'Choose a language', '选择阅读语言')}</Text>
      <View style={styles.filterRow}>{availableLanguages.map(item => <Pressable key={item} onPress={() => setLanguage(item)} style={[styles.filter, language === item && styles.filterActive]}><Text style={[styles.filterText, language === item && styles.filterTextActive]}>{languageName(item, english)}</Text></Pressable>)}</View>
      <Text style={styles.smallCopy}>{textFor(english, 'Each edition shows its source and license. Editorial notes are kept separate from the original text.', '每个版本都会标明来源和许可。此间解说与原文分别呈现。')}</Text>
      <Pressable style={styles.primaryButton} onPress={() => void openReader()}><Text style={styles.primaryButtonText}>{textFor(english, 'Begin reading', '开始阅读')}</Text></Pressable>
      {readerState.progress.some(item => item.bookId === activeBook.id) && <Text style={styles.smallCopy}>{textFor(english, 'Your saved position is available in the selected edition.', '所选版本有已保存的阅读位置。')}</Text>}
    </ScrollView>}

    {screen === 'reader' && <>
      <ScrollView key={`${version?.versionId}:${version?.language}:${chapterIndex}`} contentContainerStyle={styles.readerContent} scrollEventThrottle={250} onScroll={updateProgressFromScroll}>
        {loading && !currentChapter ? <ActivityIndicator color="#687963" /> : null}
        {readerError ? <Text accessibilityRole="alert" style={styles.notice}>{readerError}</Text> : null}
        {readerError && !loading ? <Pressable style={styles.primaryButton} onPress={() => version ? openChapter(chapterIndex, activeParagraph) : void openReader()}><Text style={styles.primaryButtonText}>{textFor(readerEnglish, 'Retry', '重新加载')}</Text></Pressable> : null}
        {version && currentChapter && <>
          <Text style={styles.kicker}>{languageName(version.language, readerEnglish)} · {readerEnglish ? 'Reading edition' : version.versionLabel} · v{version.revisionNo}</Text>
          <Text style={styles.readerTitle}>{readerEnglish ? englishReadingTitle(version.title) : version.title}</Text>
          <Text style={styles.readerByline}>{!readerEnglish && `${version.author || version.publisher} · `}{textFor(readerEnglish, 'Chapter', '第')} {currentChapter.chapterNo}{version.isExcerpt ? ` · ${readerEnglish ? 'Selected passages' : version.excerptLabel}` : ''}</Text>
          <Text style={styles.chapterTitle}>{readerEnglish && /\p{Script=Han}/u.test(currentChapter.title) ? `Chapter ${currentChapter.chapterNo}` : currentChapter.title}</Text>
          {!readerEnglish && currentChapter.annotationLayer !== 'source' && currentChapter.sourceText !== currentChapter.text && <View style={styles.originalSection}>
            <Text style={styles.sectionTitle}>{textFor(readerEnglish, 'Original text', '原文')}</Text>
            {currentChapter.sourceText.split(/\n\s*\n/).filter(Boolean).map((paragraph, index) => <Text key={`original-${index}`} style={[styles.paragraph, { fontSize, lineHeight: Math.round(fontSize * 1.85) }]}>{paragraph}</Text>)}
          </View>}
          <Text style={styles.sectionTitle}>{textFor(readerEnglish, 'Reading text', currentChapter.annotationLayer === 'source' ? '汉文原文' : '译文正文')}</Text>
          {!!annotationRanges.length && <View style={styles.annotationLegend}>
            <Text style={styles.annotationHint}>{textFor(readerEnglish, 'Text above, notes below. Read at your own pace.', '上为正文，下为词句释义；按自己的速度读。')}</Text>
            <View style={styles.annotationLegendRow}><Text style={[styles.annotationLegendItem, { color: '#27988a' }]}>{textFor(readerEnglish, 'Word', '释词')}</Text><Text style={[styles.annotationLegendItem, { color: '#d84a42' }]}>{textFor(readerEnglish, 'Clause', '释句')}</Text><Text style={[styles.annotationLegendItem, { color: '#b7790b' }]}>{textFor(readerEnglish, 'Text variant', '异文')}</Text></View>
          </View>}
          {paragraphs.map(({ text: paragraph, start }, index) => {
            const anchor = currentAnchor(index + 1); const bookmarked = !!anchor && readerState.bookmarks.some(item => anchorKey(item) === anchorKey(anchor));
            const paragraphEnd = start + paragraph.length;
            const matches = annotationRanges.filter(item => item.start >= start && item.end <= paragraphEnd);
            const parts: any[] = []; let cursor = start;
            for (const item of currentChapter.annotationLayer === 'source' ? [] : matches) {
              if (cursor < item.start) parts.push(currentChapter.text.slice(cursor, item.start));
              parts.push(<Text key={`annotation-${item.id}`} style={{ color: annotationColor(item.tone) }} accessibilityRole="link" accessibilityLabel={`${item.phrase}: ${item.note}`} onPress={() => setSelectedAnnotationId(value => value === item.id ? null : item.id)}>{item.phrase}</Text>);
              cursor = item.end;
            }
            if (cursor < paragraphEnd) parts.push(currentChapter.text.slice(cursor, paragraphEnd));
            const selected = matches.find(item => item.id === selectedAnnotationId);
            return <View key={`${currentChapter.id}:${index}`} style={styles.paragraphRow} onLayout={event => { paragraphOffsets.current[index] = event.nativeEvent.layout.y; }}><Pressable style={styles.paragraphBody} onPress={() => { setActiveParagraph(index + 1); saveProgress(currentChapter.chapterNo, index + 1); }}>
              {(readerEnglish || currentChapter.annotationLayer === 'source') ? <View style={[styles.annotationPassage, activeParagraph === index + 1 && styles.paragraphSelected]}>
                {annotationUnits(paragraph, matches, start).flatMap(unit => readerEnglish && !unit.note ? (unit.original.match(/\S+\s*/g) || []).map(original => ({ original } as AnnotatedUnit)) : [unit]).map((unit, unitIndex) => <View key={unitIndex} style={[styles.annotationUnit, readerEnglish ? (unit.note ? { width: Math.min(280, Math.max(110, unit.original.length * fontSize * .55 + 12)) } : { flexShrink: 0 }) : { width: annotationUnitWidth(unit, fontSize) }]}>
                  <Text style={[styles.annotationOriginal, { fontSize: fontSize + 2, lineHeight: Math.round((fontSize + 2) * 1.4) }]}>{unit.original}</Text>
                  {unit.note && <Text style={[styles.annotationMeaning, { color: annotationColor(unit.tone), borderBottomColor: annotationColor(unit.tone) + '30', fontSize: Math.max(12, Math.round(fontSize * .62 + 1)), lineHeight: Math.max(17, Math.round(fontSize * .9 + 1)) }]}>{unit.note}</Text>}
                </View>)}
              </View> : <Text style={[styles.paragraph, { fontSize, lineHeight: Math.round(fontSize * 1.75) }, activeParagraph === index + 1 && styles.paragraphSelected]}>{parts}</Text>}
              {selected && <View style={[styles.inlineNote, { borderLeftColor: annotationColor(selected.tone) }]}><Text style={[styles.inlineNotePhrase, { color: annotationColor(selected.tone) }]}>{selected.phrase}</Text><Text style={styles.inlineNoteText}>{selected.note}</Text></View>}
            </Pressable><Pressable accessibilityRole="button" accessibilityLabel={textFor(readerEnglish, 'Bookmark paragraph', '添加书签')} style={styles.bookmarkButton} onPress={() => { if (anchor) toggleBookmark(anchor); }}><Text style={[styles.bookmarkGlyph, bookmarked && styles.bookmarkOn]}>{bookmarked ? '★' : '☆'}</Text></Pressable></View>;
          })}
          {!readerEnglish && currentChapter.annotationLayer === 'source' && <View style={styles.originalSection}>
            <Text style={styles.sectionTitle}>{textFor(readerEnglish, 'Modern Chinese rendering', '白话译文')}</Text>
            {currentChapter.translationText.split(/\n\s*\n/).filter(Boolean).map((paragraph, index) => <Text key={`vernacular-${index}`} style={[styles.paragraph, { fontSize, lineHeight: Math.round(fontSize * 1.85) }]}>{paragraph}</Text>)}
          </View>}
          {!!currentChapter.commentary && <View style={styles.commentary}><Text style={styles.interpretationTitle}>{textFor(readerEnglish, 'Chapter interpretation', '章节解读')}</Text>{currentChapter.commentary.split(/\n\s*\n/).filter(Boolean).map((paragraph, index) => <Text key={`interpretation-${index}`} style={[styles.commentaryText, index > 0 && styles.commentaryFollowing]}>{paragraph}</Text>)}</View>}
          <Text style={styles.endMark}>○</Text>
          <View style={styles.chapterNav}><Pressable disabled={chapterIndex <= 0} onPress={() => openChapter(chapterIndex - 1)}><Text style={[styles.navText, chapterIndex <= 0 && styles.navDisabled]}>{textFor(readerEnglish, 'Previous', '上一章')}</Text></Pressable><Text style={styles.chapterCount}>{chapterIndex + 1} / {version.chapters.length}</Text><Pressable disabled={chapterIndex >= version.chapters.length - 1} onPress={() => openChapter(chapterIndex + 1)}><Text style={[styles.navText, chapterIndex >= version.chapters.length - 1 && styles.navDisabled]}>{textFor(readerEnglish, 'Next', '下一章')}</Text></Pressable></View>
        </>}
      </ScrollView>
        {version && sourceOpen && <View style={styles.sourceSheet}><Pressable style={styles.sourceClose} onPress={() => setSourceOpen(false)}><Text style={styles.navText}>{textFor(readerEnglish, 'Close', '收起')}</Text></Pressable><Text style={styles.noteLabel}>{textFor(readerEnglish, 'Edition & source', '版本与出处')}</Text><Text style={styles.sourceText}>{version.author || version.publisher} · {version.versionLabel} · {textFor(readerEnglish, 'Source edition', '来源版本')} {version.sourceVersionNo}</Text><Text style={styles.sourceText}>{version.language === version.sourceLanguage ? textFor(readerEnglish, 'Original language edition', '原文版本') : version.modelNote ? textFor(readerEnglish, `AI-assisted translation (${version.modelNote}); human review pending`, `AI 辅助翻译（${version.modelNote}），人工逐章校对待完成`) : textFor(readerEnglish, 'AI-assisted translation; human review pending', 'AI 辅助翻译，人工逐章校对待完成')}</Text><Text style={styles.sourceText}>{version.translatorNote || version.editorNote}</Text><Text style={styles.sourceText}>{version.licenseName} · {version.licenseHolder} · {version.licenseTerm}</Text>{version.sourceUrl ? <Text style={styles.sourceLink} onPress={() => void Linking.openURL(version.sourceUrl)}>{version.sourceUrl}</Text> : null}</View>}
    </>}

    {screen === 'bookmarks' && <ScrollView contentContainerStyle={styles.content}><Text style={styles.title}>{textFor(english, 'Saved passages', '我的书签')}</Text>
      {!readerState.bookmarks.length ? <Text style={styles.empty}>{textFor(english, 'No bookmarks yet.', '还没有书签。')}</Text> : readerState.bookmarks.slice().reverse().map(item => {
        const book = books.find(candidate => candidate.id === item.bookId); const chapter = book?.releases.find(release => release.id === item.versionId);
        return <View key={anchorKey(item)} style={styles.bookmarkRow}><Pressable style={styles.bookmarkJump} onPress={() => jumpBookmark(item)}><Text style={styles.bookTitle}>{book?.title || textFor(english, 'Removed from shelf', '书籍已从书架移除')}</Text><Text style={styles.byline}>{languageName(item.language, english)} · {textFor(english, `Chapter ${item.chapterNo}, paragraph ${item.paragraphNo}`, `第 ${item.chapterNo} 章，第 ${item.paragraphNo} 段`)}</Text><Text style={styles.byline}>{chapter ? `v${chapter.revisionNo}` : ''}</Text></Pressable><Pressable onPress={() => toggleBookmark(item)}><Text style={styles.deleteBookmark}>×</Text></Pressable></View>;
      })}
    </ScrollView>}

    {syncPrompt && <View style={styles.promptBackdrop}><View style={styles.promptCard}><Text style={styles.sectionTitle}>{syncPrompt === 'enable' ? textFor(english, 'Sync reading data?', '开启跨设备同步？') : textFor(english, 'Turn off account sync?', '关闭跨设备同步？')}</Text><Text style={styles.smallCopy}>{syncPrompt === 'enable' ? textFor(english, 'Only reading positions and bookmark locations will sync to your account. Book text and notes are not uploaded as personal data.', '只会同步阅读位置和书签锚点，不会把书籍正文或私人批注作为个人数据上传。') : textFor(english, 'Turning sync off also deletes the account copy. This device’s saved positions and bookmarks remain.', '关闭同步会同时删除账号中的阅读位置和书签；本机副本会保留。')}</Text><View style={styles.bookActions}><Pressable style={styles.secondaryButton} onPress={() => setSyncPrompt(null)}><Text style={styles.secondaryText}>{textFor(english, 'Cancel', '取消')}</Text></Pressable><Pressable style={styles.primaryButton} disabled={busy} onPress={() => syncPrompt === 'enable' ? void enableSync() : void disableSync()}><Text style={styles.primaryButtonText}>{busy ? '…' : syncPrompt === 'enable' ? textFor(english, 'Enable', '开启') : textFor(english, 'Turn off and clear account copy', '关闭并删除账号副本')}</Text></Pressable></View></View></View>}
  </View>;
}

const styles = StyleSheet.create({
  originalSection: { paddingTop: 16, paddingBottom: 10, gap: 12 },
  root: { flex: 1, backgroundColor: '#f5f4ed' },
  topBar: { minHeight: 54, paddingHorizontal: 16, borderBottomWidth: 1, borderBottomColor: '#e5e3d8', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#f8f7f1' },
  backButton: { paddingVertical: 12, minWidth: 74 }, backText: { color: '#52654f', fontSize: 13 }, brand: { color: '#3d4d3c', fontFamily: Platform.OS === 'ios' ? 'Georgia' : 'serif', fontSize: 17 },
  topLink: { minWidth: 74, alignItems: 'flex-end', paddingVertical: 12 }, topLinkText: { color: '#65745e', fontSize: 12 }, readerTools: { flexDirection: 'row', alignItems: 'center', gap: 12 }, content: { padding: 22, paddingBottom: 48, maxWidth: 860, width: '100%', alignSelf: 'center' },
  intro: { paddingTop: 22, paddingBottom: 24 }, kicker: { color: '#8a8c7b', fontSize: 10, letterSpacing: 2, marginBottom: 12 }, title: { fontFamily: Platform.OS === 'ios' ? 'Georgia' : 'serif', fontSize: 28, lineHeight: 37, color: '#39473a' }, subtitle: { color: '#74796d', fontSize: 14, lineHeight: 22, marginTop: 10 },
  filterRow: { flexDirection: 'row', gap: 8, flexWrap: 'wrap', marginBottom: 18 }, filter: { borderWidth: 1, borderColor: '#dadbd0', borderRadius: 22, paddingVertical: 9, paddingHorizontal: 14 }, filterActive: { backgroundColor: '#5c705b', borderColor: '#5c705b' }, filterText: { color: '#667064', fontSize: 12 }, filterTextActive: { color: '#fffdf7' },
  bookCard: { flexDirection: 'row', alignItems: 'center', gap: 14, backgroundColor: '#fffefa', padding: 14, borderRadius: 15, marginBottom: 12, borderWidth: 1, borderColor: '#e9e7dc' }, cover: { width: 65, height: 88, borderTopRightRadius: 8, borderBottomRightRadius: 8, justifyContent: 'space-between', alignItems: 'center', paddingVertical: 10, borderLeftWidth: 4, borderLeftColor: '#d8cdb4' }, coverMark: { color: '#f6f1e4', fontSize: 20 }, coverLabel: { color: '#f6f1e4', fontSize: 9 }, bookCardText: { flex: 1, gap: 5 }, bookTitle: { color: '#394539', fontFamily: Platform.OS === 'ios' ? 'Georgia' : 'serif', fontSize: 17 }, byline: { color: '#899083', fontSize: 11 }, bookSummary: { color: '#687166', fontSize: 12, lineHeight: 18 }, editionLine: { color: '#6c7b68', fontSize: 10, marginTop: 3 }, arrow: { color: '#8c9989', fontSize: 18 },
  privacyBox: { marginTop: 24, padding: 16, backgroundColor: '#eeefe8', borderRadius: 14, gap: 10 }, sectionTitle: { color: '#455543', fontSize: 14, fontWeight: '600' }, smallCopy: { color: '#73796e', fontSize: 12, lineHeight: 18 }, settingRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 8, borderTopWidth: 1, borderTopColor: '#dedfd4' }, settingText: { color: '#586653', fontSize: 13 }, switch: { fontSize: 10, color: '#899083', borderWidth: 1, borderColor: '#c8cbbf', borderRadius: 12, paddingVertical: 4, paddingHorizontal: 9 }, switchOn: { backgroundColor: '#647861', color: '#fff', borderColor: '#647861' }, clearText: { color: '#9b6758', fontSize: 11, paddingVertical: 4 },
  notice: { color: '#8c6454', fontSize: 12, lineHeight: 18, padding: 12, backgroundColor: '#f3eae2', borderRadius: 10, marginBottom: 14 }, empty: { color: '#828779', fontSize: 14, textAlign: 'center', paddingVertical: 36 }, detailCover: { alignSelf: 'center', width: 170, height: 230, backgroundColor: '#7d8c7a', borderTopRightRadius: 12, borderBottomRightRadius: 12, borderLeftWidth: 8, borderLeftColor: '#d3c7aa', justifyContent: 'space-between', alignItems: 'center', padding: 20, marginVertical: 12 }, detailCoverTitle: { fontFamily: Platform.OS === 'ios' ? 'Georgia' : 'serif', color: '#f4f0e5', fontSize: 22, textAlign: 'center' }, detailByline: { textAlign: 'center', color: '#71786e', fontSize: 12, marginTop: 10 }, detailSummary: { color: '#586356', fontSize: 15, lineHeight: 24, marginTop: 18, marginBottom: 24 }, primaryButton: { alignItems: 'center', justifyContent: 'center', backgroundColor: '#5d735e', borderRadius: 24, paddingVertical: 13, paddingHorizontal: 18, minWidth: 120 }, primaryButtonText: { color: '#fffdf7', fontSize: 13, fontWeight: '600' },
  readerContent: { paddingHorizontal: 24, paddingTop: 28, paddingBottom: 60, maxWidth: 720, width: '100%', alignSelf: 'center' }, readerTitle: { fontFamily: Platform.OS === 'ios' ? 'Georgia' : 'serif', fontSize: 25, lineHeight: 34, color: '#3d4b3c', marginTop: 4 }, readerByline: { color: '#818677', fontSize: 11, marginTop: 8 }, chapterTitle: { color: '#4b5d49', fontFamily: Platform.OS === 'ios' ? 'Georgia' : 'serif', fontSize: 19, marginTop: 30, marginBottom: 18 }, paragraphRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 8 }, paragraphBody: { flex: 1 }, paragraph: { color: '#393f38', fontSize: 16, lineHeight: 28, marginBottom: 14 }, paragraphSelected: { backgroundColor: '#edece1' }, bookmarkButton: { width: 30, alignItems: 'center', paddingVertical: 4 }, bookmarkGlyph: { color: '#9aa08f', fontSize: 18 }, bookmarkOn: { color: '#9a8350' }, commentary: { backgroundColor: '#eee8dc', paddingHorizontal: 16, paddingVertical: 18, marginTop: 28, marginBottom: 20 }, notes: { paddingTop: 22, gap: 9 }, noteLabel: { color: '#74806d', fontSize: 10, letterSpacing: 1 }, commentaryText: { color: '#5f685c', fontSize: 13, lineHeight: 21, marginTop: 7 }, annotation: { color: '#626b5f', fontSize: 12, lineHeight: 19 }, annotationTerm: { color: '#445541', fontWeight: '600' }, endMark: { color: '#9aa18d', fontSize: 19, textAlign: 'center', marginTop: 34 }, chapterNav: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 20, marginTop: 14, borderTopWidth: 1, borderColor: '#e2e1d7' }, navText: { color: '#5d725c', fontSize: 12 }, navDisabled: { color: '#c0c2b8' }, chapterCount: { color: '#929687', fontSize: 11 }, sourceSheet: { position: 'absolute', left: 12, right: 12, bottom: 12, padding: 18, borderRadius: 16, backgroundColor: '#fffefa', borderWidth: 1, borderColor: '#e3e1d6', gap: 8 }, sourceClose: { alignSelf: 'flex-end' }, sourceText: { color: '#676f61', fontSize: 12, lineHeight: 18 }, sourceLink: { color: '#61765e', fontSize: 11 },
  annotationLegend: { marginTop: 14, marginBottom: 18, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#e7e2d7', gap: 6 },
  annotationHint: { color: '#77746b', fontSize: 11 },
  annotationLegendRow: { flexDirection: 'row', gap: 14 },
  annotationLegendItem: { fontSize: 11 },
  annotationPassage: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'flex-start', columnGap: 4, rowGap: 8, marginBottom: 18 },
  annotationUnit: { maxWidth: '100%', alignItems: 'center' },
  annotationOriginal: { color: '#292721', fontFamily: Platform.OS === 'ios' ? 'Georgia' : 'serif', textAlign: 'center' },
  annotationMeaning: { alignSelf: 'stretch', textAlign: 'center', borderBottomWidth: 1, paddingHorizontal: 1, paddingBottom: 3 },
  inlineNote: { marginTop: 8, marginBottom: 12, paddingLeft: 10, borderLeftWidth: 2, gap: 3 },
  inlineNotePhrase: { fontSize: 12, fontWeight: '600' },
  inlineNoteText: { color: '#625e55', fontSize: 12, lineHeight: 18 },
  interpretationTitle: { color: '#795f43', fontSize: 16, fontWeight: '600', marginBottom: 4 },
  commentaryFollowing: { marginTop: 9, paddingLeft: 12 },
  bookmarkRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 13, borderBottomWidth: 1, borderBottomColor: '#e4e3d9', gap: 10 }, bookmarkJump: { flex: 1, gap: 4 }, deleteBookmark: { color: '#a06858', fontSize: 22, paddingHorizontal: 10 }, promptBackdrop: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: '#29332acc', alignItems: 'center', justifyContent: 'center', padding: 22 }, promptCard: { width: '100%', maxWidth: 440, backgroundColor: '#fffefa', borderRadius: 18, padding: 20, gap: 14 }, bookActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 10, flexWrap: 'wrap' }, secondaryButton: { justifyContent: 'center', paddingVertical: 12, paddingHorizontal: 18, borderRadius: 24, borderWidth: 1, borderColor: '#dedfd4' }, secondaryText: { color: '#5c6857', fontSize: 13 },
});
