import type { LocalDraftSummary } from '@/platform/zenflow/local-store';
import { DraftWriter } from '@/core/zenflow/draft-writer';
import type { OwnerDraftStore } from './use-owner-store';
import { ChatMessageInput } from './chat-message-input';
import { AnswerMarkdown } from './answer-markdown';
import { Alert } from '@/platform/zenflow/alert';
import { useEffect, useEffectEvent, useMemo, useRef, useState } from 'react';
import { Platform, ActivityIndicator,  AppState, Keyboard, Linking, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { CoreApi, stableId, isRunTerminal, type Conversation, type Message, type Note, type Run } from '@/core/zenflow/core';
import { CitationCards, GeneratedDraft, RevisionHistory, MemoryPanel } from './private-tools';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import quotations from './quotations.json';
import { PrivateAudioNotes } from './private-audio-notes';
import { VoiceInput } from './voice-input';
import type { ZenflowSession } from '@/core/zenflow/session';

function Button({ title, onPress, disabled = false }: { title: string; onPress: () => void; disabled?: boolean }) {
  return <Pressable accessibilityRole="button" disabled={disabled} onPress={event => { event.stopPropagation(); onPress(); }} style={[styles.button, disabled && { opacity: .4 }]}><Text style={styles.buttonText}>{title}</Text></Pressable>;
}
function failure(error: unknown, en: boolean): string {
  const code = error instanceof Error ? error.message : '';
  if (code === 'CONVERSATION_BUSY') return en ? 'An answer is already in progress. Your text is kept here.' : '这段对话正在回答，输入已保留。';
  if (code === 'VERSION_CONFLICT') return en ? 'This item changed. Refresh before saving again.' : '内容已经更新，请刷新后再保存。';
  if (code === 'COMPANION_NOT_READY' || code === 'COMPANION_NOT_CONFIGURED') return en ? 'The conversation service is being prepared.' : '对话服务正在准备中。';
  if (code === 'AUTH_REQUIRED' || code === 'STALE_IDENTITY') return en ? 'Please sign in again.' : '请重新登录。';
  return en ? 'This action did not finish. Your text is kept. Please try again.' : '操作未完成，输入已保留，请重试。';
}
function parseTagInput(value: string): string[] {
  return [...new Set(value.split(/[,\r\n]+/).map(tag => tag.trim()).filter(Boolean))];
}
function titleRepeatsBody(note: Pick<Note, 'title' | 'text'>): boolean {
  const title = note.title.trim();
  return !!title && note.text.trimStart().startsWith(title);
}
export function CoreSpace({ localDrafts, session, space, english, ready, active = true, onFocusChange, onRest, voiceEntryRequest = 0, onVoiceEntryRequestConsumed }: { localDrafts?: OwnerDraftStore; session: ZenflowSession; space: 'chat' | 'create' | 'notes'; english: boolean; ready: boolean; active?: boolean; onFocusChange?: (value: boolean) => void; onRest?: () => void; voiceEntryRequest?: number; onVoiceEntryRequestConsumed?: (request: number) => void }) {
  if (!ready) return <View style={styles.empty}><Text style={styles.heading}>{english ? 'A little room to pause.' : '给自己留一小会儿。'}</Text><Text style={styles.muted}>{space === 'chat' ? (english ? 'This space opens after the conversation service is configured and published.' : '这处空间将在对话服务配置并发布后开放。') : (english ? 'The notes service is currently unavailable. Please try again later.' : '记录服务当前不可用，请稍后重试。')}</Text></View>;
  return space === 'chat' ? <TalkSpace session={session} en={english} active={active} onRest={onRest} /> : <WritingSpace localDrafts={localDrafts} session={session} en={english} active={active} onFocusChange={onFocusChange} compose={space === 'create'} voiceEntryRequest={space === 'notes' ? voiceEntryRequest : 0} onVoiceEntryRequestConsumed={space === 'notes' ? onVoiceEntryRequestConsumed : undefined} />;
}
function TalkSpace({ session, en, active, onRest }: { session: ZenflowSession; en: boolean; active: boolean; onRest?: () => void }) {
  const serif = { fontFamily: en ? 'Georgia' : 'Songti SC' };
  const api = useMemo(() => new CoreApi(session), [session]);
  const [list, setList] = useState<Conversation[]>([]); const [current, setCurrent] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<Message[]>([]); const [draft, setDraft] = useState(''); const [answer, setAnswer] = useState('');
  const [optionsOpen, setOptionsOpen] = useState(false);
  const [run, setRun] = useState<Run | null>(null); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [history, setHistory] = useState(false);
  const [notes, setNotes] = useState<Note[]>([]); const [selected, setSelected] = useState<Note[]>([]); const [sourcesOpen, setSourcesOpen] = useState(false); const [nextCursor, setNextCursor] = useState<string | null>(null); const [older, setOlder] = useState<number | null>(null);
  const [archiveView, setArchiveView] = useState(false); const listFilter = useRef<'ACTIVE' | 'ARCHIVED'>('ACTIVE'); const listRequest = useRef(0);
  const chatList = useRef<ScrollView>(null), followBottom = useRef(true);
  const stream = useRef<AbortController | null>(null); const alive = useRef(true); const messageKey = useRef<{ text: string; notes: string; id: string } | null>(null);
  const refreshList = async () => { const ticket = ++listRequest.current; const page = await api.conversations(undefined, listFilter.current); if (alive.current && ticket === listRequest.current) { setList(page.items); setNextCursor(page.hasMore ? page.nextCursor || null : null); } };
  const load = async (item: Conversation) => { stream.current?.abort(); setError(''); const page = await api.messages(item.id); if (!alive.current) return; setCurrent(item); setMessages(page.items); setOlder(page.hasMore ? page.nextBeforeSequence : null); setHistory(false); setRun(null); setAnswer(''); if (item.activeRunId) await follow(item.id, item.activeRunId); };
  const follow = async (sessionId: string, id: string) => {
    stream.current?.abort(); const controller = new AbortController(); stream.current = controller;
    try {
      let snapshot = await api.run(id); if (!alive.current || controller.signal.aborted) return;
      setRun(snapshot); setAnswer(snapshot.draftContent || '');
      if (!isRunTerminal(snapshot.status)) {
        await api.events(id, Number(snapshot.lastEventSeq), controller.signal, event => {
          if (!alive.current || controller.signal.aborted) return;
          if (event.type === 'RESPONSE_DELTA') setAnswer(value => value + String(event.data.delta || ''));
          if (['RUN_COMPLETED', 'RUN_FAILED', 'RUN_CANCELLED'].includes(event.type)) setRun(value => value ? { ...value, status: event.type.slice(4) } : value);
        });
        snapshot = await api.run(id); if (!alive.current || controller.signal.aborted) return; setRun(snapshot);
      }
      if (snapshot.status === 'FAILED') setError(en ? 'The answer could not finish. Check the model configuration or try again.' : '回答未能完成，请检查模型配置或重试。');
      const page = await api.messages(sessionId); if (alive.current && !controller.signal.aborted) { setMessages(page.items); if (snapshot.status === 'COMPLETED') setAnswer(''); await refreshList(); }
    } catch (e) { if (alive.current && !controller.signal.aborted) setError(en ? 'Connection interrupted. Reconnect to resume this answer.' : '连接中断，可以重新连接恢复这次回答。'); }
  };
  useEffect(() => { alive.current = true; return () => { alive.current = false; stream.current?.abort(); }; }, [api]);
  useEffect(() => { if (active) void refreshList().catch(e => { if (alive.current) setError(failure(e, en)); }); }, [active, api]);
  const send = async (input = draft) => {
    if (!input.trim() || busy || (run && !isRunTerminal(run.status))) return;
    setBusy(true); setError(''); setRun(null); followBottom.current = true; const text = input.trim(); const sources = selected.map(n => `${n.id}:${n.currentRevisionId}`).sort().join('|');
    if (!messageKey.current || messageKey.current.text !== text || messageKey.current.notes !== sources) messageKey.current = { text, notes: sources, id: stableId() };
    try {
      const item = current || await api.createConversation(en); if (!alive.current) return; setCurrent(item);
      const accepted = await api.send(item.id, messageKey.current.id, text, selected); if (!alive.current) return;
      setDraft(''); setSelected([]); messageKey.current = null; setAnswer(''); setMessages(value => [...value, { messageId: `pending-${accepted.runId}`, role: 'USER', content: text, status: 'COMPLETED', sequenceNo: (value[value.length - 1]?.sequenceNo || 0) + 1 }]);
      await follow(item.id, accepted.runId);
    } catch (e) { if (alive.current) setError(failure(e, en)); } finally { if (alive.current) setBusy(false); }
  };
  const resume = useEffectEvent(() => { if (current && run) void follow(current.id, run.runId); });
  useEffect(() => { let paused = false; const listener = AppState.addEventListener('change', state => { if (state !== 'active') { paused = true; stream.current?.abort(); } else if (paused) { paused = false; resume(); } }); return () => listener.remove(); }, []);
  const changeConversation = async (item: Conversation, remove: boolean) => {
    try { const latest = await api.conversation(item.id); if (remove) await api.deleteConversation(latest); else await api.conversationState(latest, latest.status === 'ARCHIVED' ? 'ACTIVE' : 'ARCHIVED');
      if (!alive.current) return; if (current?.id === item.id) { stream.current?.abort(); setCurrent(null); setMessages([]); setOlder(null); setRun(null); setAnswer(''); } await refreshList();
    } catch (e) { if (alive.current) setError(failure(e, en)); }
  };
  const removeConversation = (item: Conversation) => Alert.alert(en ? 'Delete this conversation?' : '删除这段对话？', en ? 'Messages and their references will become unavailable. Your notes are kept.' : '消息及其引用将不可访问，原来的私人记录会保留。', [{ text: en ? 'Cancel' : '取消', style: 'cancel' }, { text: en ? 'Delete' : '删除', style: 'destructive', onPress: () => void changeConversation(item, true) }]);
  const running = run && !isRunTerminal(run.status);
  return <View style={styles.fill}>
    <View style={styles.chatHeading}><Text style={[styles.chatTitle, serif]}>{en ? 'Let’s talk.' : '聊聊。'}</Text><Pressable accessibilityRole="button" accessibilityLabel={en ? 'Conversation options' : '聊天选项'} style={styles.iconButton} onPress={() => { Keyboard.dismiss(); setOptionsOpen(!optionsOpen); }}><Text style={styles.text}>···</Text></Pressable></View>
    <Modal visible={optionsOpen} transparent animationType="fade" onRequestClose={() => setOptionsOpen(false)}><Pressable style={styles.backdrop} onPress={() => setOptionsOpen(false)}><Pressable style={styles.optionSheet} onPress={event => event.stopPropagation()}><Text style={styles.optionTitle}>{en ? 'Conversation options' : '聊天选项'}</Text><View style={styles.optionActions}><Button title={en ? 'History' : '历史'} onPress={() => { setHistory(!history); setOptionsOpen(false); void refreshList().catch(e => setError(failure(e, en))); }} /><Button title={en ? 'New' : '新对话'} disabled={!!running} onPress={() => { stream.current?.abort(); setCurrent(null); setMessages([]); setOlder(null); setRun(null); setAnswer(''); setError(''); setHistory(false); setOptionsOpen(false); }} /><Button title={en ? `Private notes (${selected.length}/3)` : `选入记录 (${selected.length}/3)`} onPress={() => { setSourcesOpen(!sourcesOpen); setOptionsOpen(false); void api.notes().then(page => setNotes(page.items.filter(note => note.status === 'ACTIVE'))).catch(e => setError(failure(e, en))); }} /></View><Pressable accessibilityRole="button" style={styles.textAction} onPress={() => setOptionsOpen(false)}><Text style={styles.muted}>{en ? 'Done' : '好了'}</Text></Pressable></Pressable></Pressable></Modal>
    {history && <View style={styles.toolbar}><Button title={archiveView ? (en ? 'Active conversations' : '查看当前对话') : (en ? 'Archived conversations' : '查看归档对话')} onPress={() => { const next = !archiveView; setArchiveView(next); listFilter.current = next ? 'ARCHIVED' : 'ACTIVE'; void refreshList().catch(e => setError(failure(e, en))); }} /></View>}
    {history ? <ScrollView style={styles.list}>{list.map(item => <Pressable key={item.id} style={styles.card} onPress={() => void load(item).catch(e => setError(failure(e, en)))}><Text style={styles.text}>{item.title}</Text><Text style={styles.muted}>{en ? 'Open conversation' : '继续对话'}</Text><View style={styles.toolbar}><Button title={item.status === 'ARCHIVED' ? (en ? 'Restore' : '恢复') : (en ? 'Archive' : '归档')} onPress={() => void changeConversation(item, false)} /><Button title={en ? 'Delete' : '删除'} onPress={() => removeConversation(item)} /></View></Pressable>)}{nextCursor && <Button title={en ? 'Load more' : '更多对话'} onPress={() => void (() => { const filter = listFilter.current; const ticket = listRequest.current; return api.conversations(nextCursor, filter).then(page => { if (alive.current && ticket === listRequest.current && filter === listFilter.current) { setList(value => [...value, ...page.items]); setNextCursor(page.hasMore ? page.nextCursor || null : null); } }); })().catch(e => setError(failure(e, en)))} />}</ScrollView> : <ScrollView ref={chatList} scrollEventThrottle={32} onScroll={event => { const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent; followBottom.current = contentSize.height - contentOffset.y - layoutMeasurement.height < 96; }} onContentSizeChange={() => { if (followBottom.current) chatList.current?.scrollToEnd({ animated: false }); }} style={styles.list} keyboardShouldPersistTaps="handled">
      {older && current && <Button title={en ? 'Earlier messages' : '更早的消息'} onPress={() => void api.messages(current.id, older).then(page => { if (alive.current) { setMessages(value => [...page.items, ...value]); setOlder(page.hasMore ? page.nextBeforeSequence : null); } }).catch(e => setError(failure(e, en)))} />}
      {!messages.length && !answer && <View style={styles.chatEmpty}><Text style={[styles.emptyTitle, serif]}>{en ? 'What is here for you, right now?' : '此刻，有什么想留在这里？'}</Text><Text style={styles.pageDescription}>{en ? 'Start with whatever is here.' : '从此刻想说的开始。'}</Text><Pressable accessibilityRole="button" style={styles.textAction} onPress={() => setDraft(en ? 'Today, I want to slow down a little.' : '今天，我想放慢一点。')}><Text style={styles.fine}>{en ? 'Try an example' : '试一句示例'} →</Text></Pressable></View>}
      {messages.filter(m => m.content).map(m => <View key={m.messageId} style={m.role === 'USER' ? styles.chatUser : styles.chatResponse}>{m.role === 'ASSISTANT' && <Text style={styles.label}>Invelle</Text>}{m.role === 'USER' && <Text selectable style={[styles.text, { fontSize: 14, lineHeight: 26 }]}>{m.content}</Text>}{m.role === 'ASSISTANT' && m.status !== 'COMPLETED' && <AnswerMarkdown content={m.content} en={en} />}{m.role === 'ASSISTANT' && m.status === 'COMPLETED' && current && <CitationCards api={api} en={en} conversationId={current.id} messageId={m.messageId} content={m.content} />}{m.status !== 'COMPLETED' && <Text style={styles.muted}>{en ? 'Incomplete answer' : '未完成的回答'}</Text>}</View>)}
      {busy && !answer && (!run || running) && <View style={styles.chatResponse}><Text style={styles.label}>Invelle</Text><Text style={styles.muted}>{en ? 'Gathering a response…' : '正在整理回应…'}</Text></View>}
      {!!answer && !messages.some(m => m.content === answer) && <View style={styles.chatResponse}><Text style={styles.label}>Invelle</Text><AnswerMarkdown content={answer} en={en} /><Text style={styles.muted}>{running ? (en ? 'Answering…' : '正在回答…') : (en ? 'Incomplete answer' : '未完成的回答')}</Text></View>}
    </ScrollView>}
    {!!error && <Text style={styles.error}>{error}</Text>}
    {run && (running || !!error) && <View style={styles.toolbar}>{running && <Button title={en ? 'Stop' : '停止'} onPress={() => void api.cancel(run.runId).then(() => current && follow(current.id, run.runId)).catch(e => setError(failure(e, en)))} />}{current && <Button title={en ? 'Reconnect' : '重新连接'} onPress={() => void follow(current.id, run.runId)} />}</View>}
    {sourcesOpen && <ScrollView style={{ maxHeight: 170, paddingHorizontal: 20 }}>{notes.map(note => <Pressable key={note.id} style={styles.source} onPress={() => setSelected(value => value.some(x => x.id === note.id) ? value.filter(x => x.id !== note.id) : value.length < 3 ? [...value, note] : value)}><Text style={styles.text}>{selected.some(x => x.id === note.id) ? '✓ ' : '+ '}{note.title || (en ? 'Untitled' : '无标题')}</Text></Pressable>)}</ScrollView>}
    <View style={styles.chatBottom}>
      {(sourcesOpen || selected.length > 0) && <Text style={styles.muted}>{en ? 'Selected notes are shared with the configured model for this turn only.' : '选中的记录仅授权这一轮使用，并会发送给已配置的模型。'}</Text>}
      <View style={styles.chatComposer}><ChatMessageInput onSend={() => void send()} sendDisabled={busy || !!running || !draft.trim()} accessibilityLabel={en ? 'Message' : '消息'} multiline maxLength={8000} style={styles.chatInput} value={draft} onChangeText={setDraft} placeholder={en ? 'Say what’s on your mind…' : '写下想说的话……'} placeholderTextColor="#929c82" /><VoiceInput correctVoiceSentence={(request, signal) => api.correctVoiceSentence(request, signal)} compact sendOnFinish en={en} disabled={busy || !!running} target={en ? 'Message' : '消息'} maxLength={8000 - draft.length} onUse={value => { const question = (draft + (draft ? '\n' : '') + value).slice(0, 8000); setDraft(question); void send(question); }} /><Pressable accessibilityRole="button" accessibilityLabel={en ? 'Send message' : '发送消息'} style={[styles.send, (busy || !!running || !draft.trim()) && styles.disabled]} disabled={busy || !!running || !draft.trim()} onPress={() => void send()}><Text style={styles.sendText}>{busy ? '…' : '↑'}</Text></Pressable></View>
      <View style={styles.row}><Text style={styles.fine}>{en ? 'Only in your own account.' : '对话仅留在你的账号中。'}</Text><Pressable accessibilityRole="button" style={styles.textAction} onPress={() => { Keyboard.dismiss(); onRest?.(); }}><Text style={styles.fine}>{en ? 'Pause here' : '先聊到这'}</Text></Pressable></View>
    </View>
  </View>;
}
function WritingSpace({ localDrafts, session, en, compose, active, onFocusChange, voiceEntryRequest = 0, onVoiceEntryRequestConsumed }: { localDrafts?: OwnerDraftStore; session: ZenflowSession; en: boolean; compose: boolean; active: boolean; onFocusChange?: (value: boolean) => void; voiceEntryRequest?: number; onVoiceEntryRequestConsumed?: (request: number) => void }) {
  const serif = { fontFamily: en ? 'Georgia' : 'Songti SC' };
  const api = useMemo(() => new CoreApi(session), [session]); const [items, setItems] = useState<Note[]>([]); const [note, setNote] = useState<Note | null>(null);
  const [editing, setEditing] = useState(false); const [viewing, setViewing] = useState(false); const [title, setTitle] = useState(''); const [text, setText] = useState(''); const [tagsText, setTagsText] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [saved, setSaved] = useState(false);
  const [cursor, setCursor] = useState<string | null>(null);
  const [filter, setFilter] = useState<'ALL' | 'FAVORITE' | 'ARCHIVED'>('ALL'); const filterRef = useRef<'ALL' | 'FAVORITE' | 'ARCHIVED'>('ALL'); const refreshRequest = useRef(0);
  const [focused, setFocused] = useState(false);
  const focusAvailable = active && editing;
  const [previousFocusAvailable, setPreviousFocusAvailable] = useState(focusAvailable);
  if (previousFocusAvailable !== focusAvailable) {
    setPreviousFocusAvailable(focusAvailable);
    if (!focusAvailable) setFocused(false);
  }
  useEffect(() => { onFocusChange?.(focusAvailable && focused); return () => onFocusChange?.(false); }, [focusAvailable, focused, onFocusChange]);
  const [quoteIndex, setQuoteIndex] = useState(0), [prompt, setPrompt] = useState(''), [assistOpen, setAssistOpen] = useState(false), [detailsOpen, setDetailsOpen] = useState(false);
  const paperScroll = useRef<ScrollView>(null);
  const selectionRequest = useRef(0); const [editorKey, setEditorKey] = useState(stableId);
  useEffect(() => { paperScroll.current?.scrollTo({ y: 0, animated: false }); }, [editing, viewing, editorKey]);
  const generatedSource = useRef<{ text: string; messageId: string } | null>(null);
  const client = useRef(stableId()); const alive = useRef(true);
  const refresh = async () => { const ticket = ++refreshRequest.current; const query = filterRef.current; const page = await api.notes(undefined, query); if (alive.current && ticket === refreshRequest.current && query === filterRef.current) { setItems(page.items); setCursor(page.hasMore ? page.nextCursor || null : null); } };
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, [api]);
  useEffect(() => { if (active) void refresh().catch(e => { if (alive.current) setError(failure(e, en)); }); }, [active, api]);
  const openNow = async (item: Note, edit = false) => { const ticket = ++selectionRequest.current; const latest = await api.note(item.id); if (!alive.current || ticket !== selectionRequest.current) return; setDraftSlot(`${compose?'creation':'notes'}:note:${latest.id}`); setEditorKey(latest.id); generatedSource.current = null; setNote(latest); setTitle(latest.title || ''); setText(latest.text); setTagsText((latest.tags || []).join(', ')); setEditing(edit); setViewing(!edit); setSaved(false); setError(''); };
  const freshNow = () => { selectionRequest.current++; setDraftSlot(`${compose?'creation':'notes'}:draft:${stableId()}`); setEditorKey(stableId()); generatedSource.current = null; setNote(null); setTitle(''); setText(''); setTagsText(''); setEditing(true); setViewing(false); setSaved(false); setError(''); client.current = stableId(); };
  const tags = parseTagInput(tagsText);
  const dirty = note ? text !== note.text || title !== (note.title || '') || tags.join('\u0000') !== (note.tags || []).join('\u0000') : !!text || !!title || tags.length > 0;
  const namespace=compose?'creation':'notes';
  const [draftSlot,setDraftSlot]=useState(`${namespace}:editor`);
  const [draftSelection,setDraftSelection]=useState(0);
  const [draftItems,setDraftItems]=useState<LocalDraftSummary[]>([]);
  const [draftCursor,setDraftCursor]=useState<string|null>(null);
  const draftListRequest=useRef(0);
  const draftBody=JSON.stringify({formatVersion:1,targetId:note?.id??null,sourceVersion:note?.version??null,clientRequestId:client.current,title,text,tagsText});
  const [recoverable,setRecoverable]=useState<{body:string;value:{targetId:string|null;sourceVersion:number|null;clientRequestId?:string;title:string;text:string;tagsText:string}}|null>(null);
  const draftReadReady=useRef<OwnerDraftStore|undefined>(undefined);
  const editorSnapshot=useRef({body:draftBody,editing});editorSnapshot.current={body:draftBody,editing};
  const [localSavedBody,setLocalSavedBody]=useState(''),[localError,setLocalError]=useState('');
  const writer=useMemo(()=>localDrafts?new DraftWriter(localDrafts.store,draftSlot,localDrafts.generation,(body,error)=>{
    if(!alive.current)return;
    if(error)setLocalError(en?'Local draft could not be saved. Keep this page open and retry.':'本机草稿未能保存，请保留当前页面并重试。');
    else{setLocalSavedBody(body);setLocalError('');}
  }):null,[localDrafts,draftSlot,en]);
  useEffect(()=>{
    let current=true;draftReadReady.current=undefined;setRecoverable(null);setLocalSavedBody('');
    if(localDrafts)void localDrafts.store.readDraft(draftSlot,localDrafts.generation).then(draft=>{
      if(!current)return;draftReadReady.current=localDrafts;if(!draft){setLocalSavedBody('ready');return;}const value=JSON.parse(draft.body);
      if(value.formatVersion!==1||typeof value.title!=='string'||typeof value.text!=='string'||typeof value.tagsText!=='string'
        ||(value.clientRequestId!==undefined&&(typeof value.clientRequestId!=='string'||value.clientRequestId.length>200))
        ||value.title.length>120||value.text.length>50000||value.tagsText.length>620
        ||!(value.targetId===null||typeof value.targetId==='string')
        ||!(value.sourceVersion===null||Number.isSafeInteger(value.sourceVersion)&&value.sourceVersion>0))throw Error('INVALID_DRAFT');
      if(editorSnapshot.current.editing&&editorSnapshot.current.body===draft.body)setLocalSavedBody(draft.body);
      else setRecoverable({body:draft.body,value});
    }).catch(()=>{draftReadReady.current=undefined;if(current)setLocalError(en?'The saved local draft could not be opened.':'已存的本机草稿暂未能打开。');});
    return()=>{current=false;};
  },[localDrafts,draftSlot,draftSelection]);
  useEffect(()=>{
    if(!writer)return;
    const unregister=localDrafts?.registerFlush(()=>writer.flush());
    return()=>{unregister?.();void writer.flush().catch(()=>{}).finally(()=>writer.cancel());};
  },[writer,localDrafts]);
  useEffect(()=>{if(editing&&dirty&&!recoverable&&localSavedBody!==draftBody&&draftReadReady.current===localDrafts)writer?.schedule(draftBody);},[writer,editing,dirty,draftBody,recoverable,localSavedBody]);
  useEffect(()=>{if(!active)void writer?.flush().catch(()=>{});},[active,writer]);
  const refreshDrafts=async(cursor='')=>{
    if(!localDrafts)return;
    const ticket=++draftListRequest.current;
    try{
      const page=await localDrafts.store.listDrafts(namespace,localDrafts.generation,cursor);
      if(alive.current&&ticket===draftListRequest.current){setDraftItems(previous=>cursor?[...previous,...page.items]:page.items);setDraftCursor(page.nextCursor);}
    }catch{if(alive.current&&ticket===draftListRequest.current)setLocalError(en?'Local drafts could not be listed.':'本机草稿列表暂未能打开。');}
  };
  useEffect(()=>{setDraftItems([]);setDraftCursor(null);if(active)void refreshDrafts();return()=>{draftListRequest.current++;};},[localDrafts,active,localSavedBody]);
  const restoreLocal=async()=>{
    if(!recoverable||!localDrafts||busy)return;
    setBusy(true);setError('');
    try{
      const value=recoverable.value;
      generatedSource.current=null;client.current=value.clientRequestId||stableId();
      const latest=value.targetId?await api.note(value.targetId):null;
      if(!alive.current)return;
      // Keep the original version so the existing API rejects an overwrite after another-device update.
      setNote(latest?{...latest,version:value.sourceVersion??latest.version}:null);
      setTitle(value.title);setText(value.text);setTagsText(value.tagsText);setEditorKey(value.targetId||stableId());
      setEditing(true);setViewing(false);setSaved(false);setLocalSavedBody(recoverable.body);setRecoverable(null);
      if(latest&&latest.version!==value.sourceVersion)setError(en?'The cloud version has changed. This draft is kept; saving may require resolving a conflict.':'云端版本已更新。本机草稿已保留，保存前需要核对冲突。');
    }catch(e){if(alive.current)setError(failure(e,en));}finally{if(alive.current)setBusy(false);}
  };
  const confirmDiscard = (action: () => void) => {
    if (!dirty || !editing) { action(); return; }
    if(writer&&draftReadReady.current===localDrafts&&!recoverable){
      writer.schedule(draftBody);
      void writer.flush().then(()=>{if(alive.current)action();}).catch(()=>{});
      return;
    }
    Alert.alert(en ? 'Leave unsaved writing?' : '离开未保存的文字？', en ? 'Save it privately first if you want to keep it.' : '如果想保留，请先私人保存。', [{ text: en ? 'Keep editing' : '继续编辑', style: 'cancel' }, { text: en ? 'Leave' : '离开', style: 'destructive', onPress: action }]);
  };
  const fresh = () => confirmDiscard(freshNow);
  const open = async (item: Note, edit = false) => { confirmDiscard(() => void openNow(item, edit).catch(e => { if (alive.current) setError(failure(e, en)); })); };
  const save = async () => {
    if (busy || !text.trim()) return;
    if (tags.length > 20 || tags.some(tag => tag.length > 30)) { setError(en ? 'Use up to 20 tags, with 30 characters or fewer each.' : '标签最多 20 个，每个不超过 30 个字符。'); return; }
    setBusy(true); setError('');
    const submittedDraftBody=draftBody;
    try {
      await writer?.flush().catch(()=>{});
      const value = !note && generatedSource.current?.text === text
        ? await api.saveExcerpt(title, text, client.current, generatedSource.current.messageId, tags)
        : await api.saveNote(note?.id || null, title, text, note?.version || 1, client.current, tags);
      if (alive.current) { setNote(value); setTagsText((value.tags || []).join(', ')); setSaved(true); writer?.acknowledge(submittedDraftBody); if(localDrafts)await localDrafts.store.discardDraftIfBodyMatches(draftSlot,submittedDraftBody,localDrafts.generation).catch(()=>setLocalError(en?'Cloud saved; the local draft still needs cleanup.':'云端已保存，本机草稿仍待清理。'));setRecoverable(null); await refreshDrafts(); await refresh(); }
    } catch (e) { if (alive.current) setError(failure(e, en)); }
    finally { if (alive.current) setBusy(false); }
  };
  const changeState = async (values: { archived?: boolean; favorite?: boolean; pinned?: boolean }) => {
    if (!note || busy) return; setBusy(true); setError('');
    try { const value = await api.noteState(note, values); if (alive.current) { setNote(value); await refresh(); } }
    catch (e) { if (alive.current) setError(failure(e, en)); } finally { if (alive.current) setBusy(false); }
  };
  const togglePinned = async (item: Note) => {
    if (busy) return; setBusy(true); setError('');
    try { const latest = await api.note(item.id); await api.noteState(latest, { pinned: !latest.pinned }); if (alive.current) await refresh(); }
    catch (e) { if (alive.current) setError(failure(e, en)); } finally { if (alive.current) setBusy(false); }
  };
  const permission = () => {
    if (!note || busy) return;
    const current = note.allowedScopes?.find(value => value.scope === 'PERSISTENT' && value.state === 'ACTIVE');
    const action = async () => { setBusy(true); try { if (current) await api.revoke(note, current); else await api.grantFuture(note); const latest = await api.note(note.id); if (alive.current) { setNote(latest); await refresh(); } } catch (e) { if (alive.current) setError(failure(e, en)); } finally { if (alive.current) setBusy(false); } };
    Alert.alert(current ? (en ? 'Withdraw future permission?' : '撤回以后参考许可？') : (en ? 'Allow this note for future reference?' : '允许以后参考这条记录？'), en ? 'This applies only to the saved version. It does not turn on memory or share other notes.' : '只作用于已保存的这个版本，不会开启记忆或授权其他记录。', [{ text: en ? 'Cancel' : '取消', style: 'cancel' }, { text: en ? 'Confirm' : '确认', onPress: () => void action() }]);
  };
  const remove = () => { if (!note) return; Alert.alert(en ? 'Delete this private note?' : '删除这条私人记录？', en ? 'Access closes immediately. All text revisions are then removed.' : '立即关闭访问，随后清理所有文字版本。', [{ text: en ? 'Keep' : '保留', style: 'cancel' }, { text: en ? 'Delete' : '删除', style: 'destructive', onPress: () => { setBusy(true); void api.deleteNote(note).then(() => { if (alive.current) { freshNow(); setEditing(false); return refresh(); } }).catch(e => setError(failure(e, en))).finally(() => setBusy(false)); } }]); };
  const removeItem = (item: Note) => Alert.alert(en ? 'Delete this private note?' : '删除这条私人记录？', en ? 'Access closes immediately. All text revisions are then removed.' : '立即关闭访问，随后清理所有文字版本。', [{ text: en ? 'Keep' : '保留', style: 'cancel' }, { text: en ? 'Delete' : '删除', style: 'destructive', onPress: () => { setBusy(true); setError(''); void api.note(item.id).then(latest => api.deleteNote(latest)).then(() => alive.current ? refresh() : undefined).catch(e => { if (alive.current) setError(failure(e, en)); }).finally(() => { if (alive.current) setBusy(false); }); } }]);
  const exportWriting = async () => {
    if (!text.trim()) return;
    if (Platform.OS === 'web') {
      const url = URL.createObjectURL(new Blob([`${title ? title + '\n\n' : ''}${text}`], { type: 'text/plain;charset=utf-8' }));
      const link = document.createElement('a'); link.href = url; link.download = 'invelle-writing.txt'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); return;
    }
    const path = `${FileSystem.cacheDirectory}invelle-writing-${Date.now()}.txt`;
    try { await FileSystem.writeAsStringAsync(path, `${title ? title + '\n\n' : ''}${text}`); await Sharing.shareAsync(path, { mimeType: 'text/plain', UTI: 'public.plain-text' }); }
    catch { setError(en ? 'Export did not finish. Your writing is kept.' : '暂未能导出，文字已保留。'); }
    finally { await FileSystem.deleteAsync(path, { idempotent: true }).catch(() => {}); }
  };
  const filteredItems = items;
  const quote = quotations[quoteIndex];
  const language = en ? 'en' : 'zh';
  const voiceWriting = (value: string) => { setText(current => (current + (current ? '\n' : '') + value).slice(0, 50000)); setSaved(false); };
  const date = (value?: string) => value && !Number.isNaN(Date.parse(value)) ? new Date(value).toLocaleDateString(en ? 'en-US' : 'zh-CN', { month: 'short', day: 'numeric' }) : '';
  return <View style={styles.fill}>
    {!compose&&<PrivateAudioNotes store={localDrafts} en={en} active={active}/>}
    {recoverable&&<View style={{padding:12,gap:8}}><Text style={styles.muted}>{en?'A private draft is saved on this device.':'这台设备上有一份私人草稿。'}</Text><Button title={en?'Restore local draft':'恢复本机草稿'} onPress={()=>confirmDiscard(()=>void restoreLocal())} disabled={busy}/></View>}
    {editing&&Platform.OS!=='web'&&<View style={{paddingHorizontal:20}}><Text accessibilityLiveRegion="polite" style={styles.fine}>{localError||(localDrafts?(localSavedBody===draftBody?(en?'Saved on this device.':'已存本机。'):(en?'Local draft pending…':'本机草稿待保存……')):(en?'Local draft storage is unavailable. Keep this page open until cloud save succeeds.':'本机草稿存储当前不可用，请保留页面直到云端保存成功。'))}</Text>{!!localError&&writer&&<Button title={en?'Retry local save':'重试本机保存'} onPress={()=>{writer.schedule(draftBody);void writer.flush().catch(()=>{});}}/>}</View>}

    <ScrollView ref={paperScroll} style={styles.list} contentContainerStyle={styles.writingContent} keyboardShouldPersistTaps="handled" keyboardDismissMode="interactive">
      {!editing&&draftItems.length>0&&<View style={{gap:8}}><Text style={styles.label}>{en?'Drafts on this device':'本机草稿'}</Text>{draftItems.map((draft,index)=><Button key={draft.id} title={`${en?'Draft':'草稿'} ${index+1} · ${new Date(draft.persistedAt).toLocaleString(en?'en-US':'zh-CN')}`} onPress={()=>{setViewing(false);setDraftSlot(draft.id);setDraftSelection(value=>value+1);}}/>)}{draftCursor&&<Button title={en?'More drafts':'更多草稿'} onPress={()=>void refreshDrafts(draftCursor)}/>}</View>}
      {editing ? <>
        <Pressable accessibilityRole="button" style={styles.back} onPress={() => confirmDiscard(() => { selectionRequest.current++; setEditing(false); void refresh(); })}><Text style={styles.muted}>← {en ? (compose ? 'Back to my pages' : 'Back to notes') : (compose ? '回到创作列表' : '回到记录')}</Text></Pressable>
        <View style={styles.pageHeading}>{!focused && <View style={styles.headingCopy}><Text style={[styles.pageTitle, serif]}>{en ? 'An open page.' : '写点什么。'}</Text><Text style={styles.pageDescription}>{en ? 'A thought becomes a beginning when you give it a little room.' : '给心里的念头，留一个开始。'}</Text></View>}<Pressable accessibilityRole="button" style={styles.textAction} onPress={() => { const value = !focused; setFocused(value); onFocusChange?.(value); }}><Text style={styles.muted}>{focused ? (en ? 'Leave focus' : '退出专注') : (en ? 'Focus' : '专心写')} ↗</Text></Pressable></View>
        {compose && !focused && <View style={styles.quoteCard}><View style={styles.quoteTape} /><View style={styles.row}><Text style={styles.eyebrow}>{en ? 'WORDS TO SIT WITH' : '读一句，停一停'}</Text><View style={styles.row}><Pressable accessibilityRole="button" accessibilityLabel={en ? 'Previous quotation' : '上一句'} style={styles.iconButton} onPress={() => setQuoteIndex(i => (i + quotations.length - 1) % quotations.length)}><Text style={styles.text}>←</Text></Pressable><Pressable accessibilityRole="button" accessibilityLabel={en ? 'Next quotation' : '下一句'} style={styles.iconButton} onPress={() => setQuoteIndex(i => (i + 1) % quotations.length)}><Text style={styles.text}>→</Text></Pressable></View></View><Text style={[styles.quotation, serif]}>{quote.quote[language]}</Text><Text style={styles.quoteCredit}>{quote.author[language]} · {quote.work[language]}</Text><View style={styles.row}><Pressable accessibilityRole="button" style={styles.textAction} onPress={() => setPrompt(quote.prompt[language])}><Text style={styles.muted}>{en ? 'Write a response' : '回应这句话'} ↗</Text></Pressable><Pressable accessibilityRole="link" style={styles.textAction} onPress={() => void Linking.openURL(quote.source)}><Text style={styles.muted}>{en ? 'Source' : '出处'} ↗</Text></Pressable></View><Text style={styles.fine}>{quote.translation[language]}</Text></View>}
        <View style={styles.paper}>
          <View style={styles.row}><View style={styles.headingCopy}><Text style={styles.eyebrow}>{en ? 'A PAGE OF YOUR OWN' : '属于你的这一页'}</Text><Text style={styles.paperHint}>{en ? 'One sentence is a beginning.' : '写一句，也是一种开始。'}</Text></View><VoiceInput correctVoiceSentence={(request, signal) => api.correctVoiceSentence(request, signal)} en={en} target={en ? 'Private writing' : '私人文字'} maxLength={50000 - text.length} onUse={voiceWriting} disabled={busy} /></View>
          {!focused && <View style={styles.starts}>{[[en ? 'I notice…' : '此刻，我注意到……'], [en ? 'I want to let go…' : '我想慢慢放下……'], [en ? 'A small joy…' : '今天的小小欢喜……']].map(([value]) => <Pressable key={value} accessibilityRole="button" style={styles.start} onPress={() => setPrompt(value)}><Text style={styles.startText}>{value}</Text></Pressable>)}</View>}
          <View style={styles.titleRow}><TextInput accessibilityLabel={en ? 'Title' : '标题'} style={[styles.paperTitle, serif]} maxLength={120} value={title} onChangeText={value => { setTitle(value); setSaved(false); }} placeholder={en ? 'A title can come later' : '标题，可以稍后再想'} placeholderTextColor="#a2a78f" /><VoiceInput correctVoiceSentence={(request, signal) => api.correctVoiceSentence(request, signal)} compact en={en} target={en ? 'Title' : '标题'} maxLength={120 - title.length} onUse={value => { setTitle(current => (current + ' ' + value).trim().slice(0, 120)); setSaved(false); }} disabled={busy} /></View>
          <View style={styles.tagEditor}><Text style={styles.label}>{en ? 'TAGS · COMMA SEPARATED' : '标签 · 用逗号隔开'}</Text><TextInput accessibilityLabel={en ? 'Tags' : '标签'} style={styles.tagInput} multiline maxLength={620} value={tagsText} onChangeText={value => { setTagsText(value); setSaved(false); }} placeholder={en ? 'Add a tag' : '添加标签'} placeholderTextColor="#929c82" /></View>
          <View style={styles.paperBody}><View style={StyleSheet.absoluteFill} pointerEvents="none">{Array.from({ length: 24 }, (_, i) => <View key={i} style={[styles.paperLine, { top: 45 + i * 32 }]} />)}</View><TextInput accessibilityLabel={en ? 'Private writing' : '私人文字'} multiline maxLength={50000} style={[styles.paperWriting, serif]} value={text} onChangeText={value => { setText(value); setSaved(false); }} placeholder={prompt || (en ? 'Start with what is here. A feeling, a detail, a thought you have not said aloud…' : '从心里最轻的那个念头写起。\n一个感受、一处细节，或一句还没说出口的话……')} placeholderTextColor="#929c82" /></View>
          <View style={styles.row}><Pressable accessibilityRole="button" style={styles.textAction} onPress={() => setPrompt(quote.prompt[language])}><Text style={styles.fine}>{en ? 'A little inspiration' : '找一点灵感'}</Text></Pressable><Text style={styles.fine}>{text.length} {en ? 'characters' : '字'}</Text><Pressable accessibilityRole="button" disabled={!text.trim()} style={styles.textAction} onPress={() => void exportWriting()}><Text style={styles.fine}>{en ? 'Export' : '导出'} ↓</Text></Pressable></View>
          <View style={styles.paperFooter}><Text style={styles.fine}>{en ? 'Only in your personal notes.' : '仅保存到个人记录。'} · {text.length} {en ? 'characters' : '字'}</Text><Pressable accessibilityRole="button" disabled={busy || !text.trim()} style={[styles.primary, (busy || !text.trim()) && styles.disabled]} onPress={() => void save()}><Text style={styles.primaryText}>{busy ? '…' : en ? 'Save privately' : '保存到记录'} ✓</Text></Pressable></View>
          {saved && <Text accessibilityLiveRegion="polite" style={styles.saved}>{en ? 'Saved privately.' : '已私人保存。'}</Text>}
        </View>
        {compose && <><Pressable accessibilityRole="button" style={styles.textAction} onPress={() => setAssistOpen(!assistOpen)}><Text style={styles.muted}>{en ? 'Create with Invelle' : '与此间一起创作'} {assistOpen ? '−' : '+'}</Text></Pressable>{assistOpen && <GeneratedDraft key={editorKey} api={api} en={en} draft={text} onUse={(value, messageId) => { generatedSource.current = { text: value, messageId }; setText(value); setSaved(false); }} />}</>}
        <Pressable accessibilityRole="button" style={styles.textAction} onPress={() => setDetailsOpen(!detailsOpen)}><Text style={styles.muted}>{en ? 'Privacy, memory & versions' : '隐私、记忆与修订'} {detailsOpen ? '−' : '+'}</Text></Pressable>
        {detailsOpen && <>{note && <RevisionHistory key={`${note.id}-${note.currentRevisionId}`} api={api} en={en} note={note} />}<MemoryPanel key={note?.id || 'new'} api={api} en={en} note={note} />{note && <View style={styles.toolbar}><Button title={note.favorite ? (en ? 'Unfavorite' : '取消收藏') : (en ? 'Favorite' : '收藏')} disabled={busy || !saved && text !== note.text} onPress={() => void changeState({ favorite: !note.favorite })} /><Button title={note.pinned ? (en ? 'Unpin' : '取消置顶') : (en ? 'Pin' : '置顶')} disabled={busy} onPress={() => void changeState({ pinned: !note.pinned })} /><Button title={note.status === 'ARCHIVED' ? (en ? 'Restore' : '取消归档') : (en ? 'Archive' : '归档')} disabled={busy} onPress={() => void changeState({ archived: note.status !== 'ARCHIVED' })} /><Button title={note.allowedScopes?.some(value => value.scope === 'PERSISTENT' && value.state === 'ACTIVE') ? (en ? 'Withdraw permission' : '撤回以后参考') : (en ? 'Allow future reference' : '允许以后参考')} disabled={busy} onPress={permission} /><Button title={en ? 'Delete' : '删除'} disabled={busy} onPress={remove} /></View>}</>}
      </> : viewing && note ? <>
        <Pressable accessibilityRole="button" style={styles.back} onPress={() => { selectionRequest.current++; setViewing(false); void refresh(); }}><Text style={styles.muted}>← {en ? (compose ? 'Back to my pages' : 'Back to notes') : (compose ? '回到创作列表' : '回到记录')}</Text></Pressable>
        <View style={styles.pageHeading}><View style={styles.headingCopy}><Text style={styles.eyebrow}>{en ? 'PRIVATE NOTE' : '私人记录'}</Text><Text style={[styles.pageDescription, { marginTop: 8 }]}>{date(note.capturedAt || note.updatedAt || note.createdAt)}</Text></View><Pressable accessibilityRole="button" accessibilityLabel={en ? 'Edit note' : '编辑记录'} style={styles.primary} onPress={() => { setViewing(false); setEditing(true); setDetailsOpen(false); }}><Text style={styles.primaryText}>{en ? 'Edit' : '编辑'}</Text></Pressable></View>
        <View style={styles.paper}>
          {!!title && <Text style={[styles.paperTitle, serif]}>{title}</Text>}
          {!!tags.length && <View style={styles.tagRow}>{tags.map(tag => <View key={tag} style={styles.tagChip}><Text style={styles.tagChipText}>{tag}</Text></View>)}</View>}
          <Text selectable style={[styles.paperWriting, serif]}>{text}</Text>
        </View>
      </> : <>
        <View style={styles.pageHeading}><View style={styles.headingCopy}><Text style={styles.eyebrow}>{compose ? (en ? 'INVELLE · MY PAGES' : '此间 · 我的创作') : (en ? 'YOUR OWN SPACE' : '属于你的空间')}</Text><Text style={[styles.pageTitle, serif]}>{en ? (compose ? 'A little of you, on a page.' : 'My notes.') : (compose ? '在文字里，留下一点自己。' : '我的记录。')}</Text><Text style={styles.pageDescription}>{en ? (compose ? 'Small stories, passing thoughts, a little light from your day.' : 'Your voice and words, kept in your own space.') : (compose ? '写一段生活，留住一个念头，也留下你的故事。' : '你的声音和文字，安心留在这里。')}</Text></View><Pressable accessibilityRole="button" disabled={busy} style={styles.primary} onPress={fresh}><Text style={styles.primaryText}>{en ? 'Write a page' : '写一篇'} ↗</Text></Pressable></View>
        {!compose && <View style={styles.voiceEntry}><View style={styles.headingCopy}><Text style={styles.eyebrow}>{en ? 'A MOMENT TO KEEP' : '留住此刻'}</Text><Text style={[styles.voiceEntryTitle, serif]}>{en ? 'Let your voice become a note.' : '把这一刻，说出来。'}</Text><Text style={styles.pageDescription}>{en ? 'Speak freely. Review the words before saving.' : '慢慢说，确认文字后再保存。'}</Text></View><VoiceInput correctVoiceSentence={(request, signal) => api.correctVoiceSentence(request, signal)} en={en} target={en ? 'New note' : '新记录'} label={en ? 'Speak' : '语音输入'} onUse={value => { freshNow(); setText(value); }} disabled={busy} maxLength={50000} autoStartRequest={voiceEntryRequest} onAutoStartRequestConsumed={onVoiceEntryRequestConsumed} /></View>}
        <View style={styles.row}><Text style={styles.listLabel}>{en ? (compose ? 'My writing' : 'All notes') : (compose ? '我的文字' : '全部记录')}</Text><View style={styles.row}>{(['ALL', 'FAVORITE', 'ARCHIVED'] as const).map(value => <Pressable key={value} accessibilityRole="button" accessibilityState={{ selected: filter === value }} style={styles.filterButton} onPress={() => { filterRef.current = value; setFilter(value); void refresh().catch(e => setError(failure(e, en))); }}><Text style={[styles.fine, filter === value && styles.activeFilter]}>{value === 'ALL' ? (en ? 'All' : '全部') : value === 'FAVORITE' ? (en ? 'Favorites' : '收藏') : (en ? 'Archived' : '归档')}</Text></Pressable>)}</View></View>
        {!filteredItems.length && <View style={styles.quietEmpty}><Text style={styles.emptyStroke}>◯</Text><Text style={[styles.emptyTitle, serif]}>{en ? 'A thought can stay here.' : '把一段想法，留在这里。'}</Text><Text style={styles.pageDescription}>{en ? 'Begin with a sentence, or say it aloud.' : '从一句话开始，也可以说出来。'}</Text><VoiceInput correctVoiceSentence={(request, signal) => api.correctVoiceSentence(request, signal)} en={en} target={en ? 'New writing' : '新文字'} onUse={value => { freshNow(); setText(value); }} maxLength={50000} /></View>}
        {filteredItems.map(item => <View key={item.id} style={styles.noteRow}><Pressable accessibilityRole="button" onPress={() => void open(item)}><View style={styles.row}><Text style={styles.fine}>{en ? 'PRIVATE' : '私人文字'}{item.pinned ? (en ? ' · Pinned' : ' · 已置顶') : ''}{item.status === 'ARCHIVED' ? (en ? ' · Archived' : ' · 已归档') : ''}</Text><Text style={styles.fine}>{date(item.capturedAt || item.updatedAt || item.createdAt)}</Text></View>{!titleRepeatsBody(item) && <Text style={[styles.noteTitle, serif]}>{item.favorite ? '☆ ' : ''}{item.title || (en ? 'Untitled' : '无标题')}</Text>}{!!item.tags?.length && <View style={styles.tagRow}>{item.tags.map(tag => <View key={tag} style={styles.tagChip}><Text style={styles.tagChipText}>{tag}</Text></View>)}</View>}<Text numberOfLines={3} style={styles.noteExcerpt}>{item.favorite && titleRepeatsBody(item) ? '☆ ' : ''}{item.text}</Text><Text style={styles.noteRead}>{en ? 'Read the whole page' : '展开阅读'} →</Text></Pressable><View style={styles.noteActions}><Button title={en ? 'Edit' : '编辑'} disabled={busy} onPress={() => open(item, true)} /><Button title={item.pinned ? (en ? 'Unpin' : '取消置顶') : (en ? 'Pin' : '置顶')} disabled={busy} onPress={() => void togglePinned(item)} /><Button title={en ? 'Delete' : '删除'} disabled={busy} onPress={() => removeItem(item)} /></View></View>)}
        {cursor && <Button title={en ? 'Load more' : '更多记录'} onPress={() => { const query = filterRef.current; void api.notes(cursor, query).then(page => { if (alive.current && filterRef.current === query) { setItems(value => [...value, ...page.items]); setCursor(page.hasMore ? page.nextCursor || null : null); } }).catch(e => setError(failure(e, en))); }} />}
        <Text style={styles.storage}>{en ? 'Your words stay in your account. Nothing is published automatically.' : '文字留在你的账号里，不会自动公开。'}</Text>
      </>}
      {busy && <ActivityIndicator color="#355a45" />}{!!error && <Text style={styles.error}>{error}</Text>}
    </ScrollView>
  </View>;
}
const styles = StyleSheet.create({ chatEmpty: { minHeight: 250, alignItems: 'center', justifyContent: 'center', paddingVertical: 36 }, backdrop: { flex: 1, backgroundColor: '#25382f55', justifyContent: 'center', padding: 24 }, optionSheet: { backgroundColor: '#f4f2e9', borderRadius: 10, padding: 25, gap: 18 }, optionTitle: { fontFamily: 'Songti SC', fontSize: 25, color: '#405640' }, optionActions: { gap: 13, alignItems: 'flex-start' },
  writingContent: { paddingTop: 20, paddingBottom: 36 }, pageHeading: { flexDirection: 'row', alignItems: 'flex-start', gap: 14, marginBottom: 28 }, headingCopy: { flex: 1 }, pageTitle: { fontFamily: 'Georgia', fontSize: 28, lineHeight: 40, color: '#405640', marginTop: 9 }, pageDescription: { fontSize: 12, lineHeight: 22, color: '#8a957e', marginTop: 10 }, eyebrow: { color: '#78866b', fontSize: 9, letterSpacing: 1.3 }, row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 }, iconButton: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, textAction: { paddingVertical: 12, minHeight: 44 }, back: { paddingVertical: 8, marginBottom: 15, minHeight: 44 }, primary: { backgroundColor: '#526b48', borderRadius: 4, paddingHorizontal: 15, paddingVertical: 13, minHeight: 44, alignItems: 'center', marginTop: 8 }, primaryText: { color: '#fffef4', fontSize: 12 }, disabled: { opacity: .4 }, fine: { color: '#8d967e', fontSize: 10, lineHeight: 18 }, paper: { backgroundColor: '#fffef8', borderWidth: 1, borderColor: '#dfe0d2', borderRadius: 8, padding: 18, shadowColor: '#354728', shadowOffset: { width: 0, height: 4 }, shadowOpacity: .04, shadowRadius: 16, marginBottom: 12 }, paperHint: { fontSize: 11, color: '#869079', marginTop: 7, lineHeight: 20 }, starts: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, borderBottomWidth: 1, borderBottomColor: '#e8e9dd', paddingBottom: 16, marginTop: 18 }, start: { borderWidth: 1, borderColor: '#e5e7d9', borderRadius: 4, backgroundColor: '#f8f9f1', minHeight: 36, paddingHorizontal: 9, justifyContent: 'center' }, startText: { color: '#7b856c', fontSize: 10 }, titleRow: { flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: '#e7e8db', marginTop: 10 }, paperTitle: { flex: 1, fontFamily: 'Georgia', fontSize: 23, color: '#405640', paddingVertical: 16, minHeight: 58 }, tagEditor: { borderBottomWidth: 1, borderBottomColor: '#e7e8db', marginTop: 10, paddingBottom: 7 }, tagInput: { minHeight: 42, maxHeight: 72, paddingVertical: 8, color: '#4c6347', fontSize: 12 }, paperBody: { overflow: 'hidden', marginTop: 8 }, paperLine: { position: 'absolute', left: 0, right: 0, height: 1, backgroundColor: '#e3e7d955' }, paperWriting: { minHeight: 280, maxHeight: 650, textAlignVertical: 'top', paddingTop: 15, paddingBottom: 15, fontFamily: 'Georgia', fontSize: 18, lineHeight: 32, color: '#4c6347', backgroundColor: 'transparent' }, paperFooter: { borderTopWidth: 1, borderTopColor: '#e4e6d8', paddingTop: 17, gap: 9, alignItems: 'flex-start' }, saved: { color: '#526b48', fontSize: 12, paddingTop: 12 }, quoteCard: { marginHorizontal: 2, marginBottom: 28, padding: 18, backgroundColor: '#eeeede', borderWidth: 1, borderColor: '#dddcc5', borderRadius: 4, transform: [{ rotate: '-.6deg' }], shadowColor: '#344624', shadowOffset: { width: 0, height: 8 }, shadowOpacity: .08, shadowRadius: 12 }, quoteTape: { position: 'absolute', top: -8, alignSelf: 'center', width: 68, height: 18, backgroundColor: '#e0debd94', transform: [{ rotate: '-3deg' }] }, quotation: { fontFamily: 'Georgia', fontSize: 22, lineHeight: 35, color: '#3f5136', marginTop: 8, marginBottom: 14 }, quoteCredit: { fontSize: 10, color: '#7b8065', lineHeight: 18 }, listLabel: { fontSize: 15, color: '#536c44' }, filterButton: { paddingVertical: 12, minHeight: 44 }, activeFilter: { color: '#45653e', fontWeight: '600' }, noteRow: { borderBottomWidth: 1, borderBottomColor: '#dfe3d3', paddingVertical: 24 }, noteTitle: { fontFamily: 'Georgia', fontSize: 23, lineHeight: 34, color: '#4c6543', marginTop: 12 }, tagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 9 }, tagChip: { backgroundColor: '#eef1e6', borderRadius: 10, paddingHorizontal: 8, paddingVertical: 4 }, tagChipText: { color: '#6e7e63', fontSize: 10, lineHeight: 15 }, noteExcerpt: { fontSize: 13, lineHeight: 25, color: '#89977d', marginTop: 10 }, noteRead: { fontSize: 10, color: '#80916f', marginTop: 15 }, noteActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 12 }, storage: { fontSize: 10, lineHeight: 20, textAlign: 'center', color: '#9ba38e', paddingTop: 28 }, quietEmpty: { alignItems: 'center', paddingVertical: 38, gap: 12 }, emptyStroke: { fontSize: 45, fontWeight: '200', color: '#b3bea1' }, emptyTitle: { fontFamily: 'Georgia', color: '#637957', fontSize: 21 }, voiceEntry: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#edf1e3', borderWidth: 1, borderColor: '#dde4d2', borderRadius: 9, padding: 22, gap: 12, marginBottom: 28 }, voiceEntryTitle: { fontFamily: 'Georgia', fontSize: 21, lineHeight: 32, color: '#506946', marginTop: 10 }, chatHeading: { flexDirection: 'row', paddingHorizontal: 22, marginTop: 18, marginBottom: 10, alignItems: 'center', justifyContent: 'space-between' }, chatTitle: { fontFamily: 'Georgia', fontSize: 28, color: '#405640' }, chatResponse: { paddingVertical: 8, marginBottom: 24 }, chatUser: { alignSelf: 'flex-end', maxWidth: '89%', backgroundColor: '#e9ede4', borderRadius: 10, borderBottomRightRadius: 2, paddingHorizontal: 16, paddingVertical: 12, marginBottom: 24 }, chatBottom: { paddingHorizontal: 20, paddingTop: 8, paddingBottom: 4, gap: 8 }, chatComposer: { flexDirection: 'row', alignItems: 'flex-end', borderWidth: 1, borderColor: '#cbd2c3', borderRadius: 12, paddingHorizontal: 10, paddingVertical: 8, gap: 3 }, chatInput: { flex: 1, fontSize: 15, lineHeight: 24, minHeight: 44, maxHeight: 120, paddingHorizontal: 5, paddingVertical: 10, color: '#4c6347' }, send: { width: 44, height: 44, borderRadius: 8, backgroundColor: '#526b48', alignItems: 'center', justifyContent: 'center' }, sendText: { fontSize: 23, color: '#fffef4' },
 fill: { flex: 1 }, empty: { flex: 1, justifyContent: 'center', padding: 32, gap: 18 }, toolbar: { paddingHorizontal: 18, paddingVertical: 10, flexDirection: 'row', gap: 10, alignItems: 'center', flexWrap: 'wrap' }, heading: { color: '#2c4938', fontSize: 25, flexGrow: 1 }, text: { color: '#2c4938', fontSize: 16, lineHeight: 31 }, muted: { color: '#758174', fontSize: 12, lineHeight: 19 }, label: { color: '#8a957e', fontSize: 10, letterSpacing: 1.1, marginBottom: 6 }, welcome: { color: '#5b725d', fontSize: 22, lineHeight: 34, paddingVertical: 42 }, list: { flex: 1, paddingHorizontal: 20 }, card: { padding: 18, marginVertical: 8, backgroundColor: '#f9faf2', borderRadius: 6, borderWidth: 1, borderColor: '#e3e6dc' }, user: { backgroundColor: 'transparent', marginLeft: 30, borderWidth: 0 }, button: { borderWidth: 1, borderColor: '#a9b7a5', paddingHorizontal: 13, paddingVertical: 9, borderRadius: 18 }, buttonText: { color: '#355a45', fontSize: 13 }, composerTools: { flexDirection: 'row' }, composerActions: { flexDirection: 'row', justifyContent: 'space-between', gap: 10 }, composer: { paddingHorizontal: 20, paddingVertical: 10, gap: 4, borderTopWidth: 1, borderTopColor: '#d9dfd5', backgroundColor: '#f5f4ed' }, input: { borderWidth: 1, borderColor: '#ccd5c8', borderRadius: 3, padding: 13, fontSize: 16, color: '#2c4938', backgroundColor: '#fffdf7', minHeight: 48, marginVertical: 8, maxHeight: 130 }, writing: { minHeight: 250, maxHeight: 600, textAlignVertical: 'top', borderWidth: 0, backgroundColor: 'transparent', fontFamily: 'Georgia', fontSize: 19, lineHeight: 32, paddingHorizontal: 0 }, source: { paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#dde3d9' }, error: { color: '#a33f30', padding: 15, lineHeight: 19 } });
