import { audioCoordinator } from '@/core/zenflow/audio-coordinator';
import React from 'react';
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
const mockStart = jest.fn(async (_locale: string, _id: string) => {}), mockCancel = jest.fn(async () => {});
const mockTakePending = jest.fn(async (): Promise<string | null> => null);
let mockListener: (event: object) => void, mockVoiceShortcutListener: () => void;
let mockWebProps: { onMessage: (event: object) => void };
jest.mock('expo-modules-core', () => ({ ...jest.requireActual('expo-modules-core'), requireOptionalNativeModule: () => ({ start: (...args: [string, string]) => mockStart(...args), stop: jest.fn(async () => {}), cancel: () => mockCancel(), takePendingVoiceShortcutRequest: () => mockTakePending(), addListener: (name: string, fn: (event: object) => void) => { if (name === 'onVoiceShortcut') mockVoiceShortcutListener = fn as () => void; else mockListener = fn; return { remove: jest.fn() }; } }) }));
jest.mock('react-native-safe-area-context', () => ({ SafeAreaView: require('react-native').View }));
jest.mock('react-native-webview', () => { const React = require('react'); return { WebView: React.forwardRef(function MockWebView(props: typeof mockWebProps, ref: unknown) { mockWebProps = props; React.useImperativeHandle(ref, () => ({ injectJavaScript: jest.fn() })); return null; }) }; });
import { addVoiceShortcutListener, takePendingVoiceShortcutRequest, VoiceInput } from '../voice-input';
const message = (action: string) => mockWebProps.onMessage({ nativeEvent: { data: JSON.stringify({ action }) } });

test('cancelled and stale speech cannot write; final current speech is used automatically once', async () => {
  const use = jest.fn();
  const screen = await render(<VoiceInput en={false} target="消息" onUse={use} />);
  await fireEvent.press(screen.getByLabelText('语音输入：消息'));
  await act(async () => message('start'));
  const first = mockStart.mock.calls[0] as unknown as string[];
  await act(async () => { mockListener({ sessionId: first[1], state: 'listening', text: '第一段' }); });
  expect(use).not.toHaveBeenCalled();
  await act(async () => message('cancel'));
  await act(async () => { mockListener({ sessionId: first[1], state: 'complete', text: '迟到结果' }); message('confirm'); });
  expect(use).not.toHaveBeenCalled();
  await fireEvent.press(screen.getByLabelText('语音输入：消息'));
  await act(async () => message('start'));
  const second = mockStart.mock.calls[1] as unknown as string[];
  await act(async () => { mockListener({ sessionId: first[1], state: 'complete', text: '旧账号内容' }); });
  await act(async () => { mockListener({ sessionId: second[1], state: 'complete', text: '当前文字' }); });
  await act(async () => { mockListener({ sessionId: second[1], state: 'complete', text: '重复结果' }); message('confirm'); });
  expect(use).toHaveBeenCalledTimes(1);
  expect(use).toHaveBeenCalledWith('当前文字');
});

test('empty recognition does not automatically use or send a message', async () => {
  const use = jest.fn();
  const screen = await render(<VoiceInput en={false} target="消息" onUse={use} sendOnFinish />);
  await fireEvent.press(screen.getByLabelText('语音输入：消息'));
  await act(async () => message('start'));
  const call = mockStart.mock.calls[mockStart.mock.calls.length - 1];
  await act(async () => { mockListener({ sessionId: call[1], state: 'complete', text: '   ' }); });
  expect(use).not.toHaveBeenCalled();
});

