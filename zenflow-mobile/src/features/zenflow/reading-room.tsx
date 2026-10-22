import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { WebView } from '@/platform/zenflow/web-view';
import type { ZenflowConfig } from '@/core/zenflow/config';
import { readingRoomOrigins, readingRoomRevision, readingRoomSource } from './reading-room-files';

type Props = { english: boolean; config: ZenflowConfig | null; onBooks: () => void; onExit: () => void };

export function ReadingRoom({ english, config, onBooks, onExit }: Props) {
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const deadline = useRef<ReturnType<typeof setTimeout> | null>(null);
  const clearDeadline = () => { if (deadline.current) clearTimeout(deadline.current); deadline.current = null; };
  useEffect(() => () => clearDeadline(), []);

  const source = useMemo(() => readingRoomSource(english, config), [english, config]);

  return (
    <View style={styles.shell}>
      {source && !failed ? <>
        <WebView
          key={`${readingRoomRevision}:${english}:${attempt}`}
          source={source}
          originWhitelist={readingRoomOrigins(config)}
          javaScriptEnabled
          domStorageEnabled={false}
          scrollEnabled={false}
          bounces={false}
          allowsInlineMediaPlayback
          mediaPlaybackRequiresUserAction={false}
          onLoadStart={() => { clearDeadline(); deadline.current = setTimeout(() => setFailed(true), 30000); }}
          onError={() => { clearDeadline(); setFailed(true); }}
          onContentProcessDidTerminate={() => { clearDeadline(); setFailed(true); }}
          onHttpError={event => { if (event.nativeEvent.statusCode >= 400) { clearDeadline(); setFailed(true); } }}
          onMessage={event => {
            try {
              const data = JSON.parse(event.nativeEvent.data);
              if (data.type === 'books') onBooks();
              if (data.type === 'exit') onExit();
              if (data.type === 'scene-diagnostic' && data.stage === 'render-ready') clearDeadline();
              if (data.type === 'scene-diagnostic' && data.stage === 'failed') { clearDeadline(); setFailed(true); }
              if (__DEV__ && data.type === 'scene-diagnostic') console.info('Reading room:', data.stage, data.message || '');
            } catch { /* Ignore messages from the page unless they match this bridge. */ }
          }}
          style={styles.web}
        />
        <Pressable accessibilityRole="button" accessibilityLabel={english ? 'Back to home' : '返回首页'} style={styles.exitShortcut} onPress={onExit}>
          <Text style={styles.shelfShortcutText}>←</Text>
        </Pressable>
        <Pressable accessibilityRole="button" style={styles.shelfShortcut} onPress={onBooks}>
          <Text style={styles.shelfShortcutText}>{english ? 'Open bookshelf ↗' : '选一本书 ↗'}</Text>
        </Pressable>
      </> : <View style={styles.fallback}>
        <Text style={styles.message}>{english ? 'The reading room is unavailable right now.' : '读经室暂时无法打开。'}</Text>
        <Pressable accessibilityRole="button" style={styles.button} onPress={onBooks}>
          <Text style={styles.buttonText}>{english ? 'Open the bookshelf' : '直接打开书架'}</Text>
        </Pressable>
        {source ? <Pressable accessibilityRole="button" onPress={() => { setFailed(false); setAttempt(value => value + 1); }}>
          <Text style={styles.link}>{english ? 'Try again' : '重新打开读经室'}</Text>
        </Pressable> : null}
        <Pressable accessibilityRole="button" onPress={onExit}>
          <Text style={styles.link}>{english ? 'Home' : '返回首页'}</Text>
        </Pressable>
      </View>}
    </View>
  );
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: '#eee9dc' },
  web: { flex: 1, backgroundColor: 'transparent' },
  fallback: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 18, backgroundColor: '#f5f4ed' },
  message: { color: '#365a49', textAlign: 'center', fontSize: 15 },
  button: { minHeight: 46, paddingHorizontal: 20, justifyContent: 'center', borderRadius: 24, backgroundColor: '#365a49' },
  buttonText: { color: '#fff', fontSize: 14 },
  shelfShortcut: { position: 'absolute', bottom: 40, left: '50%', width: 180, minHeight: 50, marginLeft: -90, alignItems: 'center', justifyContent: 'center', backgroundColor: '#f8f5e8', borderRadius: 4, elevation: 3 },
  shelfShortcutText: { color: '#465548', fontSize: 16 },
  exitShortcut: { position: 'absolute', top: 16, left: 18, width: 44, height: 44, alignItems: 'center', justifyContent: 'center', backgroundColor: '#f8f5e8', borderRadius: 22, elevation: 3 },
  link: { color: '#365a49', fontSize: 13, padding: 8 },
});
