import React from 'react';
import { act, render } from '@testing-library/react-native';
import { Text } from 'react-native';
import { RemoteBookReader } from '../remote-book-reader';

const mockPostMessage = jest.fn();
jest.mock('@/platform/zenflow/web-view', () => {
  const React = require('react'); const { View } = require('react-native');
  return { WebView: React.forwardRef((props: any, ref: any) => {
    React.useImperativeHandle(ref, () => ({ postMessage: mockPostMessage }));
    return <View {...props} testID="remote-reader" />;
  }) };
});

const revision = 'abcdef1234567890';
const edition: any = { id: 'book', versionId: 'edition', versionLabel: '底本', title: '金刚经', author: '', language: 'zh-CN', chapters: [{ id: 'chapter1', chapterNo: 1, title: '第一章', text: '如是我闻。\n\n一时。', sourceText: '如是我闻。\n\n一时。', annotations: [] }] };
const props = () => ({ config: { origin: 'https://invelleai.com', clientId: 'test', environment: 'test' }, english: false, edition, chapterIndex: 0, fontSize: 16, initialParagraph: 0, bookmarkedParagraphs: [2], onPosition: jest.fn(), onBookmark: jest.fn(), onChapter: jest.fn(), onFallback: jest.fn(), children: <Text>内置阅读器</Text> });
const originalFetch = globalThis.fetch;
beforeEach(() => { jest.useFakeTimers(); mockPostMessage.mockClear(); globalThis.fetch = jest.fn().mockResolvedValue({ ok: true, json: async () => ({ protocol: 1, revision }) }); });
afterEach(() => { globalThis.fetch = originalFetch; jest.useRealTimers(); });

test('reader receives only its chapter and accepts scoped progress/bookmark messages', async () => {
  const p = props(); const screen = await render(<RemoteBookReader {...p} />);
  const web = screen.getByTestId('remote-reader').props;
  expect(web.source.uri).toBe(`https://invelleai.com/reading-room/reader/releases/${revision}/index.html`);
  await act(async () => web.onMessage({ nativeEvent: { data: JSON.stringify({ protocol: 1, type: 'ready', revision }) } }));
  const context = JSON.parse(mockPostMessage.mock.calls.at(-1)![0]);
  expect(context.chapter.text).toBe(edition.chapters[0].text);
  expect(context.bookmarkedParagraphs).toEqual([2]);
  expect(context.edition.chapters).toBeUndefined();
  expect(context.ownerId).toBeUndefined();
  const message = (type: string, paragraph: number, session = context.session) => web.onMessage({ nativeEvent: { data: JSON.stringify({ protocol: 1, type, session, seq: context.seq, chapterId: 'chapter1', paragraph }) } });
  await act(async () => { message('progress', 2); message('bookmark', 2); message('bookmark', 99); message('progress', 1, 'old-session'); });
  expect(p.onPosition).toHaveBeenCalledWith(2); expect(p.onBookmark).toHaveBeenCalledTimes(1);
  expect(web.onShouldStartLoadWithRequest({ url: 'https://elsewhere.test/' })).toBe(false);
  expect(web.sharedCookiesEnabled).toBe(false);
  await screen.unmount();
  await act(async () => message('bookmark', 1));
  expect(p.onBookmark).toHaveBeenCalledTimes(1);
});

test.each(['unsupported protocol', 'missing manifest', 'unresponsive renderer'])('uses the built-in reader for %s', async reason => {
  if (reason === 'unsupported protocol') globalThis.fetch = jest.fn().mockResolvedValue({ ok: true, json: async () => ({ protocol: 99, revision }) });
  if (reason === 'missing manifest') globalThis.fetch = jest.fn().mockResolvedValue({ ok: false });
  const p = props(); const screen = await render(<RemoteBookReader {...p} />);
  if (reason === 'unresponsive renderer') await act(async () => { await jest.advanceTimersByTimeAsync(8000); });
  expect(screen.getByText('内置阅读器')).toBeTruthy();
  expect(p.onFallback).toHaveBeenCalledWith(0);
  await screen.unmount();
});
