import React from 'react';
import { act, fireEvent, render } from '@testing-library/react-native';
import { AccessibilityInfo, AppState, type AppStateStatus } from 'react-native';
import { Pond } from '../pond';

const mockInject = jest.fn();
jest.mock('@/platform/zenflow/web-view', () => {
  const React = require('react');
  const { View } = require('react-native');
  return { WebView: React.forwardRef((props: any, ref: any) => {
    React.useImperativeHandle(ref, () => ({ injectJavaScript: mockInject }));
    return <View {...props} testID="pond-webview" />;
  }) };
});
beforeEach(() => { mockInject.mockClear(); jest.useFakeTimers(); jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(false); });
afterEach(() => { jest.useRealTimers(); jest.restoreAllMocks(); });

test('opens immersive, ready cancels deadline, exit restores navigation on unmount', async () => {
  const onExit = jest.fn(), onSeated = jest.fn();
  const screen = await render(<Pond english={false} onExit={onExit} onSeated={onSeated} />);
  expect(onSeated).toHaveBeenCalledWith(true);
  await fireEvent(screen.getByTestId('pond-webview'), 'message', { nativeEvent: { data: '{"type":"ready"}' } });
  await act(() => { jest.advanceTimersByTime(26000); });
  expect(screen.getByTestId('pond-webview')).toBeTruthy();
  await fireEvent(screen.getByTestId('pond-webview'), 'message', { nativeEvent: { data: '{"type":"exit"}' } });
  expect(onExit).toHaveBeenCalledTimes(1);
  await screen.unmount();
  expect(onSeated).toHaveBeenLastCalledWith(false);
  expect(mockInject).toHaveBeenLastCalledWith(expect.stringContaining('background'));
});

test('background stops scene, return synchronizes latest language without remount', async () => {
  let callback: ((state: AppStateStatus) => void) | undefined;
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_event, listener) => { callback = listener; return { remove: jest.fn() }; });
  const props = { onExit: jest.fn(), onSeated: jest.fn() };
  const screen = await render(<Pond english={false} {...props} />);
  await act(() => callback?.('background'));
  expect(mockInject).toHaveBeenLastCalledWith(expect.stringContaining('background'));
  await screen.rerender(<Pond english {...props} />);
  await act(() => callback?.('active'));
  expect(mockInject).toHaveBeenLastCalledWith(expect.stringContaining('"lang":"en"'));
  await screen.unmount();
});

test('failed scene and readiness timeout both leave a usable exit and retry', async () => {
  const onExit = jest.fn();
  const screen = await render(<Pond english={false} onExit={onExit} onSeated={jest.fn()} />);
  await fireEvent(screen.getByTestId('pond-webview'), 'message', { nativeEvent: { data: '{"type":"failed"}' } });
  expect(screen.getByText('水庭暂时无法打开，请重试。')).toBeTruthy();
  await fireEvent.press(screen.getByText('重新打开'));
  expect(screen.getByTestId('pond-webview')).toBeTruthy();
  await act(() => { jest.advanceTimersByTime(26000); });
  await fireEvent.press(screen.getByText('返回首页'));
  expect(onExit).toHaveBeenCalledTimes(1);
  await screen.unmount();
});

test.each(['contentProcessDidTerminate', 'renderProcessGone'])('%s stops the scene and offers retry', async event => {
  const screen = await render(<Pond english onExit={jest.fn()} onSeated={jest.fn()} />);
  await fireEvent(screen.getByTestId('pond-webview'), event, { nativeEvent: {} });
  expect(mockInject).toHaveBeenLastCalledWith(expect.stringContaining('background'));
  expect(screen.getByText('The pond could not open. Please try again.')).toBeTruthy();
  await fireEvent.press(screen.getByText('Try again'));
  expect(screen.getByTestId('pond-webview')).toBeTruthy();
  await screen.unmount();
});
