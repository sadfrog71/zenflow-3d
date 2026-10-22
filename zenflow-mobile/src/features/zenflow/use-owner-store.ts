import { useEffect, useRef, useState } from 'react';
import { AppState, Platform } from 'react-native';
import type { ZenflowConfig } from '@/core/zenflow/config';
import type { SessionView } from '@/core/zenflow/session';
import { localOwner, serializeOwner } from '@/core/zenflow/local-owner';
import { LocalStore } from '@/platform/zenflow/local-store';
export type OwnerDraftStore={store:LocalStore;generation:number;flushAll?:()=>Promise<void>;registerFlush:(flush:()=>Promise<void>)=>()=>void};
export function useOwnerStore(config:ZenflowConfig|null,identity:SessionView){
  const [foreground,setForeground]=useState(AppState.currentState==='active');
  const [value,setValue]=useState<{key:string;handle:OwnerDraftStore}|null>(null);
  const flushers=useRef(new Set<()=>Promise<void>>());
  const lifecycleEpoch=useRef(0);
  const handle=useRef<OwnerDraftStore|null>(null);
  const owner=config&&identity.profile&&!identity.busy?localOwner(config,identity.profile):null;
  const key=owner?serializeOwner(owner)+':'+identity.generation:'';
  useEffect(()=>{
    const listener=AppState.addEventListener('change',state=>{
      const active=state==='active',epoch=++lifecycleEpoch.current;
      if(active){setForeground(true);return;}
      const old=handle.current;
      // Flush before effect cleanup locks the connection. A quick resume cancels this close.
      void Promise.allSettled([...flushers.current].map(flush=>flush())).then(()=>{
        if(lifecycleEpoch.current!==epoch)return;
        handle.current=null;void old?.store.closeOwner();setForeground(false);
      });
    });return()=>{lifecycleEpoch.current++;listener.remove();};
  },[]);
  useEffect(()=>{
    let alive=true;let opened:LocalStore|null=null;
    if(Platform.OS!=='web'&&owner&&foreground){
      void LocalStore.openOwner(owner,identity.generation).then(store=>{
        opened=store;if(!alive){void store.closeOwner();return;}
        const next={store,generation:identity.generation,flushAll:async()=>{await Promise.all([...flushers.current].map(flush=>flush()));},registerFlush:(flush:()=>Promise<void>)=>{flushers.current.add(flush);return()=>{flushers.current.delete(flush);};}};
        handle.current=next;setValue({key,handle:next});
      }).catch(()=>{if(alive)setValue(null);});
    }else setValue(null);
    return()=>{alive=false;if(handle.current?.store===opened)handle.current=null;
      // A changed identity locks immediately. No old draft may be dispatched under a new session.
      void opened?.closeOwner();};
  },[key,foreground]);
  return foreground&&value?.key===key?value.handle:undefined;
}
