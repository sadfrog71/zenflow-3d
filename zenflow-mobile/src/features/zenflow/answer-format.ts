import type { Citation } from '@/core/zenflow/core';

/** Display-only formatting; stored answers and source authorization stay unchanged. */
export function formatAnswerMarkdown(content: string, sources: Citation[] = [], en = false): string {
  return content.replace(/\[REFERENCE_[A-Za-z0-9_-]+\]/g, marker => {
    const index = sources.findIndex(source => source.citationKey === marker.slice(1, -1));
    return index < 0 ? '' : `[${en ? 'Source' : '来源'} ${index + 1}](#invelle-source-${index + 1})`;
  }).replace(/\[REFERENCE_[A-Za-z0-9_-]*$/, '');
}
