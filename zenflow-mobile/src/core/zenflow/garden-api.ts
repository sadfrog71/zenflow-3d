import type { GardenPatch, GardenState, OperationReceipt } from '@/contracts/zenflow/garden';
import type { ZenflowSession } from './session';

/** A save attempt is bound to one verified session generation, including explicit retries. */
export class GardenSaveAttempt {
  private readonly patch: GardenPatch;
  private readonly generation: number;
  private readonly owner: string;
  private running = false;
  private status: 'READY'|'SAVING'|'CONFIRMED'|'REJECTED'|'UNKNOWN'|'LOCKED' = 'READY';
  private result: OperationReceipt | null = null;
  get state() { return this.status; }
  get receipt(): OperationReceipt | null { return this.result ? {...this.result} : null; }
  constructor(private session: ZenflowSession, patch: GardenPatch) {
    const identity = session.snapshot();
    if (!identity.profile || identity.busy) throw Error('AUTH_REQUIRED');
    this.generation = identity.generation;
    this.owner = JSON.stringify([identity.profile.tenantId, identity.profile.subjectType, identity.profile.userId]);
    // Freeze the dispatched content against later editor mutation; never silently rebase a failed operation.
    this.patch = JSON.parse(JSON.stringify(patch));
    if (!/^[A-Za-z0-9_-]{8,128}$/.test(this.patch.clientRequestId)
      || !Number.isSafeInteger(this.patch.expectedVersion) || this.patch.expectedVersion < 0
      || !Array.isArray(this.patch.operations) || !this.patch.operations.length || this.patch.operations.length > 32) throw Error('INVALID_GARDEN_PATCH');
  }
  private current(): void {
    const identity = this.session.snapshot();
    if (!identity.profile || identity.busy || identity.generation !== this.generation
      || JSON.stringify([identity.profile.tenantId,identity.profile.subjectType,identity.profile.userId]) !== this.owner) {
      this.status = 'LOCKED'; throw Error('STALE_IDENTITY');
    }
  }
  private accept(value: OperationReceipt): void {
    this.current();
    if (!value || value.clientRequestId !== this.patch.clientRequestId || value.operationType !== 'GARDEN_PATCH'
      || !['PROCESSING','SUCCEEDED','REJECTED'].includes(value.state)
      || value.state === 'SUCCEEDED' && (value.resultType !== 'GARDEN' || typeof value.resultId !== 'string'
        || !value.resultId || !Number.isSafeInteger(value.resultVersion) || value.resultVersion! < 1)) throw Error('INVALID_GARDEN_RECEIPT');
    this.result = {...value};
    this.status = value.state === 'SUCCEEDED' ? 'CONFIRMED' : value.state === 'REJECTED' ? 'REJECTED' : 'UNKNOWN';
  }
  /** Call once after user confirmation; call again only as an explicit same-content retry. */
  async submit(): Promise<void> {
    this.current();
    if (this.running || this.status === 'CONFIRMED' || this.status === 'REJECTED') return;
    this.running = true; this.status = 'SAVING';
    try {
      const receipt = await this.session.request<OperationReceipt>('garden/state','PATCH',JSON.parse(JSON.stringify(this.patch)));
      this.accept(receipt);
    } catch {
      this.current(); this.status = 'UNKNOWN';
      await this.queryReceipt();
    } finally { this.running = false; }
  }
  /** A missing receipt is inconclusive and must never trigger an automatic second write. */
  async reconcile(): Promise<void> {
    this.current();
    if (this.running || this.status === 'CONFIRMED' || this.status === 'REJECTED') return;
    this.running = true;
    try { await this.queryReceipt(); } finally { this.running = false; }
  }
  private async queryReceipt(): Promise<void> {
    try {
      const value = await this.session.request<OperationReceipt>(`operations/${encodeURIComponent(this.patch.clientRequestId)}?operationType=GARDEN_PATCH`);
      this.accept(value);
    } catch { this.current(); this.status = 'UNKNOWN'; }
  }
}

export async function readGarden(session: ZenflowSession): Promise<GardenState> {
  return session.request<GardenState>('garden/state');
}
