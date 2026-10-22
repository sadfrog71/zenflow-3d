<script setup lang="ts">
import {computed,onUnmounted,reactive,ref,watch} from 'vue';
import {ElMessage} from 'element-plus';
import {api,errorText} from '../../api/zenflow/client';
import {useIdentity} from '../../stores/identity';
interface Revision{id:string;revisionNo:number;version:number;state:string;active:boolean}
interface Model{id:string;name:string}
interface Source{snapshotId:string;title:string;citationVisibility:string;excerpt:string;sourceLocator:string}
interface Snapshot{previewId:string;revisionId:string;revisionNo:number;modelId:string;releaseDigest:string;references:{state:string;items:Source[]};run:{runId:string;status:string;draftContent:string;lastEventSeq:number;errorMessageSafe?:string}}
interface Trial{label:string;previewId:string;runId:string;status:string;content:string;lastSeq:number;revisionNo?:number;modelId?:string;digest?:string;references:Source[];referenceState:string;error:string}
const props=defineProps<{revisions:Revision[];models:Model[];online:boolean}>();
const question=ref(''),locale=ref('en-US'),revisionId=ref(''),starting=ref(false),trials=ref<Trial[]>([]);
const history=reactive<{role:'USER'|'ASSISTANT';content:string}[]>([]);
const ready=computed(()=>props.revisions.filter(row=>row.state==='READY'));
watch(ready,rows=>{if(!rows.some(row=>row.id===revisionId.value))revisionId.value=rows.find(row=>!row.active)?.id||rows[0]?.id||'';},{immediate:true});
const controllers=new Map<string,AbortController>();const operations=new Set<string>();let disposed=false;
const terminal=(status:string)=>['COMPLETED','FAILED','CANCELLED'].includes(status);
const running=computed(()=>starting.value||trials.value.some(trial=>trial.previewId&&!terminal(trial.status)));
function statusText(status:string){return ({COMPLETED:'已完成',FAILED:'试聊失败',CANCELLED:'已停止',CANCEL_REQUESTED:'正在停止',CREATING:'创建中',DISCONNECTED:'连接中断'} as Record<string,string>)[status]||'运行中';}
function modelName(id?:string){return props.models.find(model=>model.id===id)?.name||id||'待确定';}
async function hydrate(trial:Trial,signal?:AbortSignal){const data:Snapshot=(await api.get('/companion/previews/'+trial.previewId)).data;if(disposed||signal?.aborted)return;if(trial.runId&&trial.runId!==data.run.runId)throw new Error('PREVIEW_RUN_MISMATCH');trial.runId=data.run.runId;trial.status=data.run.status;trial.content=data.run.status==='FAILED'&&data.run.draftContent===data.run.errorMessageSafe?trial.content:data.run.draftContent||'';trial.lastSeq=data.run.lastEventSeq;trial.revisionNo=data.revisionNo;trial.modelId=data.modelId;trial.digest=data.releaseDigest;trial.references=data.references?.items||[];trial.referenceState=data.references?.state||'PENDING';trial.error=data.run.status==='FAILED'?'本次试聊未完成，请检查模型和参考资料后重试。':data.run.errorMessageSafe||'';}
function retryDelay(milliseconds:number,signal:AbortSignal){return new Promise<void>(resolve=>{if(signal.aborted){resolve();return;}const finish=()=>{clearTimeout(timer);signal.removeEventListener('abort',finish);resolve();};const timer=setTimeout(finish,milliseconds);signal.addEventListener('abort',finish,{once:true});});}
async function follow(trial:Trial){
 const controller=new AbortController();controllers.set(trial.previewId,controller);
 try{
  for(let attempt=0;attempt<3&&!disposed;attempt++){
   if(attempt)await retryDelay(attempt*500,controller.signal);if(disposed||controller.signal.aborted)return;await hydrate(trial,controller.signal);if(disposed||controller.signal.aborted||terminal(trial.status))return;
   let response=await fetch('/api/zenflow-admin/v1/companion/previews/'+trial.previewId+'/events?afterSeq='+trial.lastSeq,{headers:{Authorization:'Bearer '+useIdentity().token},signal:controller.signal});
   if(response.status===401&&await useIdentity().refresh())response=await fetch('/api/zenflow-admin/v1/companion/previews/'+trial.previewId+'/events?afterSeq='+trial.lastSeq,{headers:{Authorization:'Bearer '+useIdentity().token},signal:controller.signal});
   if(!response.ok||!response.body)throw new Error('PREVIEW_STREAM_UNAVAILABLE');
   const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='';
   try{while(!disposed){const chunk=await reader.read();if(disposed||controller.signal.aborted)return;if(chunk.done)break;buffer+=decoder.decode(chunk.value,{stream:true});let separator:RegExpExecArray|null;
    while((separator=/\r?\n\r?\n/.exec(buffer))!==null){const frame=buffer.slice(0,separator.index);buffer=buffer.slice(separator.index+separator[0].length);const payload=frame.split(/\r?\n/).filter(line=>line.startsWith('data:')).map(line=>line.slice(5).replace(/^ /,'')).join('\n');if(!payload)continue;
     const event=JSON.parse(payload) as {runId:string;seq:number;type:string;data:{delta?:string}};
     if(event.runId!==trial.runId||!Number.isSafeInteger(event.seq)||event.seq<0)throw new Error('PREVIEW_EVENT_INVALID');
     if(event.seq>trial.lastSeq){trial.lastSeq=event.seq;if(event.type==='RESPONSE_DELTA'){if(typeof event.data?.delta!=='string')throw new Error('PREVIEW_EVENT_INVALID');trial.content+=event.data.delta;}if(['RUN_COMPLETED','RUN_FAILED','RUN_CANCELLED'].includes(event.type)){await hydrate(trial,controller.signal);return;}}
    }
    if(buffer.length>1048576)throw new Error('PREVIEW_STREAM_TOO_LARGE');
   }}finally{await reader.cancel().catch(()=>{});reader.releaseLock();}
  }
  if(disposed||controller.signal.aborted)return;await hydrate(trial,controller.signal);if(!disposed&&!terminal(trial.status)){trial.status='DISCONNECTED';trial.error='连接中断，可刷新状态或停止本次试聊。';}
 }catch(error){if(!disposed&&!controller.signal.aborted){try{await hydrate(trial,controller.signal);}catch{}if(!disposed&&!controller.signal.aborted&&!terminal(trial.status)){trial.status='DISCONNECTED';trial.error='未能持续接收回答，可刷新状态或停止本次试聊。';}}}
 finally{if(controllers.get(trial.previewId)===controller)controllers.delete(trial.previewId);}
}
async function start(compare:boolean){
 const revision=ready.value.find(row=>row.id===revisionId.value);if(!revision||!question.value.trim()){ElMessage.warning('请填写测试问题，并选择已准备的版本。');return;}
 if(history.some(message=>!message.content.trim())||history.reduce((sum,message)=>sum+message.content.length,0)>16000){ElMessage.warning('请检查手工历史，合计最多 16000 字。');return;}
 if(running.value)return;starting.value=true;
 const targets=compare?[{label:'线上版本',body:{target:'ONLINE'}},{label:'所选版本',body:{target:'REVISION',revisionId:revision.id,expectedVersion:revision.version}}]:[{label:'所选版本',body:{target:'REVISION',revisionId:revision.id,expectedVersion:revision.version}}];
 trials.value=targets.map(target=>({label:target.label,previewId:'',runId:'',status:'CREATING',content:'',lastSeq:0,references:[],referenceState:'PENDING',error:''}));
 const materials={question:question.value,locale:locale.value,history:history.map(message=>({...message}))};
 await Promise.allSettled(targets.map(async(target,index)=>{const trial=trials.value[index];try{const result=(await api.post('/companion/previews',{...target.body,...materials})).data;
  if(disposed){await api.post('/companion/previews/'+result.previewId+'/cancel').catch(()=>{});return;}trial.previewId=result.previewId;trial.runId=result.runId;trial.status=result.status;void follow(trial);
 }catch(error){if(!disposed){trial.status='FAILED';trial.error=errorText(error);}}}));
 if(!disposed)starting.value=false;
}
async function updateTrial(trial:Trial,cancel:boolean){
 if(disposed||operations.has(trial.previewId))return;operations.add(trial.previewId);
 controllers.get(trial.previewId)?.abort();controllers.delete(trial.previewId);
 try{if(cancel){await api.post('/companion/previews/'+trial.previewId+'/cancel');if(disposed)return;trial.status='CANCEL_REQUESTED';}await hydrate(trial);if(!disposed&&!terminal(trial.status))void follow(trial);}
 catch(error){if(!disposed){trial.status='DISCONNECTED';trial.error=errorText(error);ElMessage.error(trial.error);}}
 finally{operations.delete(trial.previewId);}
}
function stop(trial:Trial){return updateTrial(trial,true);}
function refresh(trial:Trial){return updateTrial(trial,false);}
onUnmounted(()=>{disposed=true;for(const controller of controllers.values())controller.abort();for(const trial of trials.value)if(trial.previewId&&!terminal(trial.status))void api.post('/companion/previews/'+trial.previewId+'/cancel').catch(()=>{});controllers.clear();});
</script>
<template><section class="preview-compare"><p class="muted">用同一问题比较线上和已准备版本。历史只使用你在这里填写的测试材料。</p>
 <el-form label-position="top" :disabled="running"><el-form-item label="所选版本"><el-select v-model="revisionId" placeholder="选择已准备的版本"><el-option v-for="row in ready" :key="row.id" :value="row.id" :label="'版本 '+row.revisionNo+(row.active?' · 当前线上':' · 已准备')"/></el-select></el-form-item><el-form-item label="回答语言"><el-select v-model="locale"><el-option label="English" value="en-US"/><el-option label="中文" value="zh-CN"/></el-select></el-form-item><el-form-item label="测试问题"><el-input v-model="question" type="textarea" :rows="3" maxlength="8000" show-word-limit/></el-form-item>
 <el-collapse><el-collapse-item title="手工测试历史（可选，最多 8 条）" name="history"><div v-for="(message,index) in history" :key="index" class="history-row"><el-select v-model="message.role"><el-option label="用户" value="USER"/><el-option label="陪伴者" value="ASSISTANT"/></el-select><el-input v-model="message.content" type="textarea" :rows="2" maxlength="8000"/><el-button text @click="history.splice(index,1)">移除</el-button></div><el-button :disabled="history.length>=8" @click="history.push({role:'USER',content:''})">添加历史</el-button></el-collapse-item></el-collapse></el-form>
 <div class="actions"><el-button type="primary" :disabled="running||!revisionId" @click="start(false)">试聊所选版本</el-button><el-button :disabled="running||!online||!revisionId" @click="start(true)">比较线上与所选版本</el-button></div>
 <el-empty v-if="!ready.length" description="先测试模型并准备陪伴版本，再进行试聊。"/>
 <div class="trial-grid"><article v-for="trial in trials" :key="trial.label" class="model-card"><div class="card-heading"><h2>{{trial.label}}</h2><el-tag :type="trial.status==='COMPLETED'?'success':trial.status==='FAILED'?'danger':'info'">{{statusText(trial.status)}}</el-tag></div><p class="muted">版本 {{trial.revisionNo??'待确定'}} · {{modelName(trial.modelId)}}</p><div class="trial-answer">{{trial.content||(terminal(trial.status)?'本次没有生成回答。':'等待回答…')}}</div><el-alert v-if="trial.error" :title="trial.error" type="warning" :closable="false"/><div class="actions"><el-button v-if="trial.previewId&&!terminal(trial.status)" @click="stop(trial)">停止试聊</el-button><el-button v-if="trial.previewId" @click="refresh(trial)">刷新状态</el-button></div>
 <el-collapse><el-collapse-item title="命中资料与版本" name="sources"><p v-if="trial.referenceState==='PENDING'">资料检索尚未完成。</p><p v-else-if="trial.referenceState==='UNAVAILABLE'">来源已变化或不可用，暂不展示。</p><p v-else-if="!trial.references.length">本轮没有命中参考资料。</p><article v-for="source in trial.references" :key="source.snapshotId"><h3>{{source.title}}</h3><p class="muted">{{source.citationVisibility==='PUBLIC'?'公开引用':source.citationVisibility==='TITLE_ONLY'?'仅展示标题':'内部参考'}}</p><p class="source-text">{{source.excerpt}}</p><p v-if="source.sourceLocator" class="muted">{{source.sourceLocator}}</p></article><p v-if="trial.digest" class="muted digest">发布摘要：{{trial.digest}}</p></el-collapse-item></el-collapse></article></div>
</section></template>
<style scoped>.preview-compare{padding-top:16px}.history-row{display:grid;grid-template-columns:120px 1fr auto;gap:12px;margin-bottom:12px}.trial-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:20px;margin-top:20px}.trial-answer{white-space:pre-wrap;line-height:1.8;min-height:120px;margin:20px 0}.source-text{white-space:pre-wrap;line-height:1.7}.digest{overflow-wrap:anywhere}@media(max-width:600px){.history-row{grid-template-columns:1fr}.trial-grid{grid-template-columns:1fr}}</style>
