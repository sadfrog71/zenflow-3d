import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import * as Crypto from 'expo-crypto';
import type { OwnerDraftStore } from '../use-owner-store';
import type { GardenEnvironment, GardenOperation } from '@/contracts/zenflow/garden';
import { GardenJournalStore, type GardenJournal } from '@/core/zenflow/garden-journal';

function environment(value:GardenJournal|null):GardenEnvironment{
  let result:GardenEnvironment={...(value?.snapshot?.environment??{presetId:'day',lampEnabled:true})};
  for(const op of value?.pending?.operations??[]){
    if(op.type==='INIT'&&op.choices?.environment)result={...op.choices.environment};
    if(op.type==='SET_ENVIRONMENT')result={...result,...(op.presetId===undefined?{}:{presetId:op.presetId}),...(op.lampEnabled===undefined?{}:{lampEnabled:op.lampEnabled})};
  }
  return result;
}
export function useGardenArchive(localDrafts:OwnerDraftStore|undefined,english:boolean,onEnvironment?:(value:GardenEnvironment)=>void){
  const journal=useMemo(()=>localDrafts?new GardenJournalStore(localDrafts.store,localDrafts.generation):null,[localDrafts]);
  const [choice,setChoice]=useState<GardenEnvironment>({presetId:'day',lampEnabled:true});
  const epoch=useRef(0),callback=useRef(onEnvironment);callback.current=onEnvironment;
  const [value,setValue]=useState<GardenJournal|null>(null),[ready,setReady]=useState(false),[busy,setBusy]=useState(false),[message,setMessage]=useState('');
  const locked=!!value?.pending&&value.pendingDisposition!=='LOCAL_ONLY';
  useEffect(()=>{
    let current=true;++epoch.current;setBusy(false);setReady(false);setValue(null);setMessage('');
    const initial={presetId:'day',lampEnabled:true};setChoice(initial);callback.current?.(initial);
    if(journal)void journal.read().then(saved=>{if(current){setValue(saved);const restored=environment(saved);setChoice(restored);callback.current?.(restored);setReady(true);}}).catch(()=>{if(current)setMessage(english?'Local archive could not be opened.':'本机存档暂未能打开。');});
    return()=>{current=false;++epoch.current;};
  },[journal,english]);
  function select(presetId:'day'|'night'){
    if(locked||busy)return;
    const next={...choice,presetId};setChoice(next);setMessage('');callback.current?.(next);
  }
  async function save(){
    if(!journal||!ready||busy||locked||!['day','night'].includes(choice.presetId))return;
    const ticket=epoch.current;setBusy(true);setMessage('');
    try{
      let operations:GardenOperation[];
      if(!value?.snapshot){
        const init=value?.pending?.operations.find(op=>op.type==='INIT');
        operations=[{type:'INIT',choices:{...(init?.type==='INIT'?init.choices:{}),environment:choice}}];
      }else operations=[...(value.pending?.operations??[]).filter(op=>op.type!=='SET_ENVIRONMENT'),{type:'SET_ENVIRONMENT',presetId:choice.presetId as 'day'|'night',lampEnabled:choice.lampEnabled}];
      const next:GardenJournal={formatVersion:1,snapshot:value?.snapshot??null,pendingDisposition:'LOCAL_ONLY',pending:{clientRequestId:Crypto.randomUUID(),expectedVersion:value?.pending?.expectedVersion??value?.snapshot?.stateVersion??0,operations}};
      await journal.save(next);if(ticket!==epoch.current)return;setValue(next);setMessage(english?'Saved on this device. Cloud connection is pending.':'已存本机，云端连接待接通。');
    }catch{if(ticket===epoch.current)setMessage(english?'Local save did not finish. Keep this page open and retry.':'本机保存未完成，请保留页面并重试。');}
    finally{if(ticket===epoch.current)setBusy(false);}
  }
  return {choice,select,save,ready,busy,message,locked,dirty:JSON.stringify(choice)!==JSON.stringify(environment(value)),hasOwner:!!localDrafts,hasPending:!!value?.pending};
}
export function GardenArchiveControls({archive,english}:{archive:ReturnType<typeof useGardenArchive>;english:boolean}){
  return <View style={{gap:12}}><Text style={{fontSize:18,color:'#304e40'}}>{english?'My garden archive':'我的园境存档'}</Text>
    <Text style={{color:'#52684f',lineHeight:23}}>{!archive.hasOwner?(english?'Sign in to use the encrypted archive on this device.':'登录后可使用本机加密存档。'):archive.locked?(english?'A previous save needs confirmation. Its content is kept.':'有一份保存请求待核对，原内容已保留。'):archive.dirty?(english?'Preview only. Save to keep this choice.':'正在预览，保存后才会留在本机。'):archive.hasPending?(english?'A local garden choice is kept here. It will not be sent automatically.':'园境选择已留在本机，不会自动发送。'):(english?'Choose the light and save on this device. Cloud saving is not connected yet.':'选择光线并保存到本机，云端保存尚未接通。')}</Text>
    <View style={{flexDirection:'row',gap:12}}>{(['day','night'] as const).map(item=><Pressable key={item} accessibilityRole="button" accessibilityState={{selected:archive.choice.presetId===item,disabled:archive.locked||archive.busy}} disabled={archive.locked||archive.busy} onPress={()=>archive.select(item)} style={{padding:12,borderRadius:16,backgroundColor:archive.choice.presetId===item?'#d7e4cb':'#efeee5'}}><Text>{english?(item==='day'?'Day':'Night'):(item==='day'?'白昼':'夜晚')}{archive.choice.presetId===item?' ✓':''}</Text></Pressable>)}</View>
    {archive.hasOwner&&!archive.locked&&<Pressable accessibilityRole="button" disabled={!archive.ready||archive.busy} onPress={()=>void archive.save()} style={{padding:14,borderRadius:18,backgroundColor:'#e1e8d8',opacity:archive.ready&&!archive.busy?1:.5}}><Text style={{color:'#304e40'}}>{english?'Save on this device':'保存到本机'}</Text></Pressable>}
    {!!archive.message&&<Text accessibilityLiveRegion="polite" style={{color:'#52684f'}}>{archive.message}</Text>}
  </View>;
}
export function GardenArchivePanel({localDrafts,english,onEnvironment}:{localDrafts?:OwnerDraftStore;english:boolean;onEnvironment?:(value:GardenEnvironment)=>void}){
  const archive=useGardenArchive(localDrafts,english,onEnvironment);
  return <GardenArchiveControls archive={archive} english={english}/>;
}
