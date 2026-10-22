import { PrivateMediaFiles } from '../private-media-files';
let mockKey:string|null=null;
const mockFiles=new Map<string,Uint8Array>();
let mockTamper=false;
jest.mock('expo-secure-store',()=>({WHEN_UNLOCKED_THIS_DEVICE_ONLY:6,getItemAsync:jest.fn(async()=>mockKey),setItemAsync:jest.fn(async(_name,key)=>{mockKey=key;})}));
jest.mock('expo-crypto',()=>({getRandomBytesAsync:jest.fn(async()=>new Uint8Array(32).fill(7))}));
jest.mock('../private-media-codec',()=>({PRIVATE_AUDIO_LIMITS:{maxBytes:5242880},sealPrivateMedia:jest.fn(async(bytes:Uint8Array)=>new Uint8Array([99,...bytes])),openPrivateMedia:jest.fn(async(bytes:Uint8Array)=>{if(mockTamper)throw Error('AUTH_FAILED');return bytes.slice(1);})}));
jest.mock('expo-file-system',()=>({File:class{
 path:string;constructor(root:string,name:string){this.path=root+name;}
 get exists(){return mockFiles.has(this.path);}get size(){return mockFiles.get(this.path)?.length??0;}
 create(){mockFiles.set(this.path,new Uint8Array());}write(bytes:Uint8Array){mockFiles.set(this.path,bytes);}
 async bytes(){return mockFiles.get(this.path)!;}delete(){mockFiles.delete(this.path);}
 move(target:any){mockFiles.set(target.path,mockFiles.get(this.path)!);mockFiles.delete(this.path);this.path=target.path;}
}}));
const id='12345678-1234-1234-1234-123456789abc';
const identity={fileId:id,purpose:'PRIVATE_NOTE' as const};
beforeEach(()=>{mockKey=null;mockFiles.clear();mockTamper=false;jest.clearAllMocks();});
test('writes only sealed bytes, verifies staging, atomically promotes and reopens with retained owner key',async()=>{
 const files=new PrivateMediaFiles('file:///owner/','a'.repeat(64),()=>{});
 await files.save(identity,new Uint8Array([1,2,3]),true);
 expect([...mockFiles.keys()]).toEqual([`file:///owner/${id}.enc`]);
 expect(mockFiles.get(`file:///owner/${id}.enc`)).toEqual(new Uint8Array([99,1,2,3]));
 const reopened=new PrivateMediaFiles('file:///owner/','a'.repeat(64),()=>{});
 expect(await reopened.read(identity)).toEqual(new Uint8Array([1,2,3]));
});
test('missing owner key never regenerates a replacement for existing media',async()=>{
 const files=new PrivateMediaFiles('file:///owner/','a'.repeat(64),()=>{});
 await expect(files.save(identity,new Uint8Array([1]),false)).rejects.toThrow('LOCAL_MEDIA_KEY_MISSING');
 await expect(files.read(identity)).rejects.toThrow('LOCAL_MEDIA_KEY_MISSING');
 expect(mockKey).toBeNull();expect(mockFiles.size).toBe(0);
});
test('staging authentication failure removes staging and never publishes a final file',async()=>{
 const files=new PrivateMediaFiles('file:///owner/','a'.repeat(64),()=>{});mockTamper=true;
 await expect(files.save(identity,new Uint8Array([1]),true)).rejects.toThrow('AUTH_FAILED');
 expect(mockFiles.size).toBe(0);
});
test('generation guard blocks late key reads before any file write',async()=>{
 let calls=0;const files=new PrivateMediaFiles('file:///owner/','a'.repeat(64),()=>{if(++calls>1)throw Error('LOCAL_STORE_LOCKED');});
 await expect(files.save(identity,new Uint8Array([1]),true)).rejects.toThrow('LOCAL_STORE_LOCKED');
 expect(mockKey).toBeNull();expect(mockFiles.size).toBe(0);
});
