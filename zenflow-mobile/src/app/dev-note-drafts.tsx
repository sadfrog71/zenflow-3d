import { GardenArchivePanel } from '@/features/zenflow/garden-shell/garden-archive-panel';
import { useEffect, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { CoreSpace } from '@/features/zenflow/core-spaces';
import type { OwnerDraftStore } from '@/features/zenflow/use-owner-store';
import type { ZenflowSession } from '@/core/zenflow/session';
import { LocalStore } from '@/platform/zenflow/local-store';

// Real editor + real encrypted store; synthetic identity, no network and no successful cloud-save substitute.
const session={request:async(path:string,method?:string)=>{
  if(method)throw Error('SYNTHETIC_CLOUD_DISABLED');
  if(path.startsWith('notes'))return {items:[],hasMore:false};
  throw Error('SYNTHETIC_ROUTE_DISABLED');
}} as unknown as ZenflowSession;
export default function NoteDraftProbe(){
  const [handle,setHandle]=useState<OwnerDraftStore>(),[message,setMessage]=useState('正在打开虚构测试库');
  const generation=useRef(0),flushers=useRef(new Set<()=>Promise<void>>()),current=useRef<LocalStore|null>(null);
  async function reopen(){
    try{
      await Promise.all([...flushers.current].map(flush=>flush()));setHandle(undefined);
      await current.current?.closeOwner();current.current=null;
      const epoch=++generation.current;
      const store=await LocalStore.openOwner({origin:'https://note-editor-probe.invalid',environment:'synthetic-qa',tenantId:'fixture',subjectType:'APP_USER',userId:'editor'},epoch);
      current.current=store;setHandle({store,generation:epoch,registerFlush:flush=>{flushers.current.add(flush);return()=>{flushers.current.delete(flush);};}});
      setMessage('虚构身份 · 正式编辑器 · 真实加密库 · 不连接云端');
    }catch(error){setMessage('FAIL：'+String(error));}
  }
  useEffect(()=>{if(__DEV__)void reopen();return()=>{generation.current++;void current.current?.closeOwner();};},[]);
  if(!__DEV__)return null;
  return <View style={{flex:1,paddingTop:35,backgroundColor:'#f7f5ec'}}><View style={{padding:12,gap:8}}><Text>{message}</Text><Pressable accessibilityRole="button" onPress={()=>void reopen()} style={{padding:14,backgroundColor:'#dce4d2'}}><Text>关闭并重开编辑器</Text></Pressable></View>{handle&&<GardenArchivePanel localDrafts={handle} english={false}/>}
{handle&&<CoreSpace key={handle.generation} session={session} space="notes" english={false} ready localDrafts={handle}/>}</View>;
}
