type SpeechEvent = { sessionId: string; state: string; text: string; code?: string };
type Recognition = { lang: string; continuous: boolean; interimResults: boolean; onresult: ((event: any) => void) | null; onerror: ((event: any) => void) | null; onend: (() => void) | null; onstart: (() => void) | null; start(): void; stop(): void; abort(): void };
const listeners = new Set<(event: SpeechEvent) => void>();
let active: Recognition | null = null;
const constructor = () => typeof window === 'undefined' ? undefined : (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
export const speech = {
  async start(locale: string, sessionId: string) {
    // Abort synchronously so start remains in the initiating interaction.
    const old = active; active = null; old?.abort();
    const Constructor = constructor();
    if (!Constructor) throw Object.assign(new Error('SPEECH_UNAVAILABLE'), { code: 'SPEECH_UNAVAILABLE' });
    const recognition: Recognition = new Constructor(); active = recognition;
    let text = '', failed = false;
    const emit = (state: string, code?: string) => { if (active === recognition) listeners.forEach(fn => fn({ sessionId, state, text, code })); };
    recognition.lang = locale; recognition.continuous = true; recognition.interimResults = true;
    recognition.onstart = () => { if (!failed) emit('listening'); };
    recognition.onresult = event => {
      if (failed) return;
      text = Array.from(event.results as ArrayLike<any>).map(result => result[0].transcript).join(''); emit('listening');
    };
    recognition.onerror = event => { failed = true; emit('error', event.error); };
    recognition.onend = () => { if (!failed) emit('complete'); if (active === recognition) active = null; };
    try { recognition.start(); } catch (error) { active = null; throw error; }
  },
  async stop() { active?.stop(); },
  async cancel() { const old = active; active = null; old?.abort(); },
  async takePendingVoiceShortcutRequest() { return null; },
  addListener(name: string, fn: (event: SpeechEvent) => void) {
    if (name === 'onSpeech') listeners.add(fn);
    return { remove() { listeners.delete(fn); } };
  },
};
