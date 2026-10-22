import * as SQLite from 'expo-sqlite';
import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';
import { File } from 'expo-file-system';
import { requireNativeModule } from 'expo-modules-core';
import { PrivateMediaFiles } from './private-media-files';
import { PRIVATE_AUDIO_LIMITS, type PrivateMediaIdentity } from './private-media-codec';
import { serializeOwner, type LocalOwner } from '@/core/zenflow/local-owner';

export type LocalMedia = {id:string;purpose:PrivateMediaIdentity['purpose'];bytes:number;durationSeconds:number;persistedAt:number};
type DraftKind = 'NOTE' | 'WISH' | 'LETTER' | 'DRAWING' | 'CHAT';
export type LocalDraft = { id: string; kind: DraftKind; body: string; localRevision: number; persistedAt: number };
export type LocalGardenJournal = {body:string;localRevision:number;persistedAt:number};
export type LocalDraftSummary = Omit<LocalDraft, 'body'>;
export type LocalDraftPage = { items: LocalDraftSummary[]; nextCursor: string | null };
const protection = () => requireNativeModule('InvelleGarden') as {
  privateStoreDirectory(digest: string): Promise<string>;
};

/** One owner and one dispatch generation per instance. No database handle leaves this adapter. */
export class LocalStore {
  private static switching: Promise<unknown> = Promise.resolve();
  private static active: LocalStore | null = null;
  private db: SQLite.SQLiteDatabase | null = null;
  private queue: Promise<unknown> = Promise.resolve();
  private locked = false;
  private media: PrivateMediaFiles | null = null;
  private directory = '';
  private playbackFiles = new Set<File>();
  private constructor(private owner: LocalOwner, readonly generation: number) {}
  private serial<T>(work: () => Promise<T>): Promise<T> {
    const next = this.queue.catch(() => {}).then(work); this.queue = next; return next;
  }
  static openOwner(owner: LocalOwner, generation: number): Promise<LocalStore> {
    const next = this.switching.catch(() => {}).then(async () => {
      await this.active?.closeOwner(); this.active = null;
      const store = await this.openEncrypted(owner, generation); this.active = store; return store;
    });
    this.switching = next; return next;
  }
  private static async openEncrypted(owner: LocalOwner, generation: number): Promise<LocalStore> {
    const store = new LocalStore(owner, generation);
    const ownerKey = serializeOwner(owner);
    const digest = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, ownerKey);
    const directory = await protection().privateStoreDirectory(digest);
    const existing = new File(directory, 'state.db').exists;
    const keyName = `invelle.owner.${digest}.db.v1`;
    let key = await SecureStore.getItemAsync(keyName);
    if (existing && !key) throw Error('LOCAL_KEY_MISSING');
    if (!existing && key) throw Error('LOCAL_DATABASE_MISSING');
    if (!key) {
      key = Array.from(await Crypto.getRandomBytesAsync(32), b => b.toString(16).padStart(2, '0')).join('');
      await SecureStore.setItemAsync(keyName, key, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY });
    }
    if (!/^[a-f0-9]{64}$/.test(key)) throw Error('LOCAL_KEY_INVALID');
    const db = await SQLite.openDatabaseAsync('state.db', { useNewConnection: true }, directory);
    try {
      // SQLCipher PRAGMA cannot bind parameters; only the validated random hex key is interpolated.
      await db.execAsync(`PRAGMA key = "x'${key}'";`);
      key = null;
      const cipher = await db.getFirstAsync<Record<string,string>>('PRAGMA cipher_version');
      if (!cipher || !Object.values(cipher).some(v => typeof v === 'string' && v.length > 0)) throw Error('SQLCIPHER_REQUIRED');
      await db.getFirstAsync('SELECT count(*) FROM sqlite_master');
      // Serialized IMMEDIATE transactions use this keyed connection exclusively.
      await db.execAsync('PRAGMA journal_mode = DELETE; PRAGMA synchronous = FULL; PRAGMA secure_delete = ON; BEGIN IMMEDIATE;');
      try {
        await db.execAsync(`CREATE TABLE IF NOT EXISTS local_meta (id INTEGER PRIMARY KEY CHECK(id=1), owner_key TEXT NOT NULL, schema_version INTEGER NOT NULL);
          CREATE TABLE IF NOT EXISTS draft (id TEXT PRIMARY KEY, kind TEXT NOT NULL, body TEXT NOT NULL, local_revision INTEGER NOT NULL, persisted_at INTEGER NOT NULL);`);
        const meta = await db.getFirstAsync<{owner_key:string;schema_version:number}>('SELECT owner_key,schema_version FROM local_meta WHERE id=1');
        if (meta && (meta.owner_key !== ownerKey || ![1,2,3].includes(meta.schema_version))) throw Error('LOCAL_OWNER_OR_SCHEMA_MISMATCH');
        await db.execAsync('CREATE TABLE IF NOT EXISTS garden_journal (id INTEGER PRIMARY KEY CHECK(id=1), body TEXT NOT NULL, local_revision INTEGER NOT NULL, persisted_at INTEGER NOT NULL)');
        await db.execAsync("CREATE TABLE IF NOT EXISTS local_media (id TEXT PRIMARY KEY, purpose TEXT NOT NULL, bytes INTEGER NOT NULL, duration_seconds REAL NOT NULL, persisted_at INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN ('STAGED','READY')))");
        if (!meta) await db.runAsync('INSERT INTO local_meta VALUES (1,?,3)', ownerKey);
        else if(meta.schema_version<3)await db.runAsync('UPDATE local_meta SET schema_version=3 WHERE id=1');
        await db.execAsync('COMMIT');
      } catch (error) { await db.execAsync('ROLLBACK'); throw error; }
      store.db = db; store.directory = directory;
      store.media = new PrivateMediaFiles(directory, digest, () => { store.current(generation); });
      // Failed pre-rename attempts have no recoverable ciphertext. Keep post-rename rows for authenticated recovery.
      const staged = await db.getAllAsync<{id:string}>("SELECT id FROM local_media WHERE state='STAGED'");
      for (const row of staged ?? []) {
        store.media.discardStaging(row.id);
        if (!store.media.exists(row.id)) await db.runAsync("DELETE FROM local_media WHERE id=? AND state='STAGED'", row.id);
      }
      return store;
    } catch (error) { await db.closeAsync(); throw error; }
  }
  private current(generation: number): SQLite.SQLiteDatabase {
    if (this.locked || !this.db || generation !== this.generation) throw Error('LOCAL_STORE_LOCKED');
    return this.db;
  }
  saveDraft(id: string, kind: DraftKind, body: string, generation: number): Promise<LocalDraft> {
    return this.serial(async () => {
      const db = this.current(generation);
      if (!id || id.length > 200 || !['NOTE','WISH','LETTER','DRAWING','CHAT'].includes(kind) || body.length > 1000000) throw Error('INVALID_LOCAL_DRAFT');
      await db.execAsync('BEGIN IMMEDIATE');
      try {
        const previous = await db.getFirstAsync<{local_revision:number}>('SELECT local_revision FROM draft WHERE id=?', id);
        const draft = { id, kind, body, localRevision: (previous?.local_revision ?? 0) + 1, persistedAt: Date.now() };
        this.current(generation);
        await db.runAsync('INSERT INTO draft VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET kind=excluded.kind,body=excluded.body,local_revision=excluded.local_revision,persisted_at=excluded.persisted_at', id,kind,body,draft.localRevision,draft.persistedAt);
        this.current(generation);
        await db.execAsync('COMMIT'); return draft;
      } catch (error) { await db.execAsync('ROLLBACK'); throw error; }
    });
  }
  readDraft(id: string, generation: number): Promise<LocalDraft | null> {
    return this.serial(async () => {
      const row = await this.current(generation).getFirstAsync<{id:string;kind:DraftKind;body:string;local_revision:number;persisted_at:number}>('SELECT * FROM draft WHERE id=?', id);
      this.current(generation);
      return row ? { id:row.id,kind:row.kind,body:row.body,localRevision:row.local_revision,persistedAt:row.persisted_at } : null;
    });
  }
  /** Bounded metadata only; private text is loaded only when a draft is selected. */
  listDrafts(namespace: 'notes' | 'creation', generation: number, cursor = ''): Promise<LocalDraftPage> {
    return this.serial(async () => {
      const db = this.current(generation);
      if (!['notes', 'creation'].includes(namespace) || cursor.length > 200 || (cursor && !cursor.startsWith(`${namespace}:`))) throw Error('INVALID_DRAFT_CURSOR');
      const rows = await db.getAllAsync<{id:string;kind:DraftKind;local_revision:number;persisted_at:number}>(
        'SELECT id,kind,local_revision,persisted_at FROM draft WHERE id GLOB ? AND id>? ORDER BY id LIMIT 51', `${namespace}:*`, cursor);
      this.current(generation);
      const items = rows.slice(0, 50).map(row => ({id:row.id,kind:row.kind,localRevision:row.local_revision,persistedAt:row.persisted_at}));
      return { items, nextCursor: rows.length > 50 ? items[49].id : null };
    });
  }
  discardDraftIfBodyMatches(id:string,body:string,generation:number):Promise<void>{
    return this.serial(async()=>{await this.current(generation).runAsync('DELETE FROM draft WHERE id=? AND body=?',id,body);this.current(generation);});
  }
  readGardenJournal(generation:number):Promise<LocalGardenJournal|null>{
    return this.serial(async()=>{
      const row=await this.current(generation).getFirstAsync<{body:string;local_revision:number;persisted_at:number}>('SELECT body,local_revision,persisted_at FROM garden_journal WHERE id=1');
      this.current(generation);
      return row?{body:row.body,localRevision:row.local_revision,persistedAt:row.persisted_at}:null;
    });
  }
  /** Snapshot and pending operation are one encrypted atomic record, with local optimistic concurrency. */
  saveGardenJournal(body:string,expectedRevision:number,generation:number):Promise<LocalGardenJournal>{
    return this.serial(async()=>{
      const db=this.current(generation);
      if(typeof body!=='string'||body.length>1000000||!Number.isSafeInteger(expectedRevision)||expectedRevision<0)throw Error('INVALID_GARDEN_JOURNAL');
      JSON.parse(body);
      await db.execAsync('BEGIN IMMEDIATE');
      try{
        const row=await db.getFirstAsync<{local_revision:number}>('SELECT local_revision FROM garden_journal WHERE id=1');
        if((row?.local_revision??0)!==expectedRevision)throw Error('LOCAL_GARDEN_CONFLICT');
        const next={body,localRevision:expectedRevision+1,persistedAt:Date.now()};
        if(!Number.isSafeInteger(next.localRevision))throw Error('LOCAL_GARDEN_REVISION_LIMIT');
        this.current(generation);
        await db.runAsync('INSERT INTO garden_journal VALUES (1,?,?,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body,local_revision=excluded.local_revision,persisted_at=excluded.persisted_at',body,next.localRevision,next.persistedAt);
        this.current(generation);await db.execAsync('COMMIT');return next;
      }catch(error){await db.execAsync('ROLLBACK');throw error;}
    });
  }
  /** STAGED is committed before file I/O; a crash after rename can be authenticated and recovered. */
  saveMedia(bytes:Uint8Array,purpose:LocalMedia['purpose'],durationSeconds:number,generation:number):Promise<LocalMedia>{
    return this.serial(async()=>{
      const db=this.current(generation);
      if(!this.media||!['PRIVATE_NOTE','WISH','LETTER'].includes(purpose)||!bytes.length||bytes.length>PRIVATE_AUDIO_LIMITS.maxBytes
        ||!Number.isFinite(durationSeconds)||durationSeconds<=0||durationSeconds>PRIVATE_AUDIO_LIMITS.maxDurationSeconds)throw Error('INVALID_LOCAL_MEDIA');
      const previous=await db.getFirstAsync<{count:number}>('SELECT count(*) AS count FROM local_media');
      const item={id:Crypto.randomUUID(),purpose,bytes:bytes.length,durationSeconds,persistedAt:Date.now()};
      this.current(generation);
      await db.runAsync("INSERT INTO local_media VALUES (?,?,?,?,?,'STAGED')",item.id,purpose,item.bytes,durationSeconds,item.persistedAt);
      await this.media.save({fileId:item.id,purpose},bytes,(previous?.count??0)===0);
      this.current(generation);
      await db.runAsync("UPDATE local_media SET state='READY' WHERE id=?",item.id);
      this.current(generation);return item;
    });
  }
  listMedia(generation:number):Promise<LocalMedia[]>{
    return this.serial(async()=>{
      const rows=await this.current(generation).getAllAsync<{id:string;purpose:LocalMedia['purpose'];bytes:number;duration_seconds:number;persisted_at:number}>('SELECT id,purpose,bytes,duration_seconds,persisted_at FROM local_media ORDER BY persisted_at DESC,id LIMIT 50');
      this.current(generation);
      return rows.map(row=>({id:row.id,purpose:row.purpose,bytes:row.bytes,durationSeconds:row.duration_seconds,persistedAt:row.persisted_at}));
    });
  }
  /** READY is acknowledged only after authenticated disk read, including interrupted STAGED saves. */
  readMedia(id:string,generation:number):Promise<{metadata:LocalMedia;bytes:Uint8Array}|null>{
    return this.serial(async()=>{
      const db=this.current(generation);
      const row=await db.getFirstAsync<{id:string;purpose:LocalMedia['purpose'];bytes:number;duration_seconds:number;persisted_at:number;state:string}>('SELECT * FROM local_media WHERE id=?',id);
      this.current(generation);if(!row||!this.media)return null;
      if(row.state==='STAGED'&&!this.media.exists(id)){
        this.media.discardStaging(id);await db.runAsync("DELETE FROM local_media WHERE id=? AND state='STAGED'",id);return null;
      }
      const bytes=await this.media.read({fileId:id,purpose:row.purpose});
      if(bytes.length!==row.bytes)throw Error('LOCAL_MEDIA_LENGTH_MISMATCH');
      this.current(generation);
      if(row.state==='STAGED')await db.runAsync("UPDATE local_media SET state='READY' WHERE id=?",id);
      this.current(generation);
      return {metadata:{id:row.id,purpose:row.purpose,bytes:row.bytes,durationSeconds:row.duration_seconds,persistedAt:row.persisted_at},bytes};
    });
  }
  async prepareMediaPlayback(id:string,generation:number):Promise<string>{
    const media=await this.readMedia(id,generation);
    if(!media)throw Error('LOCAL_MEDIA_MISSING');
    return this.serial(async()=>{
      this.current(generation);
      const file=new File(this.directory,`playback-${Crypto.randomUUID()}.m4a`);
      file.create();this.playbackFiles.add(file);
      try{file.write(media.bytes);this.current(generation);return file.uri;}
      catch(error){if(file.exists)file.delete();this.playbackFiles.delete(file);throw error;}
    });
  }
  releaseMediaPlayback(uri:string):Promise<void>{
    return this.serial(async()=>{
      for(const file of this.playbackFiles)if(file.uri===uri){if(file.exists)file.delete();this.playbackFiles.delete(file);}
    });
  }
  closeOwner(): Promise<void> {
    this.locked = true;
    return this.serial(async () => { const db = this.db; this.db = null; this.media = null;
      for(const file of this.playbackFiles){if(file.exists)file.delete();}this.playbackFiles.clear();
      await db?.closeAsync(); });
  }
}
