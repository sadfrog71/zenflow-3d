import { useEffect, useEffectEvent, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, ActivityIndicator, AppState, BackHandler, Keyboard, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import * as Crypto from 'expo-crypto';
import { GardenNavigation } from '@/core/zenflow/garden-navigation';
import type { GardenLevel, GardenPanel, ViewpointId } from '@/contracts/zenflow/garden';
import { preparePublicGarden, ensurePublicGardenRegion, type PreparedGarden, manifest } from './public-files';
import type { OwnerDraftStore } from '../use-owner-store';
import { GardenArchiveControls, useGardenArchive } from './garden-archive-panel';
import { SceneHost } from './scene-host';

type Props={localDrafts?:OwnerDraftStore;active:boolean;english:boolean;identityGeneration:number;onExit:()=>void;onWrite:()=>void;onBooks:()=>void;onChat:()=>void;onCreate:()=>void};
type Files=PreparedGarden&{html?:string;resources?:Record<string,ArrayBuffer>;dispose?:()=>void};
export function GardenShell(props:Props){
  const [attempt,setAttempt]=useState(0),[files,setFiles]=useState<Files|null>(null),[failed,setFailed]=useState(false),[diagnostic,setDiagnostic]=useState(''),[loaded,setLoaded]=useState([0,1]),[notice,setNotice]=useState('');
  const stableNode=useRef<ViewpointId>('N00'),stableLevel=useRef<GardenLevel>('WORLD_OVERVIEW');
  const nav=useMemo(()=>new GardenNavigation(Crypto.randomUUID(),props.identityGeneration,stableNode.current,stableLevel.current),[props.identityGeneration,attempt]);
  const [state,setState]=useState(nav.snapshot),[command,setCommand]=useState(''),[reduced,setReduced]=useState(false),[suspended,setSuspended]=useState(AppState.currentState!=='active');
  const {width}=useWindowDimensions(),sequence=useRef(0),timeout=useRef<ReturnType<typeof setTimeout>|null>(null);
  const [renderedPreset,setRenderedPreset]=useState<string|null>(null);
  const english=props.english;
  const environment=useRef({presetId:'day',lampEnabled:true});
  useEffect(()=>{setRenderedPreset(null);},[nav]);
  const archive=useGardenArchive(props.localDrafts,english,value=>{environment.current=value;send('environment.apply',value);});
  const context=useMemo(()=>JSON.stringify({sceneInstanceId:nav.instanceId,identityGeneration:nav.generation,nodeId:nav.snapshot.nodeId,level:nav.snapshot.level,language:props.english?'en':'zh'}),[nav]);
  function update(){setState(nav.snapshot);}
  function send(type:string,payload:object={},requestId=`native-${++sequence.current}`){setCommand(JSON.stringify({protocolVersion:1,sceneInstanceId:nav.instanceId,identityGeneration:nav.generation,requestId,type,payload}));}
  function clearDeadline(){if(timeout.current)clearTimeout(timeout.current);timeout.current=null;}
  function cancel(){clearDeadline();nav.cancel();send('navigation.cancel',{viewpointId:nav.snapshot.nodeId,level:nav.snapshot.level});update();}
  function close(){nav.closePanel();send('mode.apply',{mode:nav.snapshot.mode});update();}
  function open(panel:GardenPanel){nav.openPanel(panel);send('mode.apply',{mode:nav.snapshot.mode});update();}
  function back(){Keyboard.dismiss();if(nav.snapshot.mode==='TRANSITION')cancel();else if(nav.snapshot.panel)close();else if(nav.snapshot.level==='FIRST_PERSON')void go(nav.snapshot.nodeId,'SPACE_OVERVIEW');else if(nav.snapshot.level==='SPACE_OVERVIEW')void go(nav.snapshot.nodeId,'WORLD_OVERVIEW');else props.onExit();}
  const onBack=useEffectEvent(()=>{if(!props.active)return false;back();return true;});
  const lifecycle=useEffectEvent((appActive:boolean)=>{
    const active=appActive&&props.active;setSuspended(!active);if(!active){clearDeadline();nav.suspend();send('scene.suspend',{viewpointId:nav.snapshot.nodeId,level:nav.snapshot.level});}else{nav.resume();send('scene.resume',{mode:nav.snapshot.mode,reducedMotion:reduced});}update();
  });
  useEffect(()=>{lifecycle(AppState.currentState==='active');},[props.active,nav]);
  useEffect(()=>{const listener=AppState.addEventListener('change',value=>lifecycle(value==='active'));const memory=AppState.addEventListener('memoryWarning',()=>{clearDeadline();setFailed(true);send('scene.suspend');});const back=BackHandler.addEventListener('hardwareBackPress',()=>onBack());return()=>{listener.remove();memory?.remove();back?.remove();clearDeadline();nav.suspend();};},[nav]);
  useEffect(()=>{if(Platform.OS!=='web')return;const key=(event:KeyboardEvent)=>{if(event.key==='Escape'&&props.active){event.preventDefault();onBack();}};document.addEventListener('keydown',key);return()=>document.removeEventListener('keydown',key);},[props.active]);
  useEffect(()=>{let alive=true,dispose:(()=>void)|undefined;setFiles(null);setFailed(false);setState(nav.snapshot);setLoaded([0,1]);void preparePublicGarden((done,total)=>{if(alive)setLoaded([done,total]);},nav.snapshot.nodeId,nav.snapshot.level).then(value=>{dispose=(value as Files).dispose;if(alive)setFiles(value);else dispose?.();}).catch(error=>{if(alive){setDiagnostic(String(error));setFailed(true);}});return()=>{alive=false;dispose?.();};},[nav]);
  useEffect(()=>{let alive=true;void AccessibilityInfo.isReduceMotionEnabled().then(v=>{if(alive)setReduced(v);});const listener=AccessibilityInfo.addEventListener('reduceMotionChanged',setReduced);return()=>{alive=false;listener.remove();};},[]);
  useEffect(()=>{send('settings.apply',{reducedMotion:reduced});},[reduced,nav]);
  useEffect(()=>{if(!files)return;const deadline=setTimeout(()=>{if(nav.snapshot.mode==='LOADING')setFailed(true);},60000);return()=>clearTimeout(deadline);},[files,nav]);
  function receive(raw:string){
    if(raw.length>16384)return;let data;try{data=JSON.parse(raw);}catch{return;}
    if(data.type==='host.terminated'){setAttempt(a=>a+1);return;}
    if(data.sceneInstanceId!==nav.instanceId||data.identityGeneration!==nav.generation||data.protocolVersion!==1)return;
    if(data.type==='environment.applied'&&['day','night'].includes(data.payload?.presetId)&&data.payload.presetId===environment.current.presetId&&Object.keys(data.payload).length===1){setRenderedPreset(data.payload.presetId);return;}
    if(data.type==='scene.failed'){clearDeadline();setDiagnostic(String(data.payload?.code||'SCENE_FAILED')+': '+String(data.payload?.detail||''));setFailed(true);return;}
    if(data.type==='navigation.failed'&&data.requestId===nav.snapshot.pending?.requestId){cancel();return;}
    if(!nav.receive(data))return;
    if(data.type==='scene.ready'){send('settings.apply',{reducedMotion:reduced});send('environment.apply',environment.current);if(!props.active||AppState.currentState!=='active')lifecycle(false);}
    if(data.type==='navigation.arrived'){clearDeadline();stableNode.current=nav.snapshot.nodeId;stableLevel.current=nav.snapshot.level;AccessibilityInfo.announceForAccessibility(manifest.viewpoints.find(v=>v.id===nav.snapshot.nodeId)?.[english?'labelEn':'label']||'');}
    if(data.type==='space.select'&&manifest.viewpoints.some(v=>v.id===data.payload.viewpointId&&v.available))void go(data.payload.viewpointId);
    if(data.type==='hotspot.activate'){const panel:Record<string,GardenPanel>={'wish.take':'wish','wish.read':'wish','mailbox.open':'mailbox','friends.open':'friends'};if(panel[data.payload.anchorId])open(panel[data.payload.anchorId]);}
    update();
  }
  async function go(nodeId:ViewpointId,level:GardenLevel='SPACE_OVERVIEW'){
    if(!files)return;
    close();const id=`trip-${++sequence.current}`;if(!nav.navigate(nodeId,id,level))return;
    setNotice('');setLoaded([0,1]);update();clearDeadline();timeout.current=setTimeout(cancel,20000);
    try{
      await ensurePublicGardenRegion(nodeId,files,(done,total)=>{if(nav.snapshot.pending?.requestId===id)setLoaded([done,total]);},level);
      if(nav.snapshot.pending?.requestId===id)send('navigate.to',{viewpointId:nodeId,level},id);
    }catch{
      if(nav.snapshot.pending?.requestId!==id)return;
      cancel();setNotice(english?'This place could not open. Please retry.':'这处园境暂未打开，请重试。');
    }
  }
  const view=manifest.viewpoints.find(v=>v.id===state.nodeId)!;
  const nightText=renderedPreset==='night'?{color:'#f5f3e8',textShadowColor:'#0b1723',textShadowRadius:3}:undefined;
  const ready=state.mode==='IDLE_LOOK'||state.mode==='CONTENT_PANEL';
  const button=(zh:string,en:string,action:()=>void,disabled=false)=><Pressable key={en} accessibilityRole="button" accessibilityState={{disabled}} disabled={disabled} onPress={action} style={[s.button,disabled&&s.disabled]}><Text style={s.buttonText}>{english?en:zh}</Text></Pressable>;
  return <KeyboardAvoidingView style={s.root} behavior={Platform.OS==='ios'?'padding':undefined}>
    {files&&!failed&&<View style={StyleSheet.absoluteFill} accessibilityElementsHidden={!!state.panel} importantForAccessibility={state.panel?'no-hide-descendants':'auto'}><SceneHost key={nav.instanceId} {...files} context={context} command={command} onMessage={receive} style={StyleSheet.absoluteFill}/></View>}
    <View style={s.top}>{button(state.level==='FIRST_PERSON'?'返回空间':state.level==='SPACE_OVERVIEW'?'返回全园':'返回首页',state.level==='FIRST_PERSON'?'Back to space':state.level==='SPACE_OVERVIEW'?'Back to garden':'Home',back)}<View style={s.heading}><Text style={[s.eyebrow,nightText]}>INVELLE · {english?'YOUR QUIET GARDEN':'此间园境'}</Text><Text accessibilityRole="header" style={[s.title,nightText]}>{state.level==='WORLD_OVERVIEW'?(english?'Your garden':'我的园境'):`${view[english?'labelEn':'label']} · ${state.level==='SPACE_OVERVIEW'?(english?'Space overview':'空间全景'):(english?'First person':'第一视角')}`}</Text>{files?.fallback&&<Text style={s.small}>{english?'Using the previous cached garden':'正在使用此前缓存的园境'}</Text>}</View>{button('选择空间','Places',()=>open('places'),!ready)}</View>
    {!!notice&&<View style={s.notice}><Text style={s.body}>{notice}</Text></View>}
    {!ready&&!failed&&<View style={s.loading}><ActivityIndicator color="#365a49"/><Text style={s.body}>{english?'Opening the garden':'正在打开园境'} · {Math.round(loaded[0]/loaded[1]*100)}%</Text>{state.mode==='TRANSITION'&&button('取消前往','Cancel',cancel)}</View>}
    {failed&&<View style={s.loading}><Text style={s.body}>{english?'The garden could not open. Your records have not changed.':'园境暂未打开，你的记录没有改变。'}</Text>{__DEV__&&<Text style={s.small}>{diagnostic}</Text>}{button('重新打开','Retry',()=>setAttempt(a=>a+1))}</View>}
    {ready&&!failed&&<View style={s.bottom}>
      {state.level==='WORLD_OVERVIEW'?<>{button('选择一处空间','Choose a space',()=>open('places'))}<Text style={[s.body,nightText]}>{english?'Choose a place, then step inside.':'先选一处空间，再走近看看。'}</Text></>:state.level==='SPACE_OVERVIEW'?<>{button('进入第一视角','Enter first person',()=>{void go(state.nodeId,'FIRST_PERSON');})}{button('返回全园俯视图','Back to world view',()=>{void go(state.nodeId,'WORLD_OVERVIEW');})}</>:<>
        <View style={s.row}>{button('向左看','Look left',()=>send('camera.look',{delta:.18}),!!state.panel)}{button('回正视角','Recenter',()=>send('camera.recenter'),!!state.panel)}{button('向右看','Look right',()=>send('camera.look',{delta:-.18}),!!state.panel)}</View>
        <View style={s.row}>{state.nodeId==='N03'&&<>{button('取签与读签','Wish tree',()=>open('wish'))}{button('朋友','Friends',()=>open('friends'))}{button('信盒','Mailbox',()=>open('mailbox'))}</>}{button('阅读','Read',props.onBooks)}{button('聊天','Chat',props.onChat)}{button('创作','Create',props.onCreate)}{button('记录','Notes',props.onWrite)}</View></>}
      {renderedPreset&&<Text style={[s.small,nightText]}>{english?(renderedPreset==='night'?'Night light':'Day light'):(renderedPreset==='night'?'夜晚光线':'白昼光线')}</Text>}{button('设置','Settings',()=>open('settings'))}
    </View>}
    {state.panel&&<View style={s.panelLayer}><Pressable accessibilityLabel={english?'Close panel':'关闭面板'} style={StyleSheet.absoluteFill} onPress={close}/><View accessibilityViewIsModal style={[s.panel,{width:width>=900?380:'100%'}]}><ScrollView contentContainerStyle={s.panelContent}><View style={s.row}><Text accessibilityRole="header" style={s.panelTitle}>{english?{places:'Places',wish:'Wish tree',friends:'Friends',mailbox:'Mailbox',settings:'Settings'}[state.panel]:{places:'选择空间',wish:'树下的心意',friends:'朋友',mailbox:'信盒',settings:'让这里合你心意'}[state.panel]}</Text>{button('合上','Close',close)}</View>
      {state.panel==='places'?manifest.viewpoints.map(v=><View key={v.id}>{button(v.label,v.labelEn,()=>go(v.id as ViewpointId),!v.available)}{!v.available&&<Text style={s.small}>{english?'Region package pending':'该区域资源待接入'}</Text>}</View>):state.panel==='settings'?<><GardenArchiveControls archive={archive} english={english}/>{button(reduced?'减少动态：开':'减少动态：关',reduced?'Reduced motion: on':'Reduced motion: off',()=>setReduced(v=>!v))}<Text style={s.body}>{english?'Sound is off. Device preferences stay on this device.':'当前声音关闭。设备偏好只影响这台设备。'}</Text>{button('重新打开园境','Reload garden',()=>setAttempt(a=>a+1))}</>:<Text style={s.body}>{english?'This service is not connected yet. Nothing will be posted or sent from this panel.':'这项服务尚未接入，当前面板不会投稿或寄信。'}</Text>}
    </ScrollView></View></View>}
    {suspended&&<View style={s.cover}><Text style={s.body}>{english?'Garden paused':'园境已暂停'}</Text></View>}
  </KeyboardAvoidingView>;
}
const s=StyleSheet.create({notice:{position:'absolute',top:88,left:16,right:16,padding:12,borderRadius:16,backgroundColor:'#f5f3e8ed'},root:{flex:1,backgroundColor:'#dce0d4'},top:{position:'absolute',top:12,left:16,right:16,flexDirection:'row',alignItems:'center',gap:12},heading:{flex:1},eyebrow:{fontSize:10,letterSpacing:2,color:'#254638',textShadowColor:'#faf8ec',textShadowRadius:7},title:{fontSize:24,color:'#254638',textShadowColor:'#faf8ec',textShadowRadius:8},button:{minHeight:44,minWidth:44,paddingHorizontal:16,paddingVertical:13,borderRadius:24,backgroundColor:'#f5f3e8ed',alignItems:'center',justifyContent:'center'},buttonText:{color:'#304e40',fontSize:14},disabled:{opacity:.45},bottom:{position:'absolute',bottom:16,left:16,right:16,gap:12,alignItems:'center'},row:{flexDirection:'row',flexWrap:'wrap',alignItems:'center',gap:8},loading:{position:'absolute',top:'36%',alignSelf:'center',maxWidth:'90%',padding:24,gap:18,alignItems:'center',backgroundColor:'#f5f3e8ed',borderRadius:20},body:{fontSize:16,lineHeight:26,color:'#365447'},small:{fontSize:12,color:'#667568',marginTop:4,marginLeft:16},panelLayer:{position:'absolute',top:0,left:0,right:0,bottom:0,alignItems:'flex-end',backgroundColor:'#15282033'},panel:{height:'100%',backgroundColor:'#f8f5eb'},panelContent:{padding:24,gap:20,paddingBottom:40},panelTitle:{fontSize:22,color:'#304e40',flex:1},cover:{position:'absolute',top:0,left:0,right:0,bottom:0,backgroundColor:'#e6e8df',alignItems:'center',justifyContent:'center'}});
