// Publication data check: missing notes must be repaired in the manuscript.
// This does not create substitute meanings or certify semantic correctness.
import { readFileSync } from 'node:fs';
const files = process.argv.slice(2);
if (!files.length) throw new Error('Provide chapter JSON files or a content.json array.');
const significant = text => [...text].filter(character => /[\p{L}\p{N}]/u.test(character)).length;
const reports = [];
for (const file of files) {
  const input = JSON.parse(readFileSync(file, 'utf8'));
  for (const chapter of Array.isArray(input) ? input : [input]) {
    const editions = [
      ['zh-CN', chapter.original, chapter.annotationsZh || chapter.classic?.passages?.flatMap(passage => passage.tokens.filter(token => !token.punctuation).map(token => ({ phrase: token.text, note: token.note, tone: token.tone })))],
      ['en-US', chapter.translationEn, chapter.annotationsEn || chapter.english?.annotations],
    ];
    for (const [language, text, annotations] of editions) {
      if (typeof text !== 'string' || !text.length || !Array.isArray(annotations)) {
        reports.push({ file, chapterNo: chapter.chapterNo, language, passed: false, error: 'MISSING_BODY_OR_ANNOTATIONS' });
        continue;
      }
      let cursor = 0, covered = 0;
      const gaps = [], invalid = [];
      for (const [index, annotation] of annotations.entries()) {
        if (!annotation.phrase?.trim() || !annotation.note?.trim() || !['word', 'clause', 'variant'].includes(annotation.tone || 'word')) {
          invalid.push({ index, error: 'INVALID_NOTE' });
          continue;
        }
        const start = text.indexOf(annotation.phrase, cursor);
        if (start < 0) {
          invalid.push({ index, phrase: annotation.phrase, error: 'ANCHOR_NOT_FOUND_IN_READING_ORDER' });
          continue;
        }
        const gap = text.slice(cursor, start);
        if (significant(gap)) gaps.push({ start: cursor, end: start, text: gap });
        covered += significant(annotation.phrase);
        cursor = start + annotation.phrase.length;
      }
      if (significant(text.slice(cursor))) gaps.push({ start: cursor, end: text.length, text: text.slice(cursor) });
      const total = significant(text);
      reports.push({ file, book: chapter.book, chapterNo: chapter.chapterNo, language, annotations: annotations.length, covered, total, coverage: total ? covered / total : 0, passed: !invalid.length && !gaps.length && covered === total,
        gaps: gaps.slice(0, 5), gapCount: gaps.length, invalid: invalid.slice(0, 5), invalidCount: invalid.length });
    }
  }
}
console.log(JSON.stringify({ passed: reports.every(report => report.passed), reports }, null, 2));
process.exitCode = reports.every(report => report.passed) ? 0 : 1;
