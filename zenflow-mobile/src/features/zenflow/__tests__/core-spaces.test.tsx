import React from 'react';
import { act, fireEvent, render } from '@testing-library/react-native';
import { CoreSpace } from '../core-spaces';
import type { ZenflowSession } from '@/core/zenflow/session';
jest.mock('react-native-enriched-markdown', () => ({ EnrichedMarkdownText: ({ markdown }: { markdown: string }) => <>{require('react').createElement(require('react-native').Text, null, markdown)}</> }));
jest.mock('../private-audio-notes',()=>({PrivateAudioNotes:()=>null}));
jest.mock('react-native-webview', () => ({ WebView: require('react-native').View }));
jest.mock('../voice-input', () => {
  const React = require('react');
  const { Pressable, Text } = require('react-native');
  return { VoiceInput: ({ en, target, label, autoStartRequest = 0, onAutoStartRequestConsumed }: any) => {
    const lastRequest = React.useRef(0);
    React.useEffect(() => {
      if (autoStartRequest && autoStartRequest !== lastRequest.current) {
        lastRequest.current = autoStartRequest;
        onAutoStartRequestConsumed?.(autoStartRequest);
      }
    }, [autoStartRequest, onAutoStartRequestConsumed]);
    return <Pressable accessibilityRole="button" accessibilityLabel={en ? `Voice input: ${target}` : `语音输入：${target}`}><Text>{label || (en ? 'Speak' : '说出来')}</Text></Pressable>;
  } };
});

const note = { id: 'note1', title: '一个小小的片刻', text: '今天，我看见一束光。', version: 1, currentRevisionId: 'r1', status: 'ACTIVE', favorite: false };
test('creation opens as a real list, opens its saved page, and saves changes through the existing API', async () => {
  const request = jest.fn(async (path: string, method?: string, body?: object) => {
    if (path === 'notes/note1' && method === 'PATCH') return { ...note, ...body, version: 2 };
    if (path === 'notes/note1') return note;
    if (path.startsWith('notes')) return { items: [note], hasMore: false };
    throw new Error('Unexpected route');
  });
  const onFocusChange = jest.fn();
  const screen = await render(<CoreSpace session={{ request } as unknown as ZenflowSession} space="create" english={false} ready onFocusChange={onFocusChange} />);
  expect(screen.queryByLabelText('私人文字')).toBeNull();
  await fireEvent.press(screen.getByText('编辑'));
  expect(screen.getByLabelText('私人文字').props.value).toBe(note.text);
  expect(screen.getByText('属于你的这一页')).toBeTruthy();
  await fireEvent.changeText(screen.getByLabelText('私人文字'), '我想把这一束光留下。');
  await fireEvent.press(screen.getByText('保存到记录 ✓'));
  expect(request).toHaveBeenCalledWith('notes/note1', 'PATCH', expect.objectContaining({ text: '我想把这一束光留下。', expectedVersion: 1 }));
  await fireEvent.press(screen.getByText('专心写 ↗'));
  expect(onFocusChange).toHaveBeenLastCalledWith(true);
  await fireEvent.press(screen.getByText('← 回到创作列表'));
  expect(onFocusChange).toHaveBeenLastCalledWith(false);
  expect(screen.queryByLabelText('私人文字')).toBeNull();
});

test('record filters keep the selected query when loading subsequent pages', async () => {
  const request = jest.fn(async (_path: string) => ({ items: [], hasMore: true, nextCursor: 'next' }));
  const screen = await render(<CoreSpace session={{ request } as unknown as ZenflowSession} space="notes" english={false} ready />);
  expect(screen.getByLabelText('语音输入：新记录')).toBeTruthy();
  await fireEvent.press(screen.getByText('归档'));
  expect(request).toHaveBeenCalledWith('notes?status=ARCHIVED');
  await act(async () => { await fireEvent.press(screen.getByText('更多记录')); });
  expect(request).toHaveBeenCalledWith('notes?cursor=next&status=ARCHIVED');
});

