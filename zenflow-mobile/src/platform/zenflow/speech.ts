import { requireOptionalNativeModule } from 'expo-modules-core';
type SpeechEvent = { sessionId: string; state: string; text: string; code?: string };
type SpeechSubscription = { remove: () => void };
interface SpeechModule {
  start: (locale: string, id: string) => Promise<void>;
  stop: () => Promise<void>;
  cancel: () => Promise<void>;
  takePendingVoiceShortcutRequest?: () => Promise<string | null>;
  addListener(name: 'onSpeech', fn: (e: SpeechEvent) => void): SpeechSubscription;
  addListener(name: 'onVoiceShortcut', fn: () => void): SpeechSubscription;
}
export const speech = requireOptionalNativeModule<SpeechModule>('InvelleSpeech');
