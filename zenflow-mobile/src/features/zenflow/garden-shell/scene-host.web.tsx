import { useEffect, useMemo, useRef } from 'react';
import { View } from 'react-native';
import type { SceneHostProps } from './scene-host';
/** The iframe keeps an opaque origin. Its only file capability is this public manifest map. */
export function SceneHost({html,resources,context,command,onMessage,style}:SceneHostProps){
  const ref=useRef<HTMLIFrameElement>(null),current=useRef({resources,onMessage});current.current={resources,onMessage};
  const channel=useMemo(()=>crypto.randomUUID(),[]);
  const content=useMemo(()=>{
    const bridge=`<script>window.InvelleGardenContext=${context};const channel=${JSON.stringify(channel)};let serial=0;const pending=new Map();window.ReactNativeWebView={postMessage:data=>parent.postMessage({channel,data},'*')};window.addEventListener('message',e=>{if(e.source!==parent||e.data?.channel!==channel)return;const d=e.data;if(d.command)window.invelleReceive?.(d.command);if(d.asset){const p=pending.get(d.id);if(!p)return;pending.delete(d.id);d.error?p.reject(Error('ASSET_UNAVAILABLE')):p.resolve(new Response(d.bytes,{status:200,headers:{'Content-Type':d.mime}}));}});window.fetch=input=>new Promise((resolve,reject)=>{const file=new URL(typeof input==='string'?input:input.url,document.baseURI).pathname.split('/').pop(),id=++serial;pending.set(id,{resolve,reject});parent.postMessage({channel,asset:true,file,id},'*');});</script>`;
    return (html||'').replace('<head>','<head>'+bridge);
  },[html,context,channel]);
  useEffect(()=>{
    const receive=(e:MessageEvent)=>{
      if(e.source!==ref.current?.contentWindow||e.data?.channel!==channel)return;
      if(e.data.asset){const {file,id}=e.data;if(typeof file!=='string'||typeof id!=='number')return;const bytes=current.current.resources?.[file]?.slice(0);e.source?.postMessage({channel,asset:true,id,error:!bytes,bytes,mime:file.endsWith('.webp')?'image/webp':'model/gltf-binary'},{targetOrigin:'*',transfer:bytes?[bytes]:[]});}
      else if(typeof e.data.data==='string')current.current.onMessage(e.data.data);
    };
    window.addEventListener('message',receive);return()=>window.removeEventListener('message',receive);
  },[channel]);
  useEffect(()=>{if(command)ref.current?.contentWindow?.postMessage({channel,command:JSON.parse(command)},'*');},[command,channel]);
  return <View style={style}><iframe ref={ref} title="Invelle garden scene" srcDoc={content} sandbox="allow-scripts" style={{width:'100%',height:'100%',border:0,display:'block'}}/></View>;
}
