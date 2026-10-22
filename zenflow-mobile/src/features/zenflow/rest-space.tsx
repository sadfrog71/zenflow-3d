import type {OwnerDraftStore} from './use-owner-store';
import {useEffect,useEffectEvent,useRef,useState} from 'react';
import {AccessibilityInfo,AppState,Pressable,StyleSheet,Text,View} from 'react-native';
import {WebView} from '@/platform/zenflow/web-view';
import {GardenShell} from './garden-shell/garden-shell';
import {Pond} from './pond';
import {BookShelf} from './book-shelf';
import {ReadingRoom} from './reading-room';
import source from './home-source.json';
import type {ZenflowConfig} from '@/core/zenflow/config';
import type {ZenflowSession} from '@/core/zenflow/session';
export function RestSpace({localDrafts,english,onSeated,onWrite,onSignIn,slogans,config,session,ownerId,identityGeneration,onChat,onCreate,active}:{localDrafts?:OwnerDraftStore;slogans:{zh:string;en:string}[];english:boolean;onSeated:(value:boolean)=>void;onWrite:()=>void;onSignIn:()=>void;config:ZenflowConfig|null;session:ZenflowSession|null;ownerId:string|null;identityGeneration:number;onChat:()=>void;onCreate:()=>void;active:boolean}){
 const [gardenStarted,setGardenStarted]=useState(false),[booksReturnToGarden,setBooksReturnToGarden]=useState(false);
 const [inside,setInside]=useState<'home'|'garden'|'pond'|'room'|'books'>('home'),[booksReturnToRoom,setBooksReturnToRoom]=useState(false),[reduced,setReduced]=useState(false);const view=useRef<import('react-native-webview').WebView>(null);
 const inject=(data:object)=>view.current?.injectJavaScript(`window.homeNative?.(${JSON.stringify(data)});true;`);
 const sync=()=>inject({action:'settings',lang:english?'en':'zh',reduce:reduced,slogans,active:active&&AppState.currentState==='active'});
 useEffect(()=>{void AccessibilityInfo.isReduceMotionEnabled().then(setReduced);const listener=AccessibilityInfo.addEventListener('reduceMotionChanged',setReduced);return()=>listener.remove();},[]);
 useEffect(()=>{if(active)sync();else inject({action:'background'});},[english,reduced,slogans,active]);
 const syncOnResume=useEffectEvent(()=>sync());
 useEffect(()=>{const listener=AppState.addEventListener('change',state=>{if(state!=='active')inject({action:'background'});else syncOnResume();});return()=>{listener.remove();inject({action:'background'});onSeated(false);};},[onSeated]);
 return <View style={s.fill}>{gardenStarted&&<View style={{flex:1,display:active&&inside==='garden'?'flex':'none'}}><GardenShell localDrafts={localDrafts} key={identityGeneration} active={active&&inside==='garden'} english={english} identityGeneration={identityGeneration} onWrite={onWrite} onChat={onChat} onCreate={onCreate} onBooks={()=>{setBooksReturnToRoom(false);setBooksReturnToGarden(true);setInside('books');}} onExit={()=>{onSeated(false);setInside('home');}}/></View>}{active&&inside!=='garden'&&(inside==='pond'?<Pond english={english} onSeated={onSeated} onExit={()=>setInside('home')}/>:inside==='room'?<ReadingRoom english={english} config={config} onBooks={()=>setInside('books')} onExit={()=>{setBooksReturnToRoom(false);onSeated(false);setInside('home');}}/>:inside==='books'?<BookShelf english={english} config={config} session={session} ownerId={ownerId} onSignIn={onSignIn} onExit={()=>{if(booksReturnToGarden){setBooksReturnToGarden(false);setInside('garden');}else if(booksReturnToRoom){onSeated(true);setInside('room');}else{onSeated(false);setInside('home');}}}/>:<WebView ref={view} source={source} originWhitelist={['about:blank']} javaScriptEnabled domStorageEnabled={false} allowsInlineMediaPlayback mediaPlaybackRequiresUserAction={false} onLoadEnd={sync} onMessage={event=>{try{const data=JSON.parse(event.nativeEvent.data);if(data.type==='enter'){setGardenStarted(true);setInside('garden');}if(data.type==='unwind')setInside('pond');if(data.type==='books'){setBooksReturnToRoom(true);onSeated(true);setInside('room');}if(data.type==='write')onWrite();}catch{}}} style={s.web}/>) }{active&&inside==='home'&&<Pressable accessibilityRole="button" style={s.gardenEntry} onPress={()=>{setGardenStarted(true);setInside('garden');}}><Text style={s.label}>{english?'Enter the garden':'进入园境'}</Text></Pressable>}</View>;
}
const s=StyleSheet.create({gardenEntry:{position:'absolute',top:12,right:16,minHeight:44,paddingHorizontal:18,paddingVertical:14,borderRadius:24,backgroundColor:'#f5f4edee',borderWidth:1,borderColor:'#7b8d7444'},fill:{flex:1},web:{flex:1,backgroundColor:'#f5f4ed'},back:{position:'absolute',top:12,left:16,paddingHorizontal:13,paddingVertical:12,borderRadius:20,backgroundColor:'#f5f4eddd'},label:{color:'#3f5c47',fontSize:12}});
