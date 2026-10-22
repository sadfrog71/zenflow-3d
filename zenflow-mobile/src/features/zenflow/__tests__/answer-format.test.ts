import { formatAnswerMarkdown } from '../answer-format';
import type { Citation } from '@/core/zenflow/core';
const source = {citationKey:'REFERENCE_123', title:'Book', visibility:'PUBLIC'} as Citation;
test('keeps markdown and numbers only available sources', () => {
  expect(formatAnswerMarkdown('# 标题\n\n**正文** [REFERENCE_123] [REFERENCE_999]', [source]))
    .toBe('# 标题\n\n**正文** [来源 1](#invelle-source-1) ');
  expect(formatAnswerMarkdown('[REFERENCE_123]', [source], true)).toBe('[Source 1](#invelle-source-1)');
});
test('hides unavailable and unfinished internal markers without eating other text', () => {
  expect(formatAnswerMarkdown('正文 [REFERENCE_123]')).toBe('正文 ');
  expect(formatAnswerMarkdown('正文 [REFERENCE_12')).toBe('正文 ');
  expect(formatAnswerMarkdown('[normal] `a_b`')).toBe('[normal] `a_b`');
});
