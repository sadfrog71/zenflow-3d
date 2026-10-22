import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, BackHandler, Pressable, StyleSheet, Text, View } from 'react-native';
import { WebView } from '@/platform/zenflow/web-view';
import source from './pond-source.json';

type Props = { english: boolean; onExit: () => void; onSeated: (value: boolean) => void };

export function Pond({ english, onExit, onSeated }: Props) {
  const view = useRef<import('react-native-webview').WebView>(null);
  const [reduced, setReduced] = useState(false);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const deadline = useRef<ReturnType<typeof setTimeout> | null>(null);
  const clearDeadline = () => { if (deadline.current) clearTimeout(deadline.current); deadline.current = null; };
  const inject = (data: object) => view.current?.injectJavaScript(`window.pondNative?.(${JSON.stringify(data)});true;`);
  const sync = () => inject({ action: 'settings', lang: english ? 'en' : 'zh', reduce: reduced, active: AppState.currentState === 'active' });
  const fail = () => { clearDeadline(); inject({ action: 'background' }); setFailed(true); };
  const exit = () => { inject({ action: 'background' }); onExit(); };
  const syncLatest = useEffectEvent(sync);
  const exitLatest = useEffectEvent(exit);

  useEffect(() => {
    let alive = true;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => { if (alive) setReduced(value); });
    const listener = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => { alive = false; listener.remove(); };
  }, []);
  useEffect(sync, [english, reduced, attempt]);
  useEffect(() => {
    onSeated(true);
    const app = AppState.addEventListener('change', value => {
      if (value !== 'active') inject({ action: 'background' }); else syncLatest();
    });
    const back = BackHandler.addEventListener('hardwareBackPress', () => { exitLatest(); return true; });
    return () => { clearDeadline(); inject({ action: 'background' }); app.remove(); back.remove(); onSeated(false); };
  }, [onSeated]);
  useEffect(() => {
    if (!failed) deadline.current = setTimeout(() => { inject({ action: 'background' }); setFailed(true); }, 25000);
    return clearDeadline;
  }, [attempt, failed]);

  return <View style={styles.fill}>{failed ? <View style={styles.fallback}>
    <Text style={styles.text}>{english ? 'The pond could not open. Please try again.' : '水庭暂时无法打开，请重试。'}</Text>
    <Pressable accessibilityRole="button" style={styles.button} onPress={() => { setFailed(false); setAttempt(value => value + 1); }}><Text style={styles.text}>{english ? 'Try again' : '重新打开'}</Text></Pressable>
    <Pressable accessibilityRole="button" style={styles.button} onPress={exit}><Text style={styles.text}>{english ? 'Back' : '返回首页'}</Text></Pressable>
  </View> : <WebView key={`${source.revision}-${attempt}`} ref={view} source={source} originWhitelist={['about:blank']}
    javaScriptEnabled domStorageEnabled={false} allowsInlineMediaPlayback mediaPlaybackRequiresUserAction
    scrollEnabled={false} bounces={false} overScrollMode="never"
    onContentProcessDidTerminate={fail} onRenderProcessGone={fail}
    onLoadEnd={sync} onError={fail}
    onMessage={event => {
      try {
        const data = JSON.parse(event.nativeEvent.data);
        if (data.type === 'ready') { clearDeadline(); sync(); }
        if (data.type === 'failed') fail();
        if (data.type === 'exit') exit();
      } catch { /* Ignore malformed scene messages. */ }
    }} style={styles.fill} />}</View>;
}

const styles = StyleSheet.create({ fill: { flex: 1, backgroundColor: '#f5f4ed' }, fallback: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 16 }, text: { color: '#365a49', textAlign: 'center' }, button: { minHeight: 44, padding: 12 } });
