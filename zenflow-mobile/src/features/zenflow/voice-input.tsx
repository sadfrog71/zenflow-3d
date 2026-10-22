import { audioCoordinator, type AudioLease } from '@/core/zenflow/audio-coordinator';
import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react';
import { AppState, Platform, Keyboard, Modal, Pressable, StyleSheet, Text } from 'react-native';
import { speech } from '@/platform/zenflow/speech';
type SpeechSubscription = { remove: () => void };
import { SafeAreaView } from 'react-native-safe-area-context';
import { WebView } from '@/platform/zenflow/web-view';
import source from './voice-source.json';
import { browserSpeechError } from './voice-errors';
import Svg, { Path, Rect } from 'react-native-svg';
import { correctSpeechTranscript, type VoiceSentenceCorrector } from './voice-correction';

export function addVoiceShortcutListener(listener: () => void): SpeechSubscription | undefined {
  return speech?.addListener('onVoiceShortcut', listener);
}

export async function takePendingVoiceShortcutRequest(): Promise<boolean> {
  try {
    return Boolean(await speech?.takePendingVoiceShortcutRequest?.());
  } catch {
    return false;
  }
}

export function VoiceInput({ en, target, onUse, correctVoiceSentence, disabled = false, maxLength = 8000, compact = false, label, sendOnFinish = false, autoStartRequest = 0, onAutoStartRequestConsumed }: { en: boolean; target: string; onUse: (text: string) => void; correctVoiceSentence?: VoiceSentenceCorrector; disabled?: boolean; maxLength?: number; compact?: boolean; label?: string; sendOnFinish?: boolean; autoStartRequest?: number; onAutoStartRequestConsumed?: (request: number) => void }) {
  const [open, setOpen] = useState(false), [state, setState] = useState('ready'), [text, setText] = useState(''), [error, setError] = useState(''), [correctionNow, setCorrectionNow] = useState(0), [correctionDeadlineAt, setCorrectionDeadlineAt] = useState(0);
  const view = useRef<import('react-native-webview').WebView>(null), id = useRef<string | null>(null), autoStarted = useRef(false), startOnOpen = useRef(false), opened = useRef(false), lastAutoStartRequest = useRef(0);
  const correctionRun = useRef(0), correctionAbort = useRef<AbortController | null>(null), stopRequestedAt = useRef<number | null>(null), completedSession = useRef<string | null>(null);
  const audioLease=useRef<AudioLease|null>(null),audioStart=useRef<Promise<unknown>|null>(null);
  function cancelOwnedSpeech(){
    const lease=audioLease.current;if(!lease)return;
    audioLease.current=null;
    const started=audioStart.current;audioStart.current=null;
    void Promise.resolve(started).catch(()=>{}).then(()=>speech?.cancel()).catch(()=>{}).finally(()=>lease.release());
  }
  const remainingSeconds = state === 'correcting' ? Math.max(0, Math.ceil((correctionDeadlineAt - correctionNow) / 1000)) : 0;
  const deliverText = useEffectEvent(onUse);
  const requestCorrection = useEffectEvent((request: Parameters<VoiceSentenceCorrector>[0], signal: AbortSignal) => correctVoiceSentence
    ? correctVoiceSentence(request, signal)
    : Promise.resolve({ sessionId: request.sessionId, sentenceId: request.sentenceId, revision: request.revision, baseTextHash: 'not-configured', status: 'skipped' as const }));
  const sync = useCallback(() => view.current?.injectJavaScript(`window.updateVoice(${JSON.stringify({ en, target, state, text, error, sendOnFinish, remainingSeconds, web: Platform.OS === 'web' })}); true;`), [en, target, state, text, error, sendOnFinish, remainingSeconds]);
  useEffect(sync, [sync]);
  useEffect(() => {
    if (state !== 'correcting') return;
    const timer = setInterval(() => setCorrectionNow(Date.now()), 100); return () => clearInterval(timer);
  }, [state]);
  const close = () => { opened.current = false; startOnOpen.current = false; id.current = null; correctionRun.current++; correctionAbort.current?.abort(); correctionAbort.current = null; stopRequestedAt.current = null; cancelOwnedSpeech(); setOpen(false); };
  useEffect(() => {
    if (!autoStartRequest || autoStartRequest === lastAutoStartRequest.current) return;
    lastAutoStartRequest.current = autoStartRequest;
    onAutoStartRequestConsumed?.(autoStartRequest);
    if (disabled || maxLength <= 0 || opened.current) return;
    Keyboard.dismiss(); opened.current = true; autoStarted.current = false; startOnOpen.current = Platform.OS !== 'web';
    setState('ready'); setText(''); setError(''); setOpen(true);
  }, [autoStartRequest, disabled, maxLength, onAutoStartRequestConsumed]);
  useEffect(() => {
    if (!open) return;
    const listener = speech?.addListener('onSpeech', value => {
      if (id.current !== value.sessionId) return;
      const recognized = value.text.slice(0, maxLength).trim();
      if (value.state === 'complete' && recognized) {
        if (completedSession.current === value.sessionId) return;
        completedSession.current = value.sessionId;
        const run = ++correctionRun.current;
        const deadlineAt = (stopRequestedAt.current ?? Date.now()) + 5000;
        setCorrectionDeadlineAt(deadlineAt);
        setCorrectionNow(Date.now());
        const controller = new AbortController(); correctionAbort.current = controller;
        setText(recognized); setError(''); setState('correcting');
        void correctSpeechTranscript(recognized, requestCorrection, {
          sessionId: value.sessionId, deadlineAt, signal: controller.signal,
          onProgress: corrected => { if (opened.current && id.current === value.sessionId && correctionRun.current === run) setText(corrected.slice(0, maxLength)); },
        }).then(finalText => {
          if (controller.signal.aborted || !opened.current || id.current !== value.sessionId || correctionRun.current !== run) return;
          const final = finalText.slice(0, maxLength).trim();
          if (!final) { id.current = null; setState('error'); setError(en ? 'No words were recognized. Try again.' : '未识别到文字，请重试。'); return; }
          opened.current = false; startOnOpen.current = false; id.current = null; correctionAbort.current = null;
          setOpen(false); cancelOwnedSpeech(); deliverText(final);
        }).catch(() => {
          if (controller.signal.aborted || correctionRun.current !== run) return;
          if (opened.current && id.current === value.sessionId) { setState('error'); setError(en ? 'Voice text could not be prepared. Try again.' : '语音文字暂未能整理，请重试。'); }
        });
        return;
      }
      if (value.state === 'complete' || value.state === 'error') {cancelOwnedSpeech();id.current = null;}
      setText(value.text.slice(0, maxLength)); setState(value.state === 'complete' ? 'error' : value.state);
      if (value.state === 'complete') setError(en ? 'No words were recognized. Try again.' : '未识别到文字，请重试。');
      if (value.state === 'error') setError(Platform.OS === 'web' ? browserSpeechError(value.code, en) : (en ? 'No words were recognized. Try again or keep typing.' : '未识别到文字，可以重试或继续打字。'));
    });
    const app = AppState.addEventListener('change', value => { if (value === 'background') close(); });
    return () => { opened.current = false; startOnOpen.current = false; id.current = null; listener?.remove(); app.remove(); cancelOwnedSpeech(); };
  }, [open, en, maxLength]);
  const start = async () => {
    if (id.current) return;
    try{audioLease.current=audioCoordinator.acquire('SPEECH');}
    catch{setState('error');setError(en?'Another audio session is active. Stop it before speaking.':'正在使用其他音频，请先停止再开始语音输入。');return;}
    const key = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`; id.current = key; completedSession.current = null; stopRequestedAt.current = null; correctionAbort.current?.abort(); setState('starting'); setText(''); setError('');
    if (!speech) { cancelOwnedSpeech(); id.current = null; setState('error'); setError(Platform.OS === 'web' ? (en ? 'Speech recognition is unavailable in this browser. Please type instead.' : '当前浏览器不支持语音识别，请使用文字输入。') : (en ? 'Voice input needs the updated iPhone app.' : '语音输入需要更新后的 iPhone App。')); return; }
    try { const pending=speech.start(en ? 'en-US' : 'zh-CN', key);audioStart.current=pending;await pending; }
    catch (e) { if (id.current !== key) return; cancelOwnedSpeech(); id.current = null; const code = (e as { code?: string }).code;
      setState('error'); setError(Platform.OS === 'web' ? browserSpeechError(code || (e as Error).name, en) : ['SPEECH_PERMISSION', 'MIC_PERMISSION'].includes(code || '') ? (en ? 'Allow microphone and speech access in Settings → Invelle.' : '请在「设置 → Invelle」中允许麦克风和语音识别。') : (en ? 'On-device recognition is unavailable. Check your iPhone language settings or use keyboard dictation.' : '本机识别暂不可用，请检查 iPhone 的语言设置，或使用键盘听写。'));
    }
  };
  return <><Pressable accessibilityRole="button" accessibilityLabel={en ? `Voice input: ${target}` : `语音输入：${target}`} disabled={disabled || maxLength <= 0} style={[styles.button, compact && styles.compact, disabled && { opacity: .4 }]} onPress={() => { Keyboard.dismiss(); opened.current = true; autoStarted.current = false; startOnOpen.current = Platform.OS !== 'web' && sendOnFinish; stopRequestedAt.current = null; setState('ready'); setText(''); setError(''); setOpen(true); }}><Svg width={19} height={19} viewBox="0 0 24 24" fill="none" stroke="#6b8064" strokeWidth={1.4} strokeLinecap="round"><Rect x={9} y={3} width={6} height={12} rx={3} /><Path d="M5.5 11v1a6.5 6.5 0 0013 0v-1M12 18.5V22m-4 0h8" /></Svg>{!compact && <Text style={styles.label}>{label || (en ? 'Speak' : '说出来')}</Text>}</Pressable>
    <Modal visible={open} animationType="fade" onRequestClose={close}><SafeAreaView style={styles.root}><WebView ref={view} source={{ html: source.html }} javaScriptEnabled originWhitelist={['about:blank']} scrollEnabled={false} onMessage={event => { if (!opened.current) return; let action: string; try { action = JSON.parse(event.nativeEvent.data).action; } catch { return; } if (action === 'ready') { sync(); if (open && startOnOpen.current && !autoStarted.current) { autoStarted.current = true; void start(); } } if (action === 'cancel') close(); if (action === 'start') void start(); if (action === 'stop') { stopRequestedAt.current = Date.now(); setState('finishing'); void speech?.stop().catch(() => { id.current = null; cancelOwnedSpeech(); setState('error'); setError(en ? 'Voice input stopped. Try again.' : '语音识别已停止，可以重试。'); }); } }} style={styles.root} /></SafeAreaView></Modal></>;
}
const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#f7f6ee' }, compact: { width: 44, height: 44, paddingHorizontal: 0, paddingVertical: 0, justifyContent: 'center', backgroundColor: 'transparent', borderWidth: 0 }, button: { flexDirection: 'row', alignItems: 'center', gap: 6, borderWidth: 1, borderColor: '#cbd5c0', borderRadius: 24, paddingHorizontal: 17, paddingVertical: 11 }, label: { color: '#6b8064', fontSize: 13 } });
