import type { ZenflowSession } from './session';
import type { VoiceSentenceCorrectionRequest, VoiceSentenceCorrectionResponse } from '@/contracts/zenflow/voice';
export type Conversation = { id: string; title: string; version: number; status: string; activeRunId: string | null };
export type Message = { messageId: string; role: 'USER' | 'ASSISTANT'; content: string; status: string; sequenceNo: number };
export type Run = { runId: string; status: string; draftContent: string; lastEventSeq: number; errorCode?: string; errorMessageSafe?: string };
export type NoteMedia = { id:string;mime:string;bytes:number;durationSeconds:number };
export type Note = { id: string; title: string; text: string; version: number; currentRevisionId: string; revisionNo?:number; media?:NoteMedia[]; status: string; favorite: boolean; pinned?: boolean; createdAt?: string; updatedAt?: string; capturedAt?: string; tags?: string[]; allowedScopes?: { id: string; scope: string; scopeKey: string; state: string; version: number }[] };
export type Citation = { runId: string; citationKey: string; title: string; sourceType: string; excerpt: string; noteId?: string; revisionId: string; snapshotId?: string; documentId?: string; sourceLocator?: string; visibility?: 'PRIVATE' | 'PUBLIC' | 'TITLE_ONLY' };
export type Revision = { id: string; revisionNo: number; text: string; createdAt: string; sourceKind: string };
export type Memory = { id: string; summary: string; state: string; version: number; noteId: string };
export type Preferences = { memoryEnabled: boolean; version: number };
export type Page<T> = { items: T[]; hasMore: boolean; nextCursor?: string | null };
export type Event = { runId: string; seq: number; type: string; data: Record<string, unknown> };
export function isRunTerminal(status: string): boolean { return ['COMPLETED', 'FAILED', 'CANCELLED'].includes(status); }
export function stableId(): string { return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`; }
export class EventFrames {
  private buffer = '';
  push(chunk: string): Event[] {
    this.buffer = (this.buffer + chunk).replace(/\r\n/g, '\n');
    if (this.buffer.length > 1024 * 1024) throw new Error('STREAM_FRAME_TOO_LARGE');
    const events: Event[] = [];
    while (true) {
      const end = this.buffer.indexOf('\n\n'); if (end < 0) break;
      const frame = this.buffer.slice(0, end); this.buffer = this.buffer.slice(end + 2);
      const data = frame.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
      if (data) { const value = JSON.parse(data); if (typeof value.runId !== 'string' || !Number.isSafeInteger(Number(value.seq)) || Number(value.seq) < 1 || typeof value.type !== 'string') throw new Error('STREAM_EVENT_INVALID'); events.push({ ...value, seq: Number(value.seq) }); }
    }
    return events;
  }
}
export class CoreApi {
  constructor(private session: ZenflowSession) {}
  conversations(cursor?: string, status: 'ACTIVE' | 'ARCHIVED' = 'ACTIVE') { return this.session.request<Page<Conversation>>(`conversations?status=${status}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`); }
  conversation(id: string) { return this.session.request<Conversation>(`conversations/${id}`); }
  createConversation(english: boolean) { return this.session.request<Conversation>('conversations', 'POST', { locale: english ? 'en-US' : 'zh-CN', title: english ? 'A little room to talk' : '留一小会儿，聊聊' }); }
  correctVoiceSentence(input: VoiceSentenceCorrectionRequest, signal: AbortSignal) { return this.session.request<VoiceSentenceCorrectionResponse>('voice/corrections', 'POST', input, {}, signal); }
  messages(id: string, before?: number) { return this.session.request<{ items: Message[]; hasMore: boolean; nextBeforeSequence: number | null }>(`conversations/${id}/messages${before ? `?beforeSeq=${before}` : ''}`); }
  send(id: string, clientMessageId: string, text: string, notes: Note[] = []) { return this.session.request<{ runId: string; responseMessageId: string }>(`conversations/${id}/messages`, 'POST', { clientMessageId, text, context: { capsules: notes.map(note => ({ id: note.id, revisionId: note.currentRevisionId, scope: 'TURN' })) } }); }
  messageCitations(conversationId: string, messageId: string) { return this.session.request<Citation[]>(`conversations/${conversationId}/messages/${messageId}/citations`); }
  citations(id: string) { return this.session.request<Citation[]>(`runs/${id}/citations`); }
  citation(id: string, key: string) { return this.session.request<Citation>(`runs/${id}/citations/${encodeURIComponent(key)}`); }
  run(id: string) { return this.session.request<Run>(`runs/${id}`); }
  cancel(id: string) { return this.session.request(`runs/${id}/cancel`, 'POST', undefined, { 'Idempotency-Key': stableId() }); }
  conversationState(item: Conversation, status: 'ACTIVE' | 'ARCHIVED') { return this.session.request<Conversation>(`conversations/${item.id}`, 'PATCH', { status, expectedVersion: item.version }); }
  deleteConversation(item: Conversation) { return this.session.request(`conversations/${item.id}?expectedVersion=${item.version}`, 'DELETE', undefined, { 'Idempotency-Key': stableId() }); }
  async events(id: string, after: number, signal: AbortSignal, receive: (event: Event) => void) { const frames = new EventFrames(); let seq = after; await this.session.stream(`runs/${id}/events?afterSeq=${after}`, chunk => { for (const event of frames.push(chunk)) { if (event.runId !== id) throw new Error('STREAM_RUN_MISMATCH'); if (event.seq <= seq) continue; if (event.seq !== seq + 1) throw new Error('STREAM_GAP'); seq = event.seq; receive(event); } }, signal); }
  notes(cursor?: string, filter: 'ALL' | 'FAVORITE' | 'ARCHIVED' = 'ALL') { const query = new URLSearchParams(); if (cursor) query.set('cursor', cursor); if (filter === 'FAVORITE') query.set('favorite', 'true'); if (filter === 'ARCHIVED') query.set('status', 'ARCHIVED'); const encoded = query.toString(); return this.session.request<Page<Note>>(`notes${encoded ? `?${encoded}` : ''}`); }
  grantFuture(note: Note) { return this.session.request(`notes/${note.id}/consents`, 'POST', { revisionId: note.currentRevisionId, scope: 'PERSISTENT', expectedVersion: note.version }); }
  revoke(note: Note, consent: { id: string; version: number }) { return this.session.request(`notes/${note.id}/consents/${consent.id}?expectedConsentVersion=${consent.version}`, 'DELETE', undefined, { 'Idempotency-Key': stableId() }); }
  versions(id: string, cursor?: string) { return this.session.request<Page<Revision>>(`notes/${id}/versions${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`); }
  preferences() { return this.session.request<Preferences>('me'); }
  memoryEnabled(preferences: Preferences, enabled: boolean) { return this.session.request<Preferences>('me/preferences', 'PATCH', { memoryEnabled: enabled, expectedVersion: preferences.version }); }
  memories(cursor?: string) { return this.session.request<Page<Memory>>(`memories${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`); }
  confirmMemory(consentId: string, summary: string, key = stableId()) { return this.session.request<Memory>('memories', 'POST', { consentId, summary }, { 'Idempotency-Key': key }); }
  removeMemory(memory: Memory) { return this.session.request(`memories/${memory.id}?expectedVersion=${memory.version}`, 'DELETE', undefined, { 'Idempotency-Key': stableId() }); }
  note(id: string) { return this.session.request<Note>(`notes/${id}`); }
  saveExcerpt(title: string, text: string, clientRecordId: string, sourceMessageId: string, tags: string[] = []) { return this.session.request<Note>('notes', 'POST', { clientRecordId, kind: 'CHAT_EXCERPT', title, text, tags, sourceMessageId }); }
  saveNote(id: string | null, title: string, text: string, version: number, clientRecordId: string, tags: string[] = [], capturedAt?: string) { return this.session.request<Note>(id ? `notes/${id}` : 'notes', id ? 'PATCH' : 'POST', id ? { title, text, tags, expectedVersion: version } : { clientRecordId, kind: 'TEXT', title, text, tags, ...(capturedAt ? { capturedAt } : {}) }); }
  noteState(note: Note, values: { archived?: boolean; favorite?: boolean; pinned?: boolean }) { return this.session.request<Note>(`notes/${note.id}/state`, 'PATCH', { ...values, expectedVersion: note.version }); }
  deleteNote(note: Note) { return this.session.request(`notes/${note.id}?expectedVersion=${note.version}`, 'DELETE', undefined, { 'Idempotency-Key': stableId() }); }
}
