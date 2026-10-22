import type { LocalOwner } from '@/core/zenflow/local-owner';
export type LocalMedia = {id:string;purpose:'PRIVATE_NOTE'|'WISH'|'LETTER';bytes:number;durationSeconds:number;persistedAt:number};
export type LocalDraft = { id:string;kind:'NOTE'|'WISH'|'LETTER'|'DRAWING'|'CHAT';body:string;localRevision:number;persistedAt:number };
export type LocalGardenJournal = {body:string;localRevision:number;persistedAt:number};
export type LocalDraftSummary = Omit<LocalDraft, 'body'>;
export type LocalDraftPage = {items:LocalDraftSummary[];nextCursor:string|null};
/** Browser private persistence is deliberately unavailable; native encryption is never emulated with localStorage. */
export class LocalStore {
  static async openOwner(_owner:LocalOwner,_generation:number):Promise<LocalStore>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async saveDraft(_id:string,_kind:LocalDraft['kind'],_body:string,_generation:number):Promise<LocalDraft>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async readDraft(_id:string,_generation:number):Promise<LocalDraft|null>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async discardDraftIfBodyMatches(_id:string,_body:string,_generation:number):Promise<void>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async listDrafts(_namespace:'notes'|'creation',_generation:number,_cursor=''):Promise<LocalDraftPage>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async readGardenJournal(_generation:number):Promise<LocalGardenJournal|null>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async saveGardenJournal(_body:string,_expectedRevision:number,_generation:number):Promise<LocalGardenJournal>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async saveMedia(_bytes:Uint8Array,_purpose:LocalMedia['purpose'],_durationSeconds:number,_generation:number):Promise<LocalMedia>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async listMedia(_generation:number):Promise<LocalMedia[]>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async readMedia(_id:string,_generation:number):Promise<{metadata:LocalMedia;bytes:Uint8Array}|null>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async prepareMediaPlayback(_id:string,_generation:number):Promise<string>{throw Error('NATIVE_PRIVATE_STORE_REQUIRED');}
  async releaseMediaPlayback(_uri:string):Promise<void>{}
  async closeOwner():Promise<void>{}
}
