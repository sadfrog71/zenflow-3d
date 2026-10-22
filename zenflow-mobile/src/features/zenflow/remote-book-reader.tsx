import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import type { WebView as NativeWebView } from 'react-native-webview';
import { WebView } from '@/platform/zenflow/web-view';
import type { ZenflowConfig } from '@/core/zenflow/config';
import type { BookVersion } from './book-shelf';

type Props = {
  config: ZenflowConfig | null; edition: BookVersion | null; chapterIndex: number; fontSize: number; english: boolean;
  initialParagraph: number; bookmarkedParagraphs: number[]; children: ReactNode;
  onPosition: (paragraph: number) => void; onBookmark: (paragraph: number) => void;
  onChapter: (direction: -1 | 1) => void; onFallback: (paragraph: number) => void;
};
type Selection = { uri: string; revision: string };

async function selectReader(origin: string, controller: AbortController): Promise<Selection> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      (async () => {
        const response = await fetch(`${origin}/reading-room/reader/manifest.json`, { credentials: 'omit', signal: controller.signal, headers: { Accept: 'application/json', 'Cache-Control': 'no-cache' } });
        if (!response.ok) throw new Error('READER_MANIFEST_UNAVAILABLE');
        const manifest = await response.json();
        if (manifest.protocol !== 1 || typeof manifest.revision !== 'string' || !/^[a-f0-9]{16}$/.test(manifest.revision)) throw new Error('READER_PROTOCOL_UNSUPPORTED');
        return { uri: `${origin}/reading-room/reader/releases/${manifest.revision}/index.html`, revision: manifest.revision };
      })(),
      new Promise<never>((_, reject) => { timer = setTimeout(() => { reject(new Error('READER_MANIFEST_TIMEOUT')); controller.abort(); }, 3000); }),
    ]);
  } finally { if (timer) clearTimeout(timer); }
}

export function RemoteBookReader(props: Props) {
  const { edition, chapterIndex, config } = props;
  const view = useRef<NativeWebView>(null), latest = useRef(props); latest.current = props;
  const live = useRef(true), sequence = useRef(0), position = useRef(props.initialParagraph);
  const session = useRef(`reader-${Date.now()}-${Math.random().toString(36).slice(2)}`);
  const deadline = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [fallback, setFallback] = useState(false), [ready, setReady] = useState(false);
  const connected = useRef(false), usingFallback = useRef(false);
  const chapter = edition?.chapters[chapterIndex];
  const previousChapter = useRef(chapter?.id);
  if (previousChapter.current !== chapter?.id) { previousChapter.current = chapter?.id; position.current = props.initialParagraph; }
  const clearDeadline = () => { if (deadline.current) clearTimeout(deadline.current); deadline.current = null; };
  const useFallback = () => {
    if (!live.current || usingFallback.current) return;
    usingFallback.current = true; clearDeadline(); connected.current = false;
    latest.current.onFallback(position.current); setFallback(true);
  };
  useEffect(() => {
    live.current = true;
    const controller = new AbortController();
    if (!config || !edition) { useFallback(); return () => { live.current = false; }; }
    void selectReader(config.origin, controller).then(value => { if (live.current) setSelection(value); }).catch(() => { if (live.current) useFallback(); });
    return () => { live.current = false; controller.abort(); clearDeadline(); };
  }, [config?.origin, edition?.id, edition?.versionId, edition?.language]);

  const bookmarkKey = props.bookmarkedParagraphs.join(',');
  const payload = useMemo(() => edition && chapter ? {
    protocol: 1, type: 'render', session: session.current,
    english: props.english, fontSize: props.fontSize, bookmarkedParagraphs: props.bookmarkedParagraphs,
    chapterIndex, chapterCount: edition.chapters.length, chapter,
    edition: { id: edition.id, versionId: edition.versionId, title: edition.title, author: edition.author || edition.publisher, versionLabel: edition.versionLabel, revisionNo: edition.revisionNo, language: edition.language, isExcerpt: edition.isExcerpt, excerptLabel: edition.excerptLabel },
  } : null, [edition, chapterIndex, props.english, props.fontSize, bookmarkKey]);
  const payloadRef = useRef(payload); payloadRef.current = payload;
  const send = () => {
    if (!payloadRef.current || !live.current || !connected.current || usingFallback.current) return;
    view.current?.postMessage(JSON.stringify({ ...payloadRef.current, seq: ++sequence.current, paragraph: position.current }));
  };
  useEffect(() => { send(); }, [payload]);
  useEffect(() => {
    if (selection && !fallback) deadline.current = setTimeout(useFallback, 8000);
    return clearDeadline;
  }, [selection, fallback]);

  if (!edition || fallback) return <>{props.children}</>;
  return <View style={styles.shell}>
    {selection && <WebView ref={view} source={{ uri: selection.uri }} style={[styles.web, !ready && styles.hidden]}
      originWhitelist={[config!.origin]} sharedCookiesEnabled={false} thirdPartyCookiesEnabled={false} incognito
      javaScriptEnabled domStorageEnabled={false} setSupportMultipleWindows={false}
      onShouldStartLoadWithRequest={request => request.url === selection.uri}
      onError={useFallback} onHttpError={event => { if (event.nativeEvent.statusCode >= 400) useFallback(); }}
      onContentProcessDidTerminate={useFallback} onRenderProcessGone={useFallback}
      onMessage={event => {
        if (!live.current || usingFallback.current || typeof event.nativeEvent.data !== 'string' || event.nativeEvent.data.length > 2048) return;
        try {
          const message = JSON.parse(event.nativeEvent.data);
          if (message.protocol !== 1) return;
          if (message.type === 'ready') {
            if (message.revision !== selection.revision) { useFallback(); return; }
            connected.current = true; send(); return;
          }
          const current = latest.current, currentChapter = current.edition?.chapters[current.chapterIndex];
          if (message.session !== session.current || message.seq !== sequence.current || message.chapterId !== currentChapter?.id) return;
          if (message.type === 'rendered') { clearDeadline(); setReady(true); return; }
          if (message.type === 'error') { useFallback(); return; }
          if (message.type === 'chapter' && (message.direction === -1 || message.direction === 1)) {
            if (current.edition && current.chapterIndex + message.direction >= 0 && current.chapterIndex + message.direction < current.edition.chapters.length) current.onChapter(message.direction);
            return;
          }
          const count = currentChapter?.text.split(/\n\s*\n/).filter(part => part.trim()).length || 0;
          if (!Number.isInteger(message.paragraph) || message.paragraph < 1 || message.paragraph > count) return;
          if (message.type === 'progress') { position.current = message.paragraph; current.onPosition(message.paragraph); }
          if (message.type === 'bookmark') current.onBookmark(message.paragraph);
        } catch { /* Ignore messages outside the reader protocol. */ }
      }} />}
    {!ready && <View pointerEvents="none" style={styles.waiting}><ActivityIndicator color="#687963" /></View>}
  </View>;
}

const styles = StyleSheet.create({ shell: { flex: 1 }, web: { flex: 1, backgroundColor: '#f8f7f1' }, hidden: { opacity: 0 }, waiting: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' } });
