import React from 'react';
import { act, fireEvent, render } from '@testing-library/react-native';
import { Linking } from 'react-native';
import { createOnShouldStartLoadWithRequest } from 'react-native-webview/lib/WebViewShared';
import { ReadingRoom } from '../reading-room';

jest.mock('@/platform/zenflow/web-view', () => {
  const { View } = require('react-native');
  return { WebView: (props: any) => <View {...props} testID="reading-webview" /> };
});

test('native navigation remains available before the room loads', async () => {
  const onBooks = jest.fn();
  const onExit = jest.fn();
  const screen = await render(<ReadingRoom english={false} config={{ origin: 'https://invelleai.com', clientId: 'test', environment: 'test' }} onBooks={onBooks} onExit={onExit} />);
  await fireEvent.press(screen.getByText('选一本书 ↗'));
  await fireEvent.press(screen.getByLabelText('返回首页'));
  expect(onBooks).toHaveBeenCalledTimes(1);
  expect(onExit).toHaveBeenCalledTimes(1);
  await act(async () => screen.getByTestId('reading-webview').props.onContentProcessDidTerminate());
  expect(screen.getByText('直接打开书架')).toBeTruthy();
  await screen.unmount();
});

test.each([false, true])('reading entry stays inside the native WebView (English: %s)', async english => {
  const canOpen = jest.spyOn(Linking, 'canOpenURL').mockResolvedValue(true);
  const open = jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined);
  const screen = await render(<ReadingRoom english={english} config={{ origin: 'https://invelleai.com', clientId: 'test', environment: 'test' }} onBooks={jest.fn()} onExit={jest.fn()} />);
  const props = screen.getByTestId('reading-webview').props;
  expect(props.source.html).toContain('window.InvelleRoomAssets=');
  expect(props.source.html).toContain(english ? "window.InvelleRoomLanguage='en'" : "window.InvelleRoomLanguage='zh'");
  expect(props.source.uri).toBeUndefined();
  const load = jest.fn();
  const handle = createOnShouldStartLoadWithRequest(load, props.originWhitelist);
  await act(async () => {
    handle({ nativeEvent: { url: 'about:blank', lockIdentifier: 1 } } as any);
  });
  expect(load).toHaveBeenCalledWith(true, 'about:blank', 1);
  expect(canOpen).not.toHaveBeenCalled();
  expect(open).not.toHaveBeenCalled();
  await screen.unmount();
  jest.restoreAllMocks();
});
