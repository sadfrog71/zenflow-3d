jest.mock('react-native-webview', () => ({ WebView: require('react-native').View }));
import { act, fireEvent, render } from '@testing-library/react-native';
import { Alert, AppState, type AppStateStatus } from 'react-native';
import { GeneratedDraft } from '../private-tools';
import type { CoreApi } from '@/core/zenflow/core';

describe('private generated writing', () => {
  afterEach(() => jest.restoreAllMocks());
  const fixture = (status: string) => ({
    createConversation: jest.fn().mockResolvedValue({ id: 'conversation' }),
    send: jest.fn().mockResolvedValue({ runId: 'run', responseMessageId: 'response' }),
    run: jest.fn().mockResolvedValue({ runId: 'run', status, draftContent: 'Generated writing', lastEventSeq: 3 }),
    events: jest.fn(), cancel: jest.fn(),
  });
  test('sends only after confirmation and adopts only after a completed result and separate choice', async () => {
    const api = fixture('COMPLETED'); const onUse = jest.fn(); const dialogs: (() => void)[] = [];
    jest.spyOn(Alert, 'alert').mockImplementation((_title, _message, buttons) => { dialogs.push(buttons![1].onPress as () => void); });
    const screen = await render(<GeneratedDraft api={api as unknown as CoreApi} en draft="My original writing" onUse={onUse} />);
    await fireEvent.changeText(screen.getByLabelText('Writing request'), 'A short reflection');
    await fireEvent.press(screen.getByText('Generate draft'));
    expect(api.send).not.toHaveBeenCalled(); expect(onUse).not.toHaveBeenCalled();
    await act(async () => { dialogs[0](); });
    expect(api.send.mock.calls[0][2]).toContain('My original writing');
    expect(screen.getByText('Generated writing')).toBeTruthy(); expect(onUse).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByText('Use this draft')); expect(onUse).not.toHaveBeenCalled();
    await act(async () => { dialogs[1](); });
    expect(onUse).toHaveBeenCalledWith('Generated writing', 'response');
  });
  test('foreground recovery reads the same run without sending a second generation request', async () => {
    const api = fixture('COMPLETED'); const onUse = jest.fn(); let confirm: () => void = () => {}; let lifecycle: (state: AppStateStatus) => void = () => {};
    api.run.mockResolvedValueOnce({ runId: 'run', status: 'RUNNING', draftContent: 'Partial', lastEventSeq: 2 });
    api.events.mockImplementation((_id, _seq, signal: AbortSignal) => new Promise((_resolve, reject) => { signal.addEventListener('abort', () => reject(new Error('Aborted')), { once: true }); }));
    jest.spyOn(AppState, 'addEventListener').mockImplementation((_type, listener) => { lifecycle = listener; return { remove: jest.fn() }; });
    jest.spyOn(Alert, 'alert').mockImplementation((_title, _message, buttons) => { confirm = buttons![1].onPress as () => void; });
    const screen = await render(<GeneratedDraft api={api as unknown as CoreApi} en draft="Original" onUse={onUse} />);
    await fireEvent.changeText(screen.getByLabelText('Writing request'), 'A reflection'); await fireEvent.press(screen.getByText('Generate draft'));
    await act(async () => { confirm(); });
    expect(api.events).toHaveBeenCalledTimes(1);
    await act(async () => { lifecycle('background'); });
    await act(async () => { lifecycle('active'); });
    expect(api.send).toHaveBeenCalledTimes(1); expect(api.run).toHaveBeenCalledTimes(2);
    expect(screen.getByText('Use this draft')).toBeTruthy(); expect(onUse).not.toHaveBeenCalled();
  });
  test('a failed or partial answer never becomes an adoptable complete draft', async () => {
    const api = fixture('FAILED'); const onUse = jest.fn(); let confirm: () => void = () => {};
    jest.spyOn(Alert, 'alert').mockImplementation((_title, _message, buttons) => { confirm = buttons![1].onPress as () => void; });
    const screen = await render(<GeneratedDraft api={api as unknown as CoreApi} en draft="Original" onUse={onUse} />);
    await fireEvent.changeText(screen.getByLabelText('Writing request'), 'A reflection'); await fireEvent.press(screen.getByText('Generate draft'));
    await act(async () => { confirm(); });
    expect(screen.queryByText('Use this draft')).toBeNull(); expect(onUse).not.toHaveBeenCalled();
    expect(screen.getByText('This draft did not finish. Your writing is unchanged.')).toBeTruthy();
  });
});
