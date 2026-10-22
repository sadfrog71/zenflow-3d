import { audioCoordinator, type AudioLease } from '@/core/zenflow/audio-coordinator';
import { useEffect, useRef, useState } from 'react';
import { AppState, Button, Modal, SafeAreaView, ScrollView, Text, View } from 'react-native';
import { File } from 'expo-file-system';
import { requireNativeModule } from 'expo-modules-core';
import { RecordingPresets, requestRecordingPermissionsAsync, setAudioModeAsync, useAudioPlayer, useAudioPlayerStatus, useAudioRecorder, useAudioRecorderState } from 'expo-audio';
import type { LocalMedia } from '@/platform/zenflow/local-store';
import { PRIVATE_AUDIO_LIMITS } from '@/platform/zenflow/private-media-codec';
import type { OwnerDraftStore } from './use-owner-store';

export function PrivateAudioNotes({store,en,active}:{store?:OwnerDraftStore;en:boolean;active:boolean}){
  const [open,setOpen]=useState(false),[error,setError]=useState('');
  const context=useRef({store,epoch:0});if(context.current.store!==store)context.current={store,epoch:context.current.epoch+1};
  const preparing=useRef(false);
  const mounted=useRef(true);useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  async function show(){
    if(!store||preparing.current)return;
    preparing.current=true;let lease:AudioLease|null=null;
    try{
      lease=audioCoordinator.acquire('RECORDING');
      await requireNativeModule('InvelleGarden').preparePrivateAudioDirectory();
      if(mounted.current){setError('');setOpen(true);}
    }catch(error){if(mounted.current)setError(error instanceof Error&&error.message==='AUDIO_BUSY'?(en?'Stop the current audio session first.':'请先停止当前音频。'):(en?'Audio storage could not be prepared.':'暂未能准备录音存储，请重试。'));}
    finally{lease?.release();preparing.current=false;}
  }

  return <View style={{padding:16,gap:8}}><Button title={en?'Original voice drafts':'本机原声草稿'} disabled={!store||!active} onPress={()=>void show()}/>{!!error&&<Text>{error}</Text>}
    <Modal visible={open} onRequestClose={()=>{}} animationType="slide">{open&&store&&<AudioNotebook key={context.current.epoch} store={store} en={en} active={active} onClose={()=>setOpen(false)}/>}</Modal>
  </View>;
}
function AudioNotebook({store,en,active,onClose}:{store:OwnerDraftStore;en:boolean;active:boolean;onClose:()=>void}){
  const recorder=useAudioRecorder({...RecordingPresets.HIGH_QUALITY,directory:'cache'},event=>{if(event.isFinished&&running.current)void stopRef.current().catch(()=>{});});
  const status=useAudioRecorderState(recorder,200);
  const player=useAudioPlayer(null),playStatus=useAudioPlayerStatus(player);
  const [items,setItems]=useState<LocalMedia[]>([]),[busy,setBusy]=useState(false),[message,setMessage]=useState(''),[recording,setRecording]=useState(false),[pending,setPending]=useState(false);
  const live=useRef(true),running=useRef(false),pendingAudio=useRef<{uri:string;duration:number}|null>(null),playback=useRef<string|null>(null);
  const stopJob=useRef<Promise<void>|null>(null),stopRef=useRef<()=>Promise<void>>(async()=>{});
  const durationSeen=useRef(0);if(running.current)durationSeen.current=Math.max(durationSeen.current,status.durationMillis/1000);
  const starting=useRef(false),startEpoch=useRef(0),audioLease=useRef<AudioLease|null>(null);
  const releaseAudio=()=>{audioLease.current?.release();audioLease.current=null;};
  const say=(value:string)=>{if(live.current)setMessage(value);};
  async function refresh(){const rows=await store.store.listMedia(store.generation);if(live.current)setItems(rows.filter(row=>row.purpose==='PRIVATE_NOTE'));}
  async function stopPlayback(){
    try{player.pause();}catch{/* Native player may already be released during unmount. */}
    const uri=playback.current;playback.current=null;
    if(uri)await store.store.releaseMediaPlayback(uri);
    if(audioLease.current?.activity==='PLAYBACK')releaseAudio();
  }
  async function persist(){
    const audio=pendingAudio.current;if(!audio)return;
    const file=new File(audio.uri);
    if(!file.exists||file.size>PRIVATE_AUDIO_LIMITS.maxBytes)throw Error('MEDIA_SIZE_LIMIT');
    await store.store.saveMedia(await file.bytes(),'PRIVATE_NOTE',audio.duration,store.generation);
    if(file.exists)file.delete();pendingAudio.current=null;if(live.current)setPending(false);
    await refresh();say(en?'Saved encrypted on this device.':'原声已加密保存到本机。');
  }
  function stop():Promise<void>{
    startEpoch.current++;
    if(stopJob.current)return stopJob.current;
    stopJob.current=(async()=>{
      try{
        if(running.current){
          const duration=Math.min(PRIVATE_AUDIO_LIMITS.maxDurationSeconds,Math.max(0.01,durationSeen.current,recorder.currentTime));
          await recorder.stop();running.current=false;if(live.current)setRecording(false);
          if(!recorder.uri)throw Error('RECORDING_MISSING');
          pendingAudio.current={uri:recorder.uri,duration};if(live.current)setPending(true);
        }
        await persist();
      }catch(error){say(en?'Audio was not saved. Keep this page open and retry, or discard it.':'原声未能保存，请保留此页重试，或明确放弃。');throw error;}
      finally{if(!starting.current&&audioLease.current?.activity==='RECORDING'){await setAudioModeAsync({allowsRecording:false}).catch(()=>{});releaseAudio();}stopJob.current=null;}
    })();return stopJob.current;
  }
  stopRef.current=stop;
  async function start(){
    if(starting.current||running.current||pendingAudio.current)return;
    starting.current=true;const epoch=++startEpoch.current;setBusy(true);
    try{
      await stopPlayback();
      audioLease.current=audioCoordinator.acquire('RECORDING');
      const permission=await requestRecordingPermissionsAsync();
      if(!live.current||!active||epoch!==startEpoch.current)return;
      if(!permission.granted){releaseAudio();say(en?'Allow microphone access in Settings to record.':'请在系统设置中允许麦克风权限后再录音。');return;}
      await setAudioModeAsync({allowsRecording:true,playsInSilentMode:true,shouldPlayInBackground:false});
      const lease=audioLease.current;if(!lease)throw Error('AUDIO_LEASE_EXPIRED');
      lease.assertActive();await recorder.prepareToRecordAsync();
      lease.assertActive();
      if(!live.current||epoch!==startEpoch.current||AppState.currentState!=='active'){await recorder.stop();if(recorder.uri){const file=new File(recorder.uri);if(file.exists)file.delete();}releaseAudio();return;}
      durationSeen.current=0;recorder.record({forDuration:PRIVATE_AUDIO_LIMITS.maxDurationSeconds});running.current=true;setRecording(true);say('');
    }catch(error){if(audioLease.current?.activity==='RECORDING'){await setAudioModeAsync({allowsRecording:false}).catch(()=>{});releaseAudio();}if(recorder.uri){const file=new File(recorder.uri);if(file.exists)file.delete();}say(error instanceof Error&&error.message==='AUDIO_BUSY'?(en?'Another audio session is active. Stop it first.':'正在使用其他音频，请先停止。'):(en?'Recording could not start. Please try again.':'录音暂未能开始，请重试。'));}
    finally{starting.current=false;if(!running.current&&audioLease.current?.activity==='RECORDING'){await setAudioModeAsync({allowsRecording:false}).catch(()=>{});releaseAudio();}if(live.current)setBusy(false);}
  }
  async function play(item:LocalMedia){
    setBusy(true);let lease:AudioLease|null=null;
    try{
      await stopPlayback();lease=audioCoordinator.acquire('PLAYBACK');audioLease.current=lease;const uri=await store.store.prepareMediaPlayback(item.id,store.generation);
      if(!live.current||AppState.currentState!=='active'){await store.store.releaseMediaPlayback(uri);releaseAudio();return;}
      playback.current=uri;lease.assertActive();await setAudioModeAsync({allowsRecording:false,playsInSilentMode:true,shouldPlayInBackground:false});lease.assertActive();player.replace({uri});player.play();
    }catch{await stopPlayback().catch(()=>{});releaseAudio();say(en?'This audio could not be opened.':'这段原声暂未能打开。');}
    finally{if(live.current)setBusy(false);}
  }
  useEffect(()=>{
    live.current=true;void refresh().catch(()=>say(en?'Drafts could not be loaded.':'原声草稿暂未能读取。'));
    const unregister=store.registerFlush(()=>stopRef.current());
    const listener=AppState.addEventListener('change',state=>{if(state!=='active'){void stopPlayback();void stopRef.current().catch(()=>{});}});
    return()=>{live.current=false;unregister();listener.remove();void stopRef.current().catch(()=>{}).finally(()=>{
      const uri=pendingAudio.current?.uri??recorder.uri;if(uri){const file=new File(uri);if(file.exists)file.delete();}pendingAudio.current=null;
    });void stopPlayback().catch(()=>{});};
  },[store]);
  useEffect(()=>{if(!active){void stopPlayback();void stopRef.current().catch(()=>{});}},[active]);
  useEffect(()=>{if(running.current&&status.durationMillis>=PRIVATE_AUDIO_LIMITS.maxDurationSeconds*1000)void stopRef.current().catch(()=>{});},[status.durationMillis]);
  useEffect(()=>{if(playStatus.didJustFinish)void stopPlayback();},[playStatus.didJustFinish]);
  async function close(){setBusy(true);try{await stop();await stopPlayback();onClose();}catch{}finally{if(live.current)setBusy(false);}}
  return <SafeAreaView style={{flex:1,backgroundColor:'#f7f5ec'}}><ScrollView contentContainerStyle={{padding:32,gap:20}}>
    <Text style={{fontSize:24,color:'#355a45'}}>{en?'Original voice drafts':'本机原声草稿'}</Text>
    <Text>{en?'Private, encrypted on this device. No transcription or upload. Cloud audio is not available yet.':'仅保存在本机，按账号加密；不自动转写或上传。云端原声保存尚未接通。'}</Text>
    <Text accessibilityLiveRegion="polite">{recording?`${en?'Recording':'录音中'} · ${Math.floor(status.durationMillis/1000)} / ${PRIVATE_AUDIO_LIMITS.maxDurationSeconds}s`:message}</Text>
    <Button title={recording?(en?'Stop and save locally':'停止并存本机'):(en?'Start recording':'开始录音')} disabled={busy||pending||!active} onPress={()=>{if(recording){setBusy(true);void stop().catch(()=>{}).finally(()=>{if(live.current)setBusy(false);});}else void start();}}/>
    {pending&&<><Button title={en?'Retry encrypted save':'重试加密保存'} disabled={busy} onPress={()=>{setBusy(true);void stop().catch(()=>{}).finally(()=>{if(live.current)setBusy(false);});}}/><Button title={en?'Discard unsaved recording':'放弃未保存的录音'} disabled={busy} onPress={()=>{const audio=pendingAudio.current;if(audio){const file=new File(audio.uri);if(file.exists)file.delete();}pendingAudio.current=null;setPending(false);setMessage(en?'Recording discarded.':'已放弃未保存的录音。');}}/></>}
    {items.map((item,index)=><View key={item.id} style={{gap:6}}><Text>{en?'Voice draft':'原声草稿'} {index+1} · {item.durationSeconds.toFixed(1)}s · {new Date(item.persistedAt).toLocaleString(en?'en-US':'zh-CN')}</Text><Button title={`${en?'Listen to voice draft':'试听原声草稿'} ${index+1}`} disabled={busy||recording||pending} onPress={()=>void play(item)}/></View>)}
    {playStatus.playing&&<Button title={en?'Stop playback':'停止试听'} onPress={()=>void stopPlayback()}/>}
    <Button title={en?'Return to notes':'返回记事'} disabled={busy} onPress={()=>void close()}/>
  </ScrollView></SafeAreaView>;
}