test('opening a saved note is read-only until edit is requested', async () => {
  const request = jest.fn(async (path: string) => {
    if (path === 'notes/note1') return note;
    if (path.startsWith('notes')) return { items: [note], hasMore: false };
    throw new Error('Unexpected route');
  });
  const screen = await render(<CoreSpace session={{ request } as unknown as ZenflowSession} space="notes" english={false} ready />);

  await fireEvent.press(screen.getByText(note.title));
  expect(screen.getByText(note.text)).toBeTruthy();
  expect(screen.queryByLabelText('私人文字')).toBeNull();
  expect(screen.queryByLabelText('标题')).toBeNull();

  await fireEvent.press(screen.getByLabelText('编辑记录'));
  expect(screen.getByLabelText('私人文字').props.value).toBe(note.text);
  await fireEvent.press(screen.getByText('← 回到记录'));
  expect(screen.queryByLabelText('私人文字')).toBeNull();
});

test('a consumed voice request does not reopen after opening and leaving a saved note', async () => {
  const onConsumed = jest.fn();
  function VoiceRequestHarness() {
    const [requestId, setRequestId] = React.useState(1);
    return <CoreSpace session={{ request: jest.fn(async (path: string) => path === 'notes/note1' ? note : { items: [note], hasMore: false }) } as unknown as ZenflowSession}
      space="notes" english={false} ready voiceEntryRequest={requestId}
      onVoiceEntryRequestConsumed={value => { onConsumed(value); setRequestId(current => current === value ? 0 : current); }} />;
  }
  const screen = await render(<VoiceRequestHarness />);
  expect(onConsumed).toHaveBeenCalledTimes(1);
  expect(onConsumed).toHaveBeenCalledWith(1);

  await fireEvent.press(screen.getByText(note.title));
  expect(onConsumed).toHaveBeenCalledTimes(1);
  await fireEvent.press(screen.getByLabelText('编辑记录'));
  expect(onConsumed).toHaveBeenCalledTimes(1);
  await fireEvent.press(screen.getByText('← 回到记录'));
  expect(onConsumed).toHaveBeenCalledTimes(1);
});

test('consecutive chat sends show the accepted question and connect the stream before reloading history', async () => {
  const conversation = { id: 'c12', title: '连续对话', version: 1, status: 'ACTIVE', activeRunId: null };
  let round = 0, finished = false;
  const canonical: object[] = [];
  let complete: () => void = () => {};
  const request = jest.fn(async (path: string, method?: string, body?: any) => {
    if (path === 'conversations' && method === 'POST') return conversation;
    if (path.startsWith('conversations?')) return { items: [conversation], hasMore: false };
    if (path === 'conversations/c12/messages' && method === 'POST') { round++; finished = false; canonical.push({ messageId: `u${round}`, role: 'USER', content: body.text, status: 'COMPLETED', sequenceNo: round * 2 - 1 }); return { runId: `r${round}`, responseMessageId: `a${round}` }; }
    if (path === 'conversations/c12/messages') return { items: [...canonical], hasMore: false };
    if (path.startsWith('runs/')) return { runId: `r${round}`, status: finished ? 'COMPLETED' : 'RUNNING', draftContent: '', lastEventSeq: 0 };
    if (path.endsWith('/citations')) return [];
    throw new Error('Unexpected route ' + path);
  });
  const stream = jest.fn(async (_path: string, receive: (text: string) => void) => new Promise<void>(resolve => { complete = () => { const answer = `第${round}轮回答`; receive(`data: ${JSON.stringify({ runId: `r${round}`, seq: 1, type: 'RESPONSE_DELTA', data: { delta: answer } })}\n\n`); finished = true; canonical.push({ messageId: `a${round}`, role: 'ASSISTANT', content: answer, status: 'COMPLETED', sequenceNo: round * 2 }); resolve(); }; }));
  const screen = await render(<CoreSpace session={{ request, stream } as unknown as ZenflowSession} space="chat" english={false} ready />);
  for (let turn = 1; turn <= 2; turn++) {
    await fireEvent.changeText(screen.getByLabelText('消息'), `问题${turn}`);
    await fireEvent.press(screen.getByLabelText('发送消息'));
    expect(stream).toHaveBeenCalledTimes(turn);
    expect(screen.getByText(`问题${turn}`)).toBeTruthy();
    expect(request.mock.calls.filter(([path, method]) => path === 'conversations/c12/messages' && method !== 'POST')).toHaveLength(turn - 1);
    await act(async () => complete());
    expect(screen.getByText(`第${turn}轮回答`)).toBeTruthy();
  }
});

