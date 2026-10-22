export type ActivityPulse = { sessionKey: string; sequence: number; state: 'START' | 'HEARTBEAT' | 'STOP' };
const key = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const r = Math.floor(Math.random() * 16); return (c === 'x' ? r : (r & 3) | 8).toString(16); });
/** Foreground observation only. Offline time and background time are not reconstructed. */
export class ActivityTracker {
  private current: { sessionKey: string; sequence: number; started: boolean } | null = null;
  private queue: Promise<void> = Promise.resolve();
  constructor(private send: (pulse: ActivityPulse) => Promise<unknown>, private newKey = key) {}
  start(): void { if (this.current) return; this.current = { sessionKey: this.newKey(), sequence: 0, started: false }; this.enqueue(this.current, 'HEARTBEAT'); }
  heartbeat(): void { if (this.current) this.enqueue(this.current, 'HEARTBEAT'); }
  stop(): void { const value = this.current; this.current = null; if (value) this.enqueue(value, 'STOP'); }
  settled(): Promise<void> { return this.queue; }
  private enqueue(value: { sessionKey: string; sequence: number; started: boolean }, state: 'HEARTBEAT' | 'STOP'): void {
    this.queue = this.queue.then(async () => {
      if (!value.started) { await this.send({ sessionKey: value.sessionKey, sequence: ++value.sequence, state: 'START' }); value.started = true; if (state === 'HEARTBEAT') return; }
      await this.send({ sessionKey: value.sessionKey, sequence: ++value.sequence, state });
    }).catch(() => { /* Operational counters must not interrupt personal interaction. */ });
  }
}
