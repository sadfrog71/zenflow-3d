import { GARDEN_ANCHORS, GARDEN_LIMITS, VIEWPOINT_IDS, type GardenLevel, type GardenMode, type GardenPanel, type SceneEnvelope, type ViewpointId } from '../../contracts/zenflow/garden';
export type NavigationSnapshot = {nodeId:ViewpointId;level:GardenLevel;mode:GardenMode;panel:GardenPanel|null;pending:{nodeId:ViewpointId;level:GardenLevel;requestId:string}|null};
/** Native owns navigation. A cancelled or stale WebView can never apply a delayed arrival. */
export class GardenNavigation {
  private value: NavigationSnapshot = {nodeId:'N00',level:'WORLD_OVERVIEW',mode:'LOADING',panel:null,pending:null};
  private seen = new Set<string>();
  private suspended: NavigationSnapshot|null = null;
  constructor(readonly instanceId:string,readonly generation:number,nodeId:ViewpointId='N00',level:GardenLevel='WORLD_OVERVIEW') {this.value.nodeId=nodeId;this.value.level=level;}
  get snapshot():NavigationSnapshot {return {...this.value,pending:this.value.pending?{...this.value.pending}:null};}
  navigate(nodeId:ViewpointId,requestId:string,level:GardenLevel='SPACE_OVERVIEW'):boolean {
    if(this.value.mode!=='IDLE_LOOK'||!VIEWPOINT_IDS.includes(nodeId))return false;
    if(level==='FIRST_PERSON'&&(this.value.level!=='SPACE_OVERVIEW'||nodeId!==this.value.nodeId))return false;
    this.value={...this.value,mode:'TRANSITION',pending:{nodeId,level,requestId}};return true;
  }
  cancel(){this.value={...this.value,pending:null,mode:this.value.mode==='TRANSITION'?'IDLE_LOOK':this.value.mode};}
  openPanel(panel:GardenPanel){if(!['places','settings'].includes(panel)&&this.value.level!=='FIRST_PERSON')return;if(this.value.mode==='IDLE_LOOK')this.value={...this.value,panel,mode:'CONTENT_PANEL'};}
  closePanel(){if(this.value.mode==='CONTENT_PANEL')this.value={...this.value,panel:null,mode:'IDLE_LOOK'};}
  suspend(){if(this.value.mode==='SUSPENDED')return;this.cancel();this.suspended=this.snapshot;this.value={...this.value,mode:'SUSPENDED'};}
  resume(){if(this.suspended){this.value=this.suspended;this.suspended=null;}}
  receive(input:unknown):boolean {
    if(!input||typeof input!=='object'||Array.isArray(input))return false;
    const o=input as SceneEnvelope;
    if(Object.keys(o).sort().join(',')!=='identityGeneration,payload,protocolVersion,requestId,sceneInstanceId,type')return false;
    if(o.protocolVersion!==1||o.sceneInstanceId!==this.instanceId||o.identityGeneration!==this.generation||typeof o.requestId!=='string'||!o.requestId||o.requestId.length>128||!o.payload||typeof o.payload!=='object'||Array.isArray(o.payload))return false;
    if(JSON.stringify(o).length>GARDEN_LIMITS.bridgeBytes||this.seen.has(o.requestId))return false;
    const p=o.payload;
    const keys=(allowed:string[])=>Object.keys(p).every(k=>allowed.includes(k));
    let accepted=false;
    if(o.type==='scene.ready'&&keys([])&&this.value.mode==='LOADING'){this.value={...this.value,mode:'IDLE_LOOK'};accepted=true;}
    if(o.type==='scene.ready'&&keys([])&&this.value.mode==='SUSPENDED'&&this.suspended?.mode==='LOADING'){this.suspended={...this.suspended,mode:'IDLE_LOOK'};accepted=true;}
    if(o.type==='navigation.arrived'&&keys(['viewpointId','level'])&&this.value.mode==='TRANSITION'&&this.value.pending?.requestId===o.requestId&&this.value.pending.nodeId===p.viewpointId&&this.value.pending.level===p.level){this.value={...this.value,nodeId:p.viewpointId as ViewpointId,level:p.level as GardenLevel,pending:null,mode:'IDLE_LOOK'};accepted=true;}
    if(o.type==='space.select'&&keys(['viewpointId'])&&this.value.mode==='IDLE_LOOK'&&this.value.level==='WORLD_OVERVIEW'&&VIEWPOINT_IDS.includes(p.viewpointId as ViewpointId))accepted=true;
    if(o.type==='hotspot.activate'&&keys(['anchorId','viewpointId'])&&this.value.mode==='IDLE_LOOK'&&this.value.level==='FIRST_PERSON'&&p.viewpointId===this.value.nodeId&&typeof p.anchorId==='string'&&GARDEN_ANCHORS[this.value.nodeId]?.includes(p.anchorId))accepted=true;
    if(accepted){this.seen.add(o.requestId);if(this.seen.size>256)this.seen.delete(this.seen.values().next().value!);}
    return accepted;
  }
}
