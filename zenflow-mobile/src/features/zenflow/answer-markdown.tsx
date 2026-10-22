import { Linking } from 'react-native';
import { EnrichedMarkdownText, type MarkdownStyle } from 'react-native-enriched-markdown';
import type { Citation } from '@/core/zenflow/core';
import { formatAnswerMarkdown } from './answer-format';

const typography: MarkdownStyle = {
  paragraph: { fontSize: 16, lineHeight: 28, color: '#355a45', marginTop: 0, marginBottom: 14 },
  h1: { fontSize: 24, lineHeight: 34, color: '#355a45', fontWeight: '600', marginTop: 8, marginBottom: 16 },
  h2: { fontSize: 20, lineHeight: 30, color: '#355a45', fontWeight: '600', marginTop: 22, marginBottom: 10 },
  h3: { fontSize: 18, lineHeight: 28, color: '#355a45', fontWeight: '600', marginTop: 18, marginBottom: 8 },
  h4: { fontSize: 17, color: '#355a45' }, h5: { fontSize: 16, color: '#355a45' }, h6: { fontSize: 16, color: '#355a45' },
  list: { fontSize: 16, lineHeight: 28, color: '#355a45', itemSpacing: 6, marginBottom: 14, gapWidth: 8 },
  blockquote: { fontSize: 16, lineHeight: 28, color: '#526653', borderColor: '#a7b59c', borderWidth: 2, padding: 12, marginBottom: 14 },
  code: { backgroundColor: '#e9eedf', color: '#355a45' },
  codeBlock: { fontSize: 14, color: '#355a45', backgroundColor: '#e9eedf', padding: 14, borderRadius: 8 },
  link: { color: '#637e56', underline: true },
};

export function AnswerMarkdown({ content, sources = [], en = false, onSource }: {
  content: string; sources?: Citation[]; en?: boolean; onSource?: (source: Citation) => void;
}) {
  return <EnrichedMarkdownText markdown={formatAnswerMarkdown(content, sources, en)} markdownStyle={typography}
    md4cFlags={{ latexMath: false }} selectable enableTaskListItemToggle={false}
    onLinkPress={({ url }) => {
      const match = /^#invelle-source-(\d+)$/.exec(url);
      if (match) { const source = sources[Number(match[1]) - 1]; if (source) onSource?.(source); return; }
      if (/^https?:\/\//i.test(url)) void Linking.openURL(url).catch(() => {});
    }} />;
}
