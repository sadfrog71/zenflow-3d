import { speech } from '../speech.web';
let recognition: any;
class FakeRecognition {
  onstart?: () => void; onresult?: (event: any) => void; onerror?: (event: any) => void; onend?: () => void;
  start = jest.fn(); stop = jest.fn(); abort = jest.fn(() => this.onend?.());
  constructor() { recognition = this; }
}
beforeEach(() => { (globalThis as any).window = { SpeechRecognition: FakeRecognition }; });
afterEach(async () => { await speech.cancel(); });
test('service error is terminal and cannot become a completed or late transcript', async () => {
  const events: any[] = [], subscription = speech.addListener('onSpeech', event => events.push(event));
  await speech.start('zh-CN', 'one');
  recognition.onerror({ error: 'network' });
  recognition.onresult({ results: [[{ transcript: 'late' }]] });
  recognition.onend();
  expect(events).toEqual([{ sessionId: 'one', state: 'error', text: '', code: 'network' }]);
  subscription.remove();
});
test('cancelling cannot send text, and old callbacks cannot affect a new session', async () => {
  const events: any[] = [], subscription = speech.addListener('onSpeech', event => events.push(event));
  await speech.start('zh-CN', 'one'); const old = recognition;
  await speech.cancel(); await speech.start('zh-CN', 'two');
  old.onend();
  recognition.onresult({ results: [[{ transcript: '当前文字' }]] }); recognition.onend();
  expect(events.map(event => [event.sessionId, event.state])).toEqual([['two', 'listening'], ['two', 'complete']]);
  subscription.remove();
});
