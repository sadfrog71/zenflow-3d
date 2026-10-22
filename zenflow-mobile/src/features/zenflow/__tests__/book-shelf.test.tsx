import React from 'react';
import { act, fireEvent, render } from '@testing-library/react-native';
import { annotationUnits, BookShelf } from '../book-shelf';

jest.mock('../book-storage', () => ({
  loadReaderState: async (owner: string) => ({ owner, rememberProgress: false, syncEnabled: false, progress: [], bookmarks: [] }),
  saveReaderState: async () => {}, pruneVersionCache: jest.fn(), getCachedVersion: jest.fn(),
  cachePublishedVersion: jest.fn(), removeCachedVersion: jest.fn(), versionCacheKey: (...parts: string[]) => parts.join(':'),
}));

test('opens only the requested chapter and fetches the next chapter on navigation', async () => {
  const release = { id: '4', sourceVersionId: '2', sourceVersionNo: 1, targetLanguage: 'zh-CN', revisionNo: 1, availableLanguages: ['zh-CN'], versionKey: '2-4' };
  const book = { id: '1', title: '金刚经', author: '', sourceLanguage: 'zh-CN', sourceVersion: '底本', category: 'classics', summary: '', releases: [release] };
  const first = { id: '3', chapterNo: 1, title: '第一章', sourceText: '如是我闻。', text: '我是这样听闻的。', commentary: '', annotations: [] };
  const second = { ...first, id: '5', chapterNo: 2, title: '第二章', text: '第二章的正文' };
  const edition = { ...book, versionId: '4', versionLabel: '底本', language: 'zh-CN', revisionNo: 1, chapterSummaries: [{ id: '3', chapterNo: 1, title: '第一章' }, { id: '5', chapterNo: 2, title: '第二章' }] };
  const originalFetch = globalThis.fetch;
  const fetchMock = jest.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ items: [book] }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ ...edition, chapters: [first] }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ ...edition, chapters: [second] }) });
  globalThis.fetch = fetchMock;
  let screen: Awaited<ReturnType<typeof render>> | undefined;
  try {
    screen = await render(<BookShelf english={false} config={{ origin: 'https://invelleai.com', clientId: 'test', environment: 'test' }} session={null} ownerId={null} onExit={jest.fn()} onSignIn={jest.fn()} />);
    await fireEvent.press(screen.getByText('金刚经')); await fireEvent.press(screen.getByText('开始阅读'));
    expect(fetchMock.mock.calls[1][0]).toContain('chapterNo=1');
    expect(screen.getByText('1 / 2')).toBeTruthy();
    await fireEvent.press(screen.getByText('下一章'));
    expect(fetchMock.mock.calls[2][0]).toContain('chapterNo=2');
    expect(screen.getByText('第二章的正文')).toBeTruthy();
    expect(screen.getByText('2 / 2')).toBeTruthy();
  } finally { await screen?.unmount(); globalThis.fetch = originalFetch; }
});

test('classical edition shows the original, vernacular, inline notes and interpretation', async () => {
  const release = { id: '4', sourceVersionId: '2', sourceVersionNo: 1, targetLanguage: 'zh-CN', revisionNo: 1, availableLanguages: ['zh-CN'], versionKey: '2-4' };
  const book = { id: '1', title: '心经', author: '玄奘', sourceLanguage: 'zh-CN', sourceVersion: '汉文底本', category: 'classics', summary: '原文与译注', releases: [release] };
  const version = { ...book, versionId: '4', versionKey: '2-4', versionLabel: '汉文底本', language: 'zh-CN', targetLanguage: 'zh-CN', revisionNo: 1,
    chapters: [{ id: '3', chapterNo: 1, title: '全经', sourceText: '照見五蘊皆空。', translationText: '观照到五蕴没有独立不变的自性。', text: '照見五蘊皆空。', annotationLayer: 'source', commentary: '本章从身心经验说明空性。', annotations: [{ phrase: '五蘊', note: '五类身心活动', tone: 'word' }] }],
  };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = jest.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ items: [book] }) }).mockResolvedValueOnce({ ok: true, json: async () => version });
  try {
    const screen = await render(<BookShelf english={false} config={{ origin: 'https://invelleai.com', clientId: 'test', environment: 'test' }} session={null} ownerId={null} onExit={jest.fn()} onSignIn={jest.fn()} />);
    await fireEvent.press(await screen.findByText('心经'));
    await fireEvent.press(screen.getByText('开始阅读'));
    expect(await screen.findByText('汉文原文')).toBeTruthy();
    expect(screen.getByText('白话译文')).toBeTruthy();
    expect(screen.getByText('观照到五蕴没有独立不变的自性。')).toBeTruthy();
    expect(screen.getByText('五类身心活动')).toBeTruthy();
    expect(screen.getByText('五蘊')).toBeTruthy();
    expect(screen.getByText('本章从身心经验说明空性。')).toBeTruthy();
    await screen.unmount();
  } finally { globalThis.fetch = originalFetch; }
});

