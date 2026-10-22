import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { LocalStore } from '@/platform/zenflow/local-store';
import { serializeOwner } from '@/core/zenflow/local-owner';
import * as Crypto from 'expo-crypto';
import * as SQLite from 'expo-sqlite';
import { File } from 'expo-file-system';
import { requireNativeModule } from 'expo-modules-core';

/** Development-only synthetic fixtures. Never authenticates an account or calls a business API. */
export default function LocalStoreProbe() {
  const [result,setResult]=useState('仅虚构数据：尚未运行'),[busy,setBusy]=useState(false);
  if (!__DEV__) return null;
  async function run(){
    if(busy)return;setBusy(true);setResult('正在验证');
    const owner={origin:'https://local-store-probe.invalid',environment:'synthetic-qa',tenantId:'fixture',subjectType:'APP_USER' as const,userId:'A'};
    let store:LocalStore|null=null;
    try{
      store=await LocalStore.openOwner(owner,1);
      await store.saveDraft('probe','NOTE','synthetic-secret-20261022',1);
      await store.closeOwner();store=null;
      store=await LocalStore.openOwner(owner,2);
      if((await store.readDraft('probe',2))?.body!=='synthetic-secret-20261022')throw Error('RESTORE_FAILED');
      let rejected=false;try{await store.readDraft('probe',1);}catch{rejected=true;}
      if(!rejected)throw Error('STALE_GENERATION_ACCEPTED');
      await store.closeOwner();store=null;
      store=await LocalStore.openOwner({...owner,userId:'B'},3);
      if(await store.readDraft('probe',3))throw Error('OWNER_LEAK');
      await store.closeOwner();store=null;
      const digest=await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256,serializeOwner(owner));
      const native=requireNativeModule('InvelleGarden');
      const directory=await native.privateStoreDirectory(digest);
      const bytes=await new File(directory,'state.db').bytes();
      if(new TextDecoder().decode(bytes).includes('synthetic-secret-20261022'))throw Error('PLAINTEXT_FOUND');
      const wrong=await SQLite.openDatabaseAsync('state.db',{useNewConnection:true},directory);
      let wrongRejected=false;
      try{await wrong.execAsync(`PRAGMA key = "x'${'00'.repeat(32)}'";`);await wrong.getFirstAsync('SELECT count(*) FROM sqlite_master');}catch{wrongRejected=true;}finally{await wrong.closeAsync();}
      if(!wrongRejected)throw Error('WRONG_KEY_ACCEPTED');
      setResult('PASS：加密、关闭重开、账号隔离、代次拒绝、错误钥匙拒绝');
    }catch(error){setResult('FAIL：'+String(error));}finally{await store?.closeOwner();setBusy(false);}
  }
  async function restoreOnly(){
    if(busy)return;setBusy(true);setResult('正在读取已存草稿');
    let store:LocalStore|null=null;
    try{store=await LocalStore.openOwner({origin:'https://local-store-probe.invalid',environment:'synthetic-qa',tenantId:'fixture',subjectType:'APP_USER',userId:'A'},10);
      if((await store.readDraft('probe',10))?.body!=='synthetic-secret-20261022')throw Error('COLD_RESTORE_FAILED');
      setResult('PASS：进程重启后草稿恢复（未重新写入）');
    }catch(error){setResult('FAIL：'+String(error));}finally{await store?.closeOwner();setBusy(false);}
  }
  async function mediaProbe(){
    if(busy)return;setBusy(true);setResult('正在验证原声密文');
    const owner={origin:'https://local-store-probe.invalid',environment:'synthetic-qa',tenantId:'fixture',subjectType:'APP_USER' as const,userId:'media-A'};
    let store:LocalStore|null=null;
    try{
      store=await LocalStore.openOwner(owner,40);
      const bytes=new TextEncoder().encode('synthetic-media-sentinel-20261022');
      const media=await store.saveMedia(bytes,'PRIVATE_NOTE',1,40);
      await store.saveDraft('media-pointer','NOTE',JSON.stringify({id:media.id}),40);
      await store.closeOwner();store=null;
      store=await LocalStore.openOwner(owner,41);
      const restored=await store.readMedia(media.id,41);
      if(!restored||new TextDecoder().decode(restored.bytes)!=='synthetic-media-sentinel-20261022')throw Error('MEDIA_RESTORE_FAILED');
      const digest=await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256,serializeOwner(owner));
      const directory=await requireNativeModule('InvelleGarden').privateStoreDirectory(digest);
      const file=new File(directory,media.id+'.enc'),sealed=await file.bytes();
      if(new TextDecoder().decode(sealed).includes('synthetic-media-sentinel'))throw Error('MEDIA_PLAINTEXT_FOUND');
      const damaged=sealed.slice();damaged[20]^=1;file.write(damaged);
      let rejected=false;
      try{await store.readMedia(media.id,41);}catch{rejected=true;}finally{file.write(sealed);}
      if(!rejected)throw Error('MEDIA_TAMPER_ACCEPTED');
      await store.closeOwner();store=null;
      store=await LocalStore.openOwner({...owner,userId:'media-B'},42);
      if(await store.readMedia(media.id,42))throw Error('MEDIA_OWNER_LEAK');
      setResult('PASS：原声密文落盘、重开恢复、篡改拒绝、账号隔离');
    }catch(error){setResult('FAIL：'+String(error));}finally{await store?.closeOwner();setBusy(false);}
  }
  async function mediaRestoreOnly(){
    if(busy)return;setBusy(true);setResult('正在只读恢复原声');
    let store:LocalStore|null=null;
    try{
      store=await LocalStore.openOwner({origin:'https://local-store-probe.invalid',environment:'synthetic-qa',tenantId:'fixture',subjectType:'APP_USER',userId:'media-A'},50);
      const pointer=await store.readDraft('media-pointer',50);
      if(!pointer)throw Error('MEDIA_POINTER_MISSING');
      const restored=await store.readMedia(JSON.parse(pointer.body).id,50);
      if(!restored||new TextDecoder().decode(restored.bytes)!=='synthetic-media-sentinel-20261022')throw Error('MEDIA_COLD_RESTORE_FAILED');
      setResult('PASS：进程重启后原声密文恢复（未重新写入）');
    }catch(error){setResult('FAIL：'+String(error));}finally{await store?.closeOwner();setBusy(false);}
  }
  return <View style={{flex:1,padding:40,justifyContent:'center',gap:24,backgroundColor:'#f7f5ec'}}><Text>原生加密存储验证 · 虚构数据</Text><Text selectable>{result}</Text><Pressable accessibilityRole="button" disabled={busy} onPress={()=>void run()} style={{padding:20,backgroundColor:'#dce4d2'}}><Text>运行加密存储验证</Text></Pressable><Pressable accessibilityRole="button" disabled={busy} onPress={()=>void restoreOnly()} style={{padding:20,backgroundColor:'#dce4d2'}}><Text>只读验证重启恢复</Text></Pressable><Pressable accessibilityRole="button" disabled={busy} onPress={()=>void mediaProbe()} style={{padding:20,backgroundColor:'#dce4d2'}}><Text>运行原声密文验证</Text></Pressable><Pressable accessibilityRole="button" disabled={busy} onPress={()=>void mediaRestoreOnly()} style={{padding:20,backgroundColor:'#dce4d2'}}><Text>只读恢复原声密文</Text></Pressable></View>;
}