test('applies a safe sentence correction before sending the chat transcript once', async () => {
  const use = jest.fn();
  const correct = jest.fn(async (request: { sessionId: string; sentenceId: number; revision: number; rawText: string; previousSentences: string[] }) => ({
    sessionId: request.sessionId, sentenceId: request.sentenceId, revision: request.revision,
    baseTextHash: 'sha256', status: 'applied' as const, text: '校对后的问题。',
  }));
  const screen = await render(<VoiceInput en={false} target="消息" onUse={use} sendOnFinish correctVoiceSentence={correct} />);
  await fireEvent.press(screen.getByLabelText('语音输入：消息'));
  await act(async () => message('start'));
  const call = mockStart.mock.calls[mockStart.mock.calls.length - 1];
  await act(async () => { mockListener({ sessionId: call[1], state: 'complete', text: '识别原文。' }); });
  await waitFor(() => expect(use).toHaveBeenCalledWith('校对后的问题。'));
  expect(correct).toHaveBeenCalledTimes(1);
  await act(async () => { mockListener({ sessionId: call[1], state: 'complete', text: '重复终稿。' }); });
  expect(use).toHaveBeenCalledTimes(1);
});

test('chat voice starts on page ready once without a second start tap and ignores completion after cancel', async () => {
  const use = jest.fn(), before = mockStart.mock.calls.length;
  const screen = await render(<VoiceInput en={false} target="消息" onUse={use} sendOnFinish />);
  await fireEvent.press(screen.getByLabelText('语音输入：消息'));
  await act(async () => { message('ready'); message('ready'); });
  expect(mockStart.mock.calls.length).toBe(before + 1);
  const call = mockStart.mock.calls[before];
  await act(async () => message('cancel'));
  await act(async () => { mockListener({ sessionId: call[1], state: 'complete', text: '已取消的内容' }); message('ready'); message('start'); });
  expect(mockStart.mock.calls.length).toBe(before + 1);
  expect(use).not.toHaveBeenCalled();
});

test('a record-tab voice request opens recognition and starts once without tapping the microphone', async () => {
  const before = mockStart.mock.calls.length, consumed = jest.fn();
  await render(<VoiceInput en={false} target="新记录" onUse={jest.fn()} autoStartRequest={1} onAutoStartRequestConsumed={consumed} />);
  await act(async () => { message('ready'); message('ready'); });
  expect(mockStart.mock.calls.length).toBe(before + 1);
  expect(mockStart.mock.calls[before]?.[0]).toBe('zh-CN');
  expect(consumed).toHaveBeenCalledTimes(1);
  expect(consumed).toHaveBeenCalledWith(1);
});

test('Siri shortcut requests are consumed once through the native bridge', async () => {
  const listener = jest.fn();
  const subscription = addVoiceShortcutListener(listener);
  mockVoiceShortcutListener();
  expect(listener).toHaveBeenCalledTimes(1);

  mockTakePending.mockResolvedValueOnce('voice-request-token');
  expect(await takePendingVoiceShortcutRequest()).toBe(true);
  expect(await takePendingVoiceShortcutRequest()).toBe(false);
  subscription?.remove();
});

test('speech cannot start or cancel another active recording',async()=>{
 const recording=audioCoordinator.acquire('RECORDING');
 const beforeStart=mockStart.mock.calls.length,beforeCancel=mockCancel.mock.calls.length;
 const screen=await render(<VoiceInput en={false} target="消息" onUse={jest.fn()}/>);
 await fireEvent.press(screen.getByLabelText('语音输入：消息'));
 await act(async()=>message('start'));
 expect(mockStart.mock.calls.length).toBe(beforeStart);
 await screen.unmount();expect(mockCancel.mock.calls.length).toBe(beforeCancel);
 recording.assertActive();recording.release();
});
test('cancel holds audio ownership until a pending native start has settled and been cancelled',async()=>{
 let resolve!:()=>void;
 const pending=new Promise<void>(done=>{resolve=done;});mockStart.mockImplementationOnce(()=>pending);
 const screen=await render(<VoiceInput en={false} target="消息" onUse={jest.fn()}/>);
 await fireEvent.press(screen.getByLabelText('语音输入：消息'));
 await act(async()=>message('start'));
 await act(async()=>message('cancel'));
 expect(()=>audioCoordinator.acquire('RECORDING')).toThrow('AUDIO_BUSY');
 await act(async()=>{resolve();});
 const recording=audioCoordinator.acquire('RECORDING');recording.release();await screen.unmount();
});