test('stacked meanings keep punctuation with the original words and preserve every source character', () => {
  const text = '「時，長老須菩提。」';
  const units = annotationUnits(text, [
    { id: 0, phrase: '時', note: '这时', start: 1, end: 2 },
    { id: 1, phrase: '長老', note: '年德较高的比丘的尊称', start: 3, end: 5 },
    { id: 2, phrase: '須菩提', note: '佛弟子，名字常译为善现', start: 5, end: 8 },
  ]);
  expect(units.map(unit => unit.original).join('')).toBe(text);
  expect(units.map(unit => unit.original)).toEqual(['「時，', '長老', '須菩提。」']);
  expect(units.every(unit => unit.note)).toBe(true);
});

test.each(['request', 'body'])('a stalled edition %s stops loading and can be retried without accepting its late response', async stage => {
  jest.useFakeTimers();
  const release = { id: '4', sourceVersionId: '2', sourceVersionNo: 1, targetLanguage: 'zh-CN', revisionNo: 1, availableLanguages: ['zh-CN'], versionKey: '2-4' };
  const book = { id: '1', title: '金刚经', author: '', sourceLanguage: 'zh-CN', sourceVersion: '底本', category: 'classics', summary: '', releases: [release] };
  const version = { ...book, versionId: '4', versionLabel: '底本', language: 'zh-CN', revisionNo: 1, chapters: [{ id: '3', chapterNo: 1, title: '法会因由', sourceText: '如是我闻。', text: '我是这样听闻的。', commentary: '', annotations: [] }] };
  let resolveStalled!: (value: unknown) => void;
  const originalFetch = globalThis.fetch;
  const fetchMock = jest.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ items: [book] }) })
    .mockImplementationOnce(() => stage === 'request'
      ? new Promise(resolve => { resolveStalled = resolve; })
      : Promise.resolve({ ok: true, json: () => new Promise(resolve => { resolveStalled = resolve; }) }))
    .mockResolvedValueOnce({ ok: true, json: async () => version });
  globalThis.fetch = fetchMock;
  let screen: Awaited<ReturnType<typeof render>> | undefined;
  try {
    screen = await render(<BookShelf english={false} config={{ origin: 'https://invelleai.com', clientId: 'test', environment: 'test' }} session={null} ownerId={null} onExit={jest.fn()} onSignIn={jest.fn()} />);
    await fireEvent.press(screen.getByText('金刚经'));
    await fireEvent.press(screen.getByText('开始阅读'));
    await act(async () => { await jest.advanceTimersByTimeAsync(20000); });
    expect(screen.getByText('读取超时，请检查网络后重试。')).toBeTruthy();
    expect(fetchMock.mock.calls[1][1].signal.aborted).toBe(true);
    await fireEvent.press(screen.getByText('重新加载'));
    expect(screen.getByText('法会因由')).toBeTruthy();
    await act(async () => { resolveStalled(stage === 'request'
      ? { ok: true, json: async () => ({ ...version, title: '迟到的旧响应' }) }
      : { ...version, title: '迟到的旧响应' }); });
    expect(screen.queryByText('迟到的旧响应')).toBeNull();
  } finally {
    await screen?.unmount(); globalThis.fetch = originalFetch; jest.useRealTimers();
  }
});
