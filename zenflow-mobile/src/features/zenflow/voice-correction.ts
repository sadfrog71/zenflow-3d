import type {
  VoiceSentenceCorrectionRequest,
  VoiceSentenceCorrectionResponse,
} from '@/contracts/zenflow/voice';

export type { VoiceSentenceCorrectionRequest, VoiceSentenceCorrectionResponse } from '@/contracts/zenflow/voice';

export type VoiceSentenceCorrector = (
  request: VoiceSentenceCorrectionRequest,
  signal: AbortSignal,
) => Promise<VoiceSentenceCorrectionResponse>;

const MAX_SENTENCES = 32;

/** Split only at sentence-ending punctuation and preserve every character in the result. */
export function splitSpeechSentences(value: string): string[] {
  if (!value) return [];
  const sentences: string[] = [];
  let start = 0;
  for (let index = 0; index < value.length; index++) {
    const character = value[index];
    let boundary = '。！？!?；;\n\r'.includes(character);
    if (character === '.') {
      const previous = value[index - 1] ?? '';
      const next = value[index + 1] ?? '';
      boundary = (!previous.match(/[0-9]/) || !next.match(/[0-9]/)) && (!next || /\s/.test(next));
    }
    if (!boundary) continue;
    let end = index + 1;
    while (end < value.length && /[”’"')\]}】」』]/.test(value[end])) end++;
    sentences.push(value.slice(start, end));
    start = end;
    index = end - 1;
  }
  if (start < value.length) sentences.push(value.slice(start));
  return sentences.length ? sentences : [value];
}

function previousContext(sentences: string[], sentenceId: number): string[] {
  const selected = sentences.slice(Math.max(0, sentenceId - 3), sentenceId)
    .map(sentence => sentence.trim()).filter(Boolean);
  let remaining = 1000;
  const bounded: string[] = [];
  for (let index = selected.length - 1; index >= 0 && remaining > 0; index--) {
    const characters = Array.from(selected[index]);
    let start = characters.length;
    while (start > 0 && remaining >= characters[start - 1].length) {
      remaining -= characters[start - 1].length;
      start--;
    }
    const value = characters.slice(start).join('');
    bounded.unshift(value);
  }
  return bounded;
}

function mergeSentence(original: string, corrected: string): string {
  const leading = original.match(/^\s*/)?.[0] ?? '';
  const trailing = original.match(/\s*$/)?.[0] ?? '';
  return `${leading}${corrected.trim()}${trailing}`;
}

export async function correctSpeechTranscript(
  transcript: string,
  correct: VoiceSentenceCorrector | undefined,
  options: {
    sessionId: string;
    deadlineAt: number;
    signal: AbortSignal;
    onProgress?: (value: string) => void;
  },
): Promise<string> {
  if (!correct || options.signal.aborted) return transcript;
  const sentences = splitSpeechSentences(transcript);
  if (!sentences.length) return transcript;
  const output = [...sentences];
  const controller = new AbortController();
  const abort = () => controller.abort();
  options.signal.addEventListener('abort', abort, { once: true });
  if (options.signal.aborted) abort();

  let timedOut = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let cancelWork: (() => void) | undefined;
  const remaining = Math.max(0, options.deadlineAt - Date.now());
  if (remaining === 0) {
    options.signal.removeEventListener('abort', abort);
    return transcript;
  }

  const timeout = new Promise<'timeout'>(resolve => {
    timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
      resolve('timeout');
    }, remaining);
  });
  const cancelled = new Promise<'cancelled'>(resolve => {
    if (options.signal.aborted) resolve('cancelled');
    else {
      cancelWork = () => resolve('cancelled');
      options.signal.addEventListener('abort', cancelWork, { once: true });
    }
  });

  const work = (async () => {
    for (let index = 0; index < Math.min(sentences.length, MAX_SENTENCES); index++) {
      if (controller.signal.aborted) return 'finished' as const;
      const rawText = sentences[index].trim();
      if (!rawText || rawText.length > 2000) continue;
      const request: VoiceSentenceCorrectionRequest = {
        sessionId: options.sessionId,
        sentenceId: index,
        revision: 1,
        rawText,
        previousSentences: previousContext(sentences, index),
      };
      try {
        const response = await correct(request, controller.signal);
        if (controller.signal.aborted) return 'finished' as const;
        if (response.sessionId !== request.sessionId || response.sentenceId !== request.sentenceId
            || response.revision !== request.revision || !response.baseTextHash) continue;
        const candidate = response.status === 'applied' ? response.text?.trim() : undefined;
        if (!candidate) continue;
        output[index] = mergeSentence(sentences[index], candidate);
        options.onProgress?.(output.join(''));
      } catch {
        if (controller.signal.aborted) return 'finished' as const;
      }
    }
    return 'finished' as const;
  })();

  try {
    const outcome = await Promise.race([work, timeout, cancelled]);
    if (outcome === 'cancelled') throw new Error('VOICE_CORRECTION_CANCELLED');
    if (outcome === 'timeout') timedOut = true;
    return output.join('');
  } finally {
    if (timer) clearTimeout(timer);
    if (timedOut || options.signal.aborted) controller.abort();
    options.signal.removeEventListener('abort', abort);
    if (cancelWork) options.signal.removeEventListener('abort', cancelWork);
  }
}
