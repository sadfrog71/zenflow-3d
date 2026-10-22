import { browserSpeechError } from '../voice-errors';

test('browser service failures are not reported as silence or microphone denial', () => {
  expect(browserSpeechError('network', false)).toContain('无法连接');
  expect(browserSpeechError('no-speech', false)).toContain('没有检测到语音');
  expect(browserSpeechError('not-allowed', false)).toContain('权限');
  expect(browserSpeechError('audio-capture', false)).toContain('输入设备');
  expect(browserSpeechError('NotAllowedError', true)).toContain('blocked');
  expect(browserSpeechError('unknown', true)).toContain('failed');
});
