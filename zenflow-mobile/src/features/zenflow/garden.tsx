import { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, ActivityIndicator, AppState, Pressable, StyleSheet, Text, View } from 'react-native';
import { WebView } from '@/platform/zenflow/web-view';
import { prepareGarden } from './garden-files';

type Props = { english: boolean; onSeated: (value: boolean) => void; onWrite: () => void; onDiagnostic?: (value: string) => void };
export function Garden({ english, onSeated, onWrite, onDiagnostic }: Props) {
  const [file, setFile] = useState<{uri: string; directory: string; html?: string} | null>(null);
  const [failed, setFailed] = useState(false), [attempt, setAttempt] = useState(0);
  const view = useRef<import('react-native-webview').WebView>(null);
  const deadline = useRef<ReturnType<typeof setTimeout> | null>(null);
  const clearDeadline = () => { if (deadline.current) clearTimeout(deadline.current); deadline.current = null; };
  useEffect(() => () => clearDeadline(), []);
  const inject = (data: object) => view.current?.injectJavaScript(`window.gardenNative?.(${JSON.stringify(data)}); true;`);
  useEffect(() => { let alive = true; prepareGarden().then(value => { if (alive) setFile(value); }).catch(error => { if (alive) { onDiagnostic?.(String(error)); setFailed(true); } }); return () => { alive = false; }; }, [attempt]);
  useEffect(() => { inject({ action: 'language', lang: english ? 'en' : 'zh' }); }, [english]);
  useEffect(()=>{void AccessibilityInfo.isReduceMotionEnabled().then(reduce=>inject({action:'reduce',reduce}));const listener=AccessibilityInfo.addEventListener('reduceMotionChanged',reduce=>inject({action:'reduce',reduce}));return()=>listener.remove();},[file]);
  useEffect(() => { const app = AppState.addEventListener('change', state => { if (state !== 'active') { inject({ action: 'background' }); onSeated(false); } }); return () => { app.remove(); inject({ action: 'background' }); onSeated(false); }; }, [onSeated]);
  return <View style={styles.shell}>{file && !failed ? <WebView key={attempt} ref={view} source={file.html ? { html: file.html } : { uri: file.uri }} allowingReadAccessToURL={file.directory}
    originWhitelist={['file://*']} javaScriptEnabled domStorageEnabled={false} allowsInlineMediaPlayback mediaPlaybackRequiresUserAction={false}
    onLoadStart={() => { clearDeadline(); deadline.current = setTimeout(() => { setFailed(true); onSeated(false); }, 25000); }}
    onLoadEnd={() => { inject({ action: 'entry', lang: english ? 'en' : 'zh', active:AppState.currentState==='active' }); void AccessibilityInfo.isReduceMotionEnabled().then(reduce => inject({ action: 'reduce', reduce })); }}
    onMessage={event => { try { const d = JSON.parse(event.nativeEvent.data); if (d.type === 'ready') clearDeadline(); if (d.type === 'failed') { onDiagnostic?.(d.reason || 'SCENE_FAILED'); clearDeadline(); setFailed(true); onSeated(false); } if (d.type === 'seated') onSeated(!!d.seated); if (d.type === 'write') onWrite(); } catch {} }}
    onError={event => { onDiagnostic?.(event.nativeEvent.description); clearDeadline(); setFailed(true); onSeated(false); }} style={styles.web} /> : <View style={styles.fallback}>{failed ? <><Text style={styles.text}>{english ? 'The garden could not open. Please try again.' : '庭院暂未打开，请重试。'}</Text><Pressable accessibilityRole="button" onPress={() => {setFailed(false);setAttempt(value => value + 1);}}><Text style={styles.text}>{english ? 'Try again' : '重新打开'}</Text></Pressable></> : <ActivityIndicator color="#365a49" />}</View>}</View>;
}
const styles = StyleSheet.create({ shell: { flex: 1, backgroundColor: '#dce0d4' }, web: { flex: 1, backgroundColor: 'transparent' }, fallback: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 20 }, text: { color: '#365a49', textAlign: 'center' } });
