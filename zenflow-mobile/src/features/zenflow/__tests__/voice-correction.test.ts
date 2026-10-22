import {
  correctSpeechTranscript,
  splitSpeechSentences,
  type VoiceSentenceCorrectionRequest,
  type VoiceSentenceCorrectionResponse,
} from '../voice-correction';

const response = (request: VoiceSentenceCorrectionRequest, text?: string): VoiceSentenceCorrectionResponse => ({
  sessionId: request.sessionId,
  sentenceId: request.sentenceId,
  revision: request.revision,
  baseTextHash: `hash-${request.sentenceId}`,
  status: text ? 'applied' : 'skipped',
  ...(text ? { text } : {}),
});

test('splits Chinese and English sentences while preserving punctuation and spacing', () => {
  expect(splitSpeechSentences('请看看水泵。 It is quiet. 还有一点。')).toEqual([
    '请看看水泵。', ' It is quiet.', ' 还有一点。',
  ]);
  expect(splitSpeechSentences('数值是 3.5 米。')).toEqual(['数值是 3.5 米。']);
});

test('corrects each sentence in order and sends only the previous three as context', async () => {
  const seen: VoiceSentenceCorrectionRequest[] = [];
  const controller = new AbortController();
  const result = await correctSpeechTranscript('第一句。 第二句。 第三句。 第四句。', async request => {
    seen.push(request);
    return response(request, `改正${request.sentenceId}。`);
  }, { sessionId: 'voice-session-1', deadlineAt: Date.now() + 1000, signal: controller.signal });

  expect(result).toBe('改正0。 改正1。 改正2。 改正3。');
  expect(seen[3].previousSentences).toEqual(['第一句。', '第二句。', '第三句。']);
});

test('keeps original text for stale responses and times out without applying late corrections', async () => {
  const controller = new AbortController();
  const stale = await correctSpeechTranscript('原句。', async request => ({
    ...response(request, '不应采用。'), sentenceId: 3,
  }), { sessionId: 'voice-session-2', deadlineAt: Date.now() + 1000, signal: controller.signal });
  expect(stale).toBe('原句。');

  jest.useFakeTimers();
  try {
    let resolveDeferred!: (value: VoiceSentenceCorrectionResponse) => void;
    const deferred = new Promise<VoiceSentenceCorrectionResponse>(resolve => { resolveDeferred = resolve; });
    const progress = jest.fn();
    const pending = correctSpeechTranscript('保留原句。', async () => deferred, {
      sessionId: 'voice-session-3', deadlineAt: Date.now() + 5000, signal: new AbortController().signal, onProgress: progress,
    });
    await jest.advanceTimersByTimeAsync(5000);
    await expect(pending).resolves.toBe('保留原句。');
    resolveDeferred({ sessionId: 'voice-session-3', sentenceId: 0, revision: 1, baseTextHash: 'late', status: 'applied', text: '迟到结果。' });
    await Promise.resolve();
    expect(progress).not.toHaveBeenCalled();
  } finally {
    jest.useRealTimers();
  }
});
