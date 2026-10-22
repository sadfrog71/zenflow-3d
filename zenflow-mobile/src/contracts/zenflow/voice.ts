export type VoiceSentenceCorrectionRequest = {
  sessionId: string;
  sentenceId: number;
  revision: number;
  rawText: string;
  previousSentences: string[];
};

export type VoiceSentenceCorrectionResponse = {
  sessionId: string;
  sentenceId: number;
  revision: number;
  baseTextHash: string;
  status: 'applied' | 'unchanged' | 'skipped';
  text?: string;
};