test('explicitly restores an encrypted local draft and retains its original cloud version',async()=>{
  const body=JSON.stringify({formatVersion:1,targetId:'note1',sourceVersion:1,clientRequestId:'restore-request',title:'本机标题',text:'未同步的本机文字',tagsText:''});
  const store={listDrafts:jest.fn(async()=>({items:[],nextCursor:null})),readDraft:jest.fn(async()=>({body})),saveDraft:jest.fn(async()=>{}),discardDraftIfBodyMatches:jest.fn(async()=>{})};
  const request=jest.fn(async(path:string,method?:string)=>{
    if(path==='notes/note1'&&method==='PATCH')throw Error('VERSION_CONFLICT');
    if(path==='notes/note1')return {...note,version:2};
    return {items:[note],hasMore:false};
  });
  const screen=await render(<CoreSpace session={{request} as unknown as ZenflowSession} space="notes" english={false} ready localDrafts={{store:store as any,generation:9,registerFlush:()=>()=>{}}}/>);
  expect(screen.queryByLabelText('私人文字')).toBeNull();
  await fireEvent.press(screen.getByText('恢复本机草稿'));
  expect(screen.getByLabelText('私人文字').props.value).toBe('未同步的本机文字');
  await fireEvent.press(screen.getByText('保存到记录 ✓'));
  expect(request).toHaveBeenCalledWith('notes/note1','PATCH',expect.objectContaining({expectedVersion:1,text:'未同步的本机文字'}));
  expect(store.discardDraftIfBodyMatches).not.toHaveBeenCalled();
  expect(screen.getByLabelText('私人文字').props.value).toBe('未同步的本机文字');
});


test('two new pages survive switching, and selecting either draft restores only its own text',async()=>{
  const drafts=new Map<string,any>();
  const store={
    readDraft:jest.fn(async(id:string)=>drafts.get(id)||null),
    saveDraft:jest.fn(async(id:string,kind:string,body:string)=>{const draft={id,kind,body,localRevision:1,persistedAt:Date.now()};drafts.set(id,draft);return draft;}),
    listDrafts:jest.fn(async()=>({items:[...drafts.values()].map(({body,...metadata})=>metadata),nextCursor:null})),
    discardDraftIfBodyMatches:jest.fn(async()=>{})
  };
  const request=jest.fn(async()=>({items:[],hasMore:false}));
  const screen=await render(<CoreSpace session={{request} as unknown as ZenflowSession} space="notes" english={false} ready localDrafts={{store:store as any,generation:1,registerFlush:()=>()=>{}}}/>);
  for(const text of ['first separate page','second separate page']){
    await fireEvent.press(screen.getByText('写一篇 ↗'));
    await fireEvent.changeText(screen.getByLabelText('私人文字'),text);
    await fireEvent.press(screen.getByText('← 回到记录'));
  }
  expect(drafts.size).toBe(2);
  expect([...drafts.values()].map(draft=>JSON.parse(draft.body).text)).toEqual(['first separate page','second separate page']);
  const buttons=screen.getAllByText(/^草稿 [12] ·/);
  await fireEvent.press(buttons[0]);
  await fireEvent.press(screen.getByText('恢复本机草稿'));
  expect(screen.getByLabelText('私人文字').props.value).toBe('first separate page');
  expect(drafts.size).toBe(2);
});
