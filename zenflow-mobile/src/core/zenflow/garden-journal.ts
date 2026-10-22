import type { GardenPatch, GardenState } from '@/contracts/zenflow/garden';
import type { LocalStore } from '@/platform/zenflow/local-store';

export type GardenJournal = {formatVersion:1;snapshot:GardenState|null;pending:GardenPatch|null;pendingDisposition?:'LOCAL_ONLY'|'UNKNOWN'};
function decode(body:string):GardenJournal{
    const value=JSON.parse(body) as GardenJournal;
    if((value.pendingDisposition!==undefined&&!['LOCAL_ONLY','UNKNOWN'].includes(value.pendingDisposition))||value.formatVersion!==1||!(value.snapshot===null||value.snapshot&&typeof value.snapshot.gardenId==='string'&&Number.isSafeInteger(value.snapshot.stateVersion)&&value.snapshot.stateVersion>0&&Array.isArray(value.snapshot.fish)&&Array.isArray(value.snapshot.placements)&&value.snapshot.environment&&typeof value.snapshot.environment.presetId==='string'&&typeof value.snapshot.environment.lampEnabled==='boolean')
      ||!(value.pending===null||value.pending&&/^[A-Za-z0-9_-]{8,128}$/.test(value.pending.clientRequestId)&&Number.isSafeInteger(value.pending.expectedVersion)&&value.pending.expectedVersion>=0&&Array.isArray(value.pending.operations)&&value.pending.operations.length>0&&value.pending.operations.length<=32))throw Error('INVALID_GARDEN_JOURNAL');
    return value;
}
/** Restored pending content is data only: this module never sends network requests. */
export class GardenJournalStore {
  private revision:number|null=null;
  constructor(private store:LocalStore,private generation:number){}
  async read():Promise<GardenJournal|null>{
    this.revision=null;
    const row=await this.store.readGardenJournal(this.generation);
    if(!row){this.revision=0;return null;}
    const value=decode(row.body);
    this.revision=row.localRevision;
    return value;
  }
  async save(value:GardenJournal):Promise<void>{
    if(this.revision===null)throw Error('GARDEN_JOURNAL_NOT_READ');
    const body=JSON.stringify(value);decode(body);
    const row=await this.store.saveGardenJournal(body,this.revision,this.generation);
    this.revision=row.localRevision;
  }
}
