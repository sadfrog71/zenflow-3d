import { LocalStore } from '../local-store';
import { serializeOwner } from '@/core/zenflow/local-owner';
const mockDb={getAllAsync:jest.fn(),execAsync:jest.fn(async(_sql:string)=>{}),getFirstAsync:jest.fn(),runAsync:jest.fn(async(_sql:string,..._args:unknown[])=>{}),closeAsync:jest.fn(async()=>{})};
let mockExists=false;
let mockKey:string|null=null;
jest.mock('expo-sqlite',()=>({openDatabaseAsync:jest.fn(async()=>mockDb)}));
jest.mock('expo-secure-store',()=>({WHEN_UNLOCKED_THIS_DEVICE_ONLY:6,getItemAsync:jest.fn(async()=>mockKey),setItemAsync:jest.fn(async(_k,v)=>{mockKey=v;})}));
jest.mock('expo-crypto',()=>({CryptoDigestAlgorithm:{SHA256:'SHA-256'},digestStringAsync:jest.fn(async()=> 'a'.repeat(64)),getRandomBytesAsync:jest.fn(async()=>new Uint8Array(32).fill(12))}));
jest.mock('expo-file-system',()=>({File:class{get exists(){return mockExists;}}}));
jest.mock('expo-modules-core',()=>({...jest.requireActual('expo-modules-core'),requireNativeModule:(name:string)=>name==='InvelleGarden'?({privateStoreDirectory:async()=> 'file:///test/private/'}):jest.requireActual('expo-modules-core').requireNativeModule(name)}));
const owner={origin:'https://invelle.example',environment:'local',tenantId:'1',subjectType:'APP_USER' as const,userId:'42'};
beforeEach(()=>{jest.clearAllMocks();mockDb.getAllAsync.mockResolvedValue([]);mockExists=false;mockKey=null;mockDb.getFirstAsync.mockImplementation(async(sql:string)=>sql==='PRAGMA cipher_version'?{cipher_version:'4.6'}:null);});
test('fails closed when native SQLite has no cipher, before creating schema or writing a draft',async()=>{
 mockDb.getFirstAsync.mockResolvedValue(null);
 await expect(LocalStore.openOwner(owner,1)).rejects.toThrow('SQLCIPHER_REQUIRED');
 expect(mockDb.execAsync.mock.calls.some(([s])=>s.includes('CREATE TABLE'))).toBe(false);
 expect(mockDb.closeAsync).toHaveBeenCalledTimes(1);
});
test('missing database key does not replace an existing encrypted database',async()=>{
 mockExists=true;
 await expect(LocalStore.openOwner(owner,1)).rejects.toThrow('LOCAL_KEY_MISSING');
 expect(mockDb.execAsync).not.toHaveBeenCalled();
});
test('closing blocks late and new work from a previous identity generation',async()=>{
 const store=await LocalStore.openOwner(owner,3);
 await expect(store.saveDraft('d','NOTE','private',2)).rejects.toThrow('LOCAL_STORE_LOCKED');
 await store.closeOwner();
 await expect(store.readDraft('d',3)).rejects.toThrow('LOCAL_STORE_LOCKED');
 expect(mockDb.runAsync).not.toHaveBeenCalledWith(expect.stringContaining('INSERT INTO draft'),expect.anything());
});
test('stored owner mismatch refuses to expose an existing draft',async()=>{
 mockDb.getFirstAsync.mockImplementation(async(sql:string)=>sql==='PRAGMA cipher_version'?{cipher_version:'4.6'}:sql.includes('local_meta')?{owner_key:'other owner',schema_version:1}:null);
 await expect(LocalStore.openOwner(owner,1)).rejects.toThrow('LOCAL_OWNER_OR_SCHEMA_MISMATCH');
 expect(mockDb.execAsync).toHaveBeenCalledWith('ROLLBACK');
});
test('owner encoding includes deployment and uses an unambiguous tuple',()=>{
 expect(serializeOwner(owner)).not.toEqual(serializeOwner({...owner,origin:'https://another.example'}));
 expect(serializeOwner({...owner,tenantId:'a:b',userId:'c'})).not.toEqual(serializeOwner({...owner,tenantId:'a',userId:'b:c'}));
 expect(()=>serializeOwner({...owner,subjectType:'ADMIN_USER' as any})).toThrow('INVALID_LOCAL_OWNER');
});
test('draft transaction rolls back on storage failure and never reports persisted success',async()=>{
 const store=await LocalStore.openOwner(owner,8);
 mockDb.runAsync.mockRejectedValueOnce(Error('DISK_FULL'));
 await expect(store.saveDraft('d','NOTE','pending',8)).rejects.toThrow('DISK_FULL');
 expect(mockDb.execAsync).toHaveBeenLastCalledWith('ROLLBACK');
 await store.closeOwner();
});
test('switching to another owner locks the previous handle before opening the next',async()=>{
 const first=await LocalStore.openOwner(owner,1);
 mockKey=null;
 const second=await LocalStore.openOwner({...owner,userId:'43'},2);
 await expect(first.readDraft('d',1)).rejects.toThrow('LOCAL_STORE_LOCKED');
 await second.closeOwner();
});

test('draft listing returns bounded metadata and a continuation without loading private bodies',async()=>{
 const store=await LocalStore.openOwner(owner,12);
 mockDb.getAllAsync.mockResolvedValue(Array.from({length:51},(_,index)=>({id:`notes:draft:${String(index).padStart(3,'0')}`,kind:'NOTE',local_revision:1,persisted_at:42})));
 const page=await store.listDrafts('notes',12);
 expect(page.items).toHaveLength(50);
 expect(page.nextCursor).toBe('notes:draft:049');
 expect(page.items[0]).not.toHaveProperty('body');
 await store.listDrafts('notes',12,page.nextCursor!);
 expect(mockDb.getAllAsync).toHaveBeenLastCalledWith(expect.stringContaining('LIMIT 51'),'notes:*','notes:draft:049');
 await expect(store.listDrafts('notes',12,'creation:draft:other')).rejects.toThrow('INVALID_DRAFT_CURSOR');
 await store.closeOwner();
});

test('garden journal refuses a stale local revision before writing any new content',async()=>{
 const store=await LocalStore.openOwner(owner,21);
 mockDb.getFirstAsync.mockImplementation(async(sql:string)=>sql.includes('garden_journal')?{local_revision:4}:null);
 const before=mockDb.runAsync.mock.calls.length;
 await expect(store.saveGardenJournal('{"pending":null}',3,21)).rejects.toThrow('LOCAL_GARDEN_CONFLICT');
 expect(mockDb.runAsync.mock.calls).toHaveLength(before);
 expect(mockDb.execAsync).toHaveBeenLastCalledWith('ROLLBACK');
 await store.closeOwner();
});
test('garden snapshot and pending operation commit together and remain generation locked',async()=>{
 const store=await LocalStore.openOwner(owner,22);
 const body=JSON.stringify({formatVersion:1,snapshot:null,pending:{clientRequestId:'pending-save'}});
 const result=await store.saveGardenJournal(body,0,22);
 expect(result.body).toBe(body);expect(result.localRevision).toBe(1);
 expect(mockDb.runAsync).toHaveBeenLastCalledWith(expect.stringContaining('INSERT INTO garden_journal'),body,1,expect.any(Number));
 await store.closeOwner();
 await expect(store.readGardenJournal(22)).rejects.toThrow('LOCAL_STORE_LOCKED');
});
test('a v1 encrypted owner database upgrades its schema transactionally without rewriting drafts',async()=>{
 mockDb.getFirstAsync.mockImplementation(async(sql:string)=>sql==='PRAGMA cipher_version'?{cipher_version:'4.6'}:sql.includes('local_meta')?{owner_key:serializeOwner(owner),schema_version:1}:null);
 const store=await LocalStore.openOwner(owner,23);
 expect(mockDb.runAsync).toHaveBeenCalledWith('UPDATE local_meta SET schema_version=3 WHERE id=1');
 expect(mockDb.runAsync.mock.calls.some(([sql])=>sql.includes('DELETE')||sql.includes('INSERT INTO draft'))).toBe(false);
 await store.closeOwner();
});

test('startup removes a pre-rename STAGED row without touching acknowledged media',async()=>{
 mockDb.getAllAsync.mockImplementation(async(sql:string)=>sql.includes("state='STAGED'")?[{id:'12345678-1234-1234-1234-123456789abc'}]:[]);
 const store=await LocalStore.openOwner(owner,31);
 expect(mockDb.runAsync).toHaveBeenCalledWith("DELETE FROM local_media WHERE id=? AND state='STAGED'",'12345678-1234-1234-1234-123456789abc');
 await store.closeOwner();
});
test('closed owner rejects media bytes, metadata and saves before I/O',async()=>{
 const store=await LocalStore.openOwner(owner,32);await store.closeOwner();
 await expect(store.readMedia('12345678-1234-1234-1234-123456789abc',32)).rejects.toThrow('LOCAL_STORE_LOCKED');
 await expect(store.listMedia(32)).rejects.toThrow('LOCAL_STORE_LOCKED');
 await expect(store.saveMedia(new Uint8Array([1]),'PRIVATE_NOTE',1,32)).rejects.toThrow('LOCAL_STORE_LOCKED');
});
