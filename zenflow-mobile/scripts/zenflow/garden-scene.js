import * as THREE from 'three';
import { Reflector } from './vendor/Reflector.js';
import { mergeVertices } from './vendor/BufferGeometryUtils.js';
import { timberFrame, softSeat, pineNeedles, cinematicBackdrop, quietLakeShader } from './quiet-art.js';

const $ = id => document.getElementById(id);
const sceneParams=new URLSearchParams(location.search);
const embedded=sceneParams.get('embed')==='1';
if(embedded)document.body.classList.add('embedded');
const notifyParent=(type,data={})=>{if(embedded&&parent!==window)parent.postMessage({source:'cijian-garden',type,...data},location.origin)};
const media = matchMedia('(prefers-reduced-motion: reduce)');
let lang = sceneParams.get('lang')==='en'?'en':'zh', still = media.matches||sceneParams.get('reduce')==='1', seated = false, ready = false;
let renderer, scene, camera, water, cushion, porch, island, breathRing, frame = 0, lastFrame = 0;
let yaw = 0, pitch = 0, drag = null, transition = null, previousFocus = null;
let mobile = innerWidth < 700;
const position = new THREE.Vector3(), target = new THREE.Vector3();
const clock = new THREE.Clock(), raycaster = new THREE.Raycaster();
const words = {
  zh:{title:'此间 · 临水庭院',garden:'临水庭院，三维空间',eyebrow:'临水庭院',heading:'在这里，<br>歇一会儿。',sit:'坐一会儿',loading:'正在走近…',back:'返回此间',gesture:'轻轻拖动，看看四周',motion:'静止画面',moving:'恢复微动',stand:'起身',hint:'不用做什么。',about:'关于这个空间',ae:'此间 · 空间小样',at:'一处临水的庭院',ab:'廊下的蒲团、石阶、松树和水面构成三维空间，远山是一幅轻量景层。点蒲团或“坐一会儿”可以坐下，轻拖画面可以看看四周。',al:'这是空间体验小样。多套场景、背景音乐和语音聊天将在后续版本接入。',done:'知道了',close:'关闭',sat:'已经坐下。可轻拖画面看四周，按 Escape 或点起身返回。',stood:'已回到庭院。',fh:'先看一眼山水',fb:'当前浏览器暂时无法显示三维庭院。可返回此间，或换一个支持 WebGL 2 的浏览器。'},
  en:{title:'Invelle · By the water',garden:'A three-dimensional waterside courtyard',eyebrow:'BY THE WATER',heading:'Take your<br>time.',sit:'Sit a while',loading:'Coming closer…',back:'Back to Invelle',gesture:'Drag gently to look around',motion:'Still view',moving:'Gentle motion',stand:'Stand up',hint:'Nothing to do.',about:'About this space',ae:'INVELLE · A SPATIAL STUDY',at:'A courtyard by the water',ab:'The cushion, stone floor, pine and water form a three-dimensional space. The mountains are a lightweight scenic layer. Tap the cushion or “Sit a while” to sit. Drag gently to look around.',al:'This is a spatial prototype. More scenes, background music and voice chat are planned for a later version.',done:'Got it',close:'Close',sat:'Seated. Drag gently to look around. Press Escape or Stand up to return.',stood:'Back in the courtyard.',fh:'A glimpse of the mountains',fb:'This browser cannot display the 3D courtyard. Return to Invelle, or try a browser with WebGL 2 support.'}
};
function translate(){
  const w=words[lang];document.documentElement.lang=lang==='zh'?'zh-CN':'en';document.title=w.title;
  for(const [id,key] of Object.entries({eyebrow:'eyebrow','sit-label':ready?'sit':'loading',back:'back',gesture:'gesture',stand:'stand','seated-hint':'hint','about-eyebrow':'ae','about-title':'at','about-body':'ab','about-limit':'al','about-done':'done'}))$(id).textContent=w[key];
  $('heading').innerHTML=w.heading;$('language').textContent=lang==='zh'?'EN':'中';$('language').setAttribute('aria-label',lang==='zh'?'Switch to English':'切换为中文');$('info').setAttribute('aria-label',w.about);$('garden').setAttribute('aria-label',w.garden);document.querySelector('.brand').setAttribute('aria-label',w.back);document.querySelector('.close').setAttribute('aria-label',w.close);
  $('motion').textContent=still?w.moving:w.motion;$('motion').setAttribute('aria-pressed',String(still));
  $('fallback').querySelector('h2').textContent=w.fh;$('fallback').querySelector('p').textContent=w.fb;$('fallback').querySelector('a').textContent=w.back;
}
$('language').onclick=()=>{lang=lang==='zh'?'en':'zh';translate()};
$('info').onclick=()=>{$('about').showModal()};
$('motion').onclick=()=>setStill(!still);
$('sit').onclick=()=>sit(true);$('stand').onclick=()=>sit(false);
function setStill(value){still=value;if(still)transition=null;document.body.classList.toggle('still',still);translate();requestFrame()}
media.addEventListener('change',event=>setStill(event.matches));
window.addEventListener('keydown',event=>{
  if(event.key==='Escape'&&seated){event.preventDefault();sit(false)}
  if(document.activeElement===$('garden')&&['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(event.key)){
    event.preventDefault();yaw=THREE.MathUtils.clamp(yaw+(event.key==='ArrowLeft'?-.018:event.key==='ArrowRight'?.018:0),-.105,.105);pitch=THREE.MathUtils.clamp(pitch+(event.key==='ArrowUp'?.012:event.key==='ArrowDown'?-.012:0),-.05,.05);requestFrame();
  }
});
function viewpoint(sat=seated){
  if(mobile)return {pos:new THREE.Vector3(0,sat?1.0:2.2,sat?1.03:5.2),look:new THREE.Vector3(0,sat?1.07:.60,sat?-9:-2)};
  const x=sat?(mobile?.74:.72):(mobile?.32:0);
  return {pos:new THREE.Vector3(x,sat?.88:1.6,sat?.84:5.5),look:new THREE.Vector3(mobile?.25:.5,sat?.95:1.2,-9)};
}
function sit(value){
  if(!ready||value===seated)return;
  const fromP=camera.position.clone(),fromT=target.clone();seated=value;yaw=0;pitch=0;
  const to=viewpoint();transition=still?null:{start:performance.now(),fromP,fromT,toP:to.pos,toT:to.look};
  document.body.classList.toggle('seated',seated);$('seated-controls').hidden=!seated;
  for(const id of ['header','invitation','footer'])$(id).inert=seated;
  $('status').textContent=words[lang][seated?'sat':'stood'];notifyParent('seated',{seated});
  if(!embedded){if(seated){previousFocus=document.activeElement;$('stand').focus({preventScroll:true})}else{(previousFocus?.isConnected&&previousFocus!==document.body?previousFocus:$('sit')).focus({preventScroll:true})}}
  requestFrame();
}
function fail(error){notifyParent('failed');console.error('Courtyard could not start:',error);ready=false;document.body.classList.add('failed');$('fallback').hidden=false;$('sit').disabled=true;if(frame)cancelAnimationFrame(frame);renderer?.dispose();}
function mesh(geometry,material,x=0,y=0,z=0,parent=scene){const m=new THREE.Mesh(geometry,material);m.position.set(x,y,z);m.castShadow=true;m.receiveShadow=true;parent.add(m);return m}
function box(w,h,d,material,x,y,z){return mesh(new THREE.BoxGeometry(w,h,d),material,x,y,z)}
function lineBranch(points,radius,material){const curve=new THREE.CatmullRomCurve3(points.map(p=>new THREE.Vector3(...p)));const geo=new THREE.TubeGeometry(curve,28,radius,9,false);const pos=geo.attributes.position;for(let i=0;i<=28;i++){const center=curve.getPointAt(i/28),taper=1-.78*(i/28);for(let j=0;j<=9;j++){const n=i*10+j;const v=new THREE.Vector3().fromBufferAttribute(pos,n).sub(center).multiplyScalar(taper).add(center);pos.setXYZ(n,v.x,v.y,v.z)}}geo.computeVertexNormals();return mesh(geo,material)}
let seed=7307;const random=()=>{seed=(seed*16807)%2147483647;return(seed-1)/2147483646};
async function build(){
  renderer=new THREE.WebGLRenderer({antialias:true,alpha:false,powerPreference:'low-power'});
  renderer.setPixelRatio(Math.min(devicePixelRatio,mobile?1.5:1.7));renderer.setSize(innerWidth,innerHeight);renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.AgXToneMapping;renderer.toneMappingExposure=1.0;
  renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
  $('viewport').appendChild(renderer.domElement);renderer.domElement.setAttribute('aria-hidden','true');renderer.domElement.addEventListener('webglcontextlost',event=>{event.preventDefault();fail('WebGL context lost')});
  scene=new THREE.Scene();scene.background=new THREE.Color('#dcdfd5');scene.fog=new THREE.FogExp2('#cdd5cd',.0025);
  camera=new THREE.PerspectiveCamera(mobile?53:43,innerWidth/innerHeight,.05,180);
  const loader=new THREE.TextureLoader();const files=['cushion-top.png','white_plaster_rough_01-normal.jpg','bark_brown_01-color.jpg','bark_brown_01-normal.jpg','cinematic-lake-20261015.png','deck-color.jpg','deck-normal.jpg','deck-roughness.jpg','moss-color.jpg','moss-normal.jpg','moss-roughness.jpg'];
  const textures=await Promise.all(files.map(f=>loader.loadAsync('./assets/'+f)));
  const [straw,wallN,barkTex,barkN,panorama,deckColor,deckNormal,deckRough,mossColor,mossNormal,mossRough]=textures;
  for(const t of [straw,barkTex,panorama,deckColor,mossColor])t.colorSpace=THREE.SRGBColorSpace;
  for(const t of textures)t.anisotropy=Math.min(8,renderer.capabilities.getMaxAnisotropy());
  for(const t of [wallN,barkTex,barkN])t.wrapS=t.wrapT=THREE.RepeatWrapping;
  wallN.repeat.set(2,2);barkTex.repeat.set(1,2);barkN.repeat.set(1,2);
  const hemi=new THREE.HemisphereLight('#f1f4ed','#807965',1.5);scene.add(hemi);
  const sun=new THREE.DirectionalLight('#fff0d8',3.0);sun.position.set(-6,8,-5);sun.castShadow=true;sun.shadow.mapSize.set(mobile?1024:2048,mobile?1024:2048);sun.shadow.camera.left=-9;sun.shadow.camera.right=9;sun.shadow.camera.top=9;sun.shadow.camera.bottom=-9;sun.shadow.normalBias=.012;sun.shadow.bias=-.0001;sun.shadow.radius=3;scene.add(sun);
  const fill=new THREE.DirectionalLight('#e7eff0',.65);fill.position.set(1,3,7);scene.add(fill);
  // A sharp forward panorama uses its pixels in the visible view, with matching reflections.
  scene.background=null;const landscape=cinematicBackdrop(THREE,panorama);scene.add(landscape);
  const envScene=new THREE.Scene();envScene.add(landscape.clone(true));const pmrem=new THREE.PMREMGenerator(renderer);scene.environment=pmrem.fromScene(envScene,.025,.1,180).texture;scene.environmentIntensity=.4;pmrem.dispose();
  for(const t of [deckColor,deckNormal,deckRough,mossColor,mossNormal,mossRough]){t.wrapS=t.wrapT=THREE.RepeatWrapping;}
  const deckMat=new THREE.MeshStandardMaterial({map:deckColor,normalMap:deckNormal,roughnessMap:deckRough,normalScale:new THREE.Vector2(.38,.38),color:'#dfcdb0',roughness:.92});
  const edgeMat=new THREE.MeshStandardMaterial({color:'#8d7d65',roughness:1});
  box(8,.19,7,edgeMat,0,-.165,3.5);
  // Real planks, narrow shadow gaps, and continuous metre-scaled UVs.
  for(let i=0;i<26;i++){
    const x=-3.875+i*.31,g=new THREE.BoxGeometry(.302,.075,7),pos=g.attributes.position,uv=g.attributes.uv;
    for(let v=0;v<pos.count;v++)uv.setXY(v,(pos.getX(v)+x)/3,(pos.getZ(v)+3.5)/3);
    mesh(g,deckMat,x,-.0375,3.5);
  }
  box(8,.14,.13,deckMat,0,-.09,.03);
  const timber=new THREE.MeshStandardMaterial({map:deckColor,normalMap:deckNormal,normalScale:new THREE.Vector2(.22,.22),color:'#e9cfa6',roughness:.92});
  porch=new THREE.Group();scene.add(porch);
  const plinth=new THREE.MeshStandardMaterial({color:'#797b70',roughness:.98});
  timberFrame(THREE,{parent:porch,timber,stone:plinth,width:8,height:3.65,depth:6,posts:[-1.12,3.65],z:-.24});
  // Cushion: lathed volume with a domed radial-mapped woven top.
  const strawMat=new THREE.MeshStandardMaterial({map:straw,color:'#b8aa8e',roughness:1});
  const sideWeave=straw.clone();sideWeave.wrapS=sideWeave.wrapT=THREE.RepeatWrapping;sideWeave.repeat.set(1,1);sideWeave.offset.set(0,0);
  const sideMat=new THREE.MeshStandardMaterial({map:sideWeave,color:'#a89779',roughness:1});
  sideMat.customProgramCacheKey=()=> 'woven-seat-v1';
  sideMat.onBeforeCompile=shader=>{shader.fragmentShader=shader.fragmentShader.replace('#include <map_fragment>', '#ifdef USE_MAP\n vec4 sampledDiffuseColor=texture2D(map,vec2(.13+fract(vMapUv.x*6.0)*.74,.42+vMapUv.y*.15)); diffuseColor*=sampledDiffuseColor;\n #endif')};
  cushion=new THREE.Group();cushion.position.set(mobile?0:.72,0,mobile?1.03:.84);scene.add(cushion);
  const profile=[[0,0],[.44,.008],[.51,.035],[.55,.095],[.552,.145],[.524,.2],[.47,.225],[0,.235]].map(([x,y])=>new THREE.Vector2(x,y));
  const cushionSide=mesh(new THREE.LatheGeometry(profile,96),sideMat,0,0,0,cushion);
  const topGeo=new THREE.CircleGeometry(.514,96,0,Math.PI*2);const topPos=topGeo.attributes.position;
  for(let i=0;i<topPos.count;i++){const x=topPos.getX(i),y=topPos.getY(i),r=Math.hypot(x,y);topPos.setZ(i,.012*(1-r/.514));}topGeo.computeVertexNormals();const topUv=topGeo.attributes.uv;for(let i=0;i<topUv.count;i++)topUv.setXY(i,.5+(topUv.getX(i)-.5)*.91,.5+(topUv.getY(i)-.5)*.91);
  const top=mesh(topGeo,strawMat,0,.224,0,cushion);top.rotation.x=-Math.PI/2;
  for(let i=0;i<7;i++){const ring=mesh(new THREE.TorusGeometry(.545-Math.abs(i-3)*.004,.005,4,96),new THREE.MeshStandardMaterial({color:i%2?'#c4aa73':'#a68e60',roughness:1}),0,.045+i*.021,0,cushion);ring.rotation.x=Math.PI/2;}

  // A real zafu on the rush mat, with a single open-circle stitched motif.
  const zafuMat=new THREE.MeshStandardMaterial({color:'#777964',normalMap:wallN,normalScale:new THREE.Vector2(.48,.48),roughness:1});
  const zafuProfile=[[0,0],[.30,0],[.35,.028],[.36,.10],[.33,.15],[.23,.168],[0,.165]].map(([x,y])=>new THREE.Vector2(x,y));
  mesh(softSeat(THREE,new THREE.LatheGeometry(zafuProfile,80)),zafuMat,0,.225,0,cushion);
  const stitchMat=new THREE.MeshStandardMaterial({color:'#b9b79c',emissive:'#b2b591',emissiveIntensity:0,roughness:1});
  breathRing=mesh(new THREE.TorusGeometry(.175,.004,5,80,Math.PI*1.84),stitchMat,0,.395,0,cushion);breathRing.rotation.x=-Math.PI/2;breathRing.rotation.z=.35;

  // Rocks are irregular volumes, with subdued moss on the upper surfaces.
  const rockMat=new THREE.MeshStandardMaterial({map:mossColor,normalMap:mossNormal,roughnessMap:mossRough,normalScale:new THREE.Vector2(.6,.6),color:'#b8b9a8',roughness:1});
  rockMat.customProgramCacheKey=()=> 'quiet-rock-v1';
  rockMat.onBeforeCompile=shader=>{shader.fragmentShader=shader.fragmentShader.replace('#include <map_fragment>', '#include <map_fragment>\n diffuseColor.rgb=mix(diffuseColor.rgb,vec3(.25,.27,.22),.40);')};
  island=new THREE.Group();scene.add(island);const islandStart=scene.children.length;
  const rocks=[];
  for(let i=0;i<42;i++){
    const side=i<24?1:-1,x=side*(1.45+random()*2.4),z=-.38-random()*3.4,size=.20+random()*.48;
    const raw=new THREE.IcosahedronGeometry(1,4);raw.deleteAttribute('normal');raw.deleteAttribute('uv');const geo=mergeVertices(raw);raw.dispose();const p=geo.attributes.position;
    for(let j=0;j<p.count;j++){const v=new THREE.Vector3().fromBufferAttribute(p,j);const f=1+.12*Math.sin(v.x*4.5+v.y*3)+.07*Math.cos(v.z*6);p.setXYZ(j,v.x*f,v.y*f,v.z*f);}geo.computeVertexNormals();const uv=[];for(let j=0;j<p.count;j++)uv.push(p.getX(j)*.45+.5,p.getZ(j)*.45+.5);geo.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));
    const rock=mesh(geo,rockMat,x,.03+size*.2,z);rock.scale.set(size*1.3,size*.46,size);rock.rotation.set(random()*.4,random()*6,random()*.3);rocks.push(rock);
  }
  const bark=new THREE.MeshStandardMaterial({map:barkTex,normalMap:barkN,normalScale:new THREE.Vector2(.7,.7),color:'#9d9a87',roughness:1});
  const trunkPoints=[[2.95,.15,-3.5],[2.65,.8,-3.45],[2.82,1.6,-3.4],[2.28,2.3,-3.35],[2.44,3.4,-3.45]];
  const trunkCurve=new THREE.CatmullRomCurve3(trunkPoints.map(p=>new THREE.Vector3(...p)));
  lineBranch(trunkPoints,.14,bark);
  const onTrunk=y=>{let closest=trunkCurve.getPoint(0);for(let i=1;i<=100;i++){const q=trunkCurve.getPoint(i/100);if(Math.abs(q.y-y)<Math.abs(closest.y-y))closest=q;}return closest.toArray();};
  const clusters=[{p:[1.8,1.72,-3.4],s:.72},{p:[3.35,2.15,-3.65],s:.91},{p:[1.60,2.76,-3.50],s:.76},{p:[2.42,3.35,-3.5],s:.73},{p:[3.4,1.1,-3.35],s:.66},{p:[2.15,2.20,-3.15],s:.64}];
  for(const {p,s}of clusters){const start=onTrunk(Math.max(.7,p[1]-.5));lineBranch([start,[(start[0]+p[0])/2,p[1]-.12,p[2]],p],.038,bark);
    for(let j=0;j<4;j++){const a=j*1.7;lineBranch([p,[p[0]+Math.cos(a)*s*.4,p[1]+.05,p[2]+Math.sin(a)*s*.3],[p[0]+Math.cos(a)*s*.8,p[1]+.08,p[2]+Math.sin(a)*s*.5]],.011,bark);}
  }
  pineNeedles(THREE,{parent:scene,clusters,random,count:3800});
  const islandObjects=scene.children.slice(islandStart);for(const object of islandObjects)island.add(object);
  // Reflect actual geometry and the scenic layer, with small slow surface ripples.
  const waterShader=quietLakeShader(THREE,Reflector);
  water=new Reflector(new THREE.PlaneGeometry(300,260),{clipBias:.004,textureWidth:mobile?512:1024,textureHeight:mobile?512:1024,multisample:0,shader:waterShader});water.rotation.x=-Math.PI/2;water.position.set(0,-.22,-130);scene.add(water);
  ready=true;notifyParent('ready');$('sit').disabled=false;translate();document.body.classList.add('ready');document.body.classList.toggle('still',still);
  wirePointer();resize();requestFrame();
  // Diagnostics are read-only and used solely to verify this standalone sample.
  Object.defineProperty(window,'__CIJIAN_3D__',{get:()=>({ready,seated,still,lang,yaw,pitch,renderer:'WebGL2',width:innerWidth,height:innerHeight,camera:camera.position.toArray(),calls:renderer.info.render.calls,triangles:renderer.info.render.triangles,textures:renderer.info.memory.textures,geometries:renderer.info.memory.geometries,revision:'cinematic-20261015'})});
}
function updateCamera(now){
  const v=viewpoint();
  if(transition){const t=Math.min(1,(now-transition.start)/1800),ease=t*t*(3-2*t);position.lerpVectors(transition.fromP,transition.toP,ease);target.lerpVectors(transition.fromT,transition.toT,ease);if(t===1)transition=null;}
  else{position.copy(v.pos);target.copy(v.look);}
  camera.position.copy(position);const look=target.clone();look.x+=Math.tan(yaw)*12;look.y+=Math.tan(pitch)*12;camera.lookAt(look);
}
function render(now){frame=0;if(document.hidden||!ready)return;if(now-lastFrame<32&&!still){requestFrame();return}lastFrame=now;updateCamera(now);if(!still)water.material.uniforms.time.value=clock.getElapsedTime();if(breathRing)breathRing.material.emissiveIntensity=still?.06:.08+.045*Math.sin(clock.getElapsedTime()*.8);renderer.render(scene,camera);projectCushion();if(!still||transition)requestFrame();}
function requestFrame(){if(!frame&&ready&&!document.hidden)frame=requestAnimationFrame(render)}
function resize(){if(!renderer)return;mobile=innerWidth<700;camera.aspect=innerWidth/innerHeight;camera.fov=mobile?53:43;camera.updateProjectionMatrix();renderer.setPixelRatio(Math.min(devicePixelRatio,mobile?1.5:1.7));renderer.setSize(innerWidth,innerHeight);cushion.position.set(mobile?0:.72,0,mobile?1.03:.84);cushion.scale.setScalar(1);porch.position.x=mobile?.18:0;island.scale.setScalar(1);island.position.set(0,-.22,0);transition=null;requestFrame()}
window.addEventListener('resize',resize);document.addEventListener('visibilitychange',()=>{if(document.hidden){cancelAnimationFrame(frame);frame=0;clock.stop()}else{clock.start();requestFrame()}});
function wirePointer(){
  const el=$('garden');
  el.addEventListener('pointerdown',e=>{if(e.button!==0)return;drag={id:e.pointerId,x:e.clientX,y:e.clientY,yaw,pitch,moved:false};el.setPointerCapture(e.pointerId);el.classList.add('dragging')});
  el.addEventListener('pointermove',e=>{if(!drag||e.pointerId!==drag.id)return;const dx=e.clientX-drag.x,dy=e.clientY-drag.y;if(Math.hypot(dx,dy)>5)drag.moved=true;yaw=THREE.MathUtils.clamp(drag.yaw-dx*.00055,-.105,.105);pitch=THREE.MathUtils.clamp(drag.pitch+dy*.0004,-.05,.05);requestFrame()});
  el.addEventListener('pointerup',e=>{if(!drag||e.pointerId!==drag.id)return;const click=!drag.moved;drag=null;el.classList.remove('dragging');el.releasePointerCapture(e.pointerId);if(click&&!seated){raycaster.setFromCamera(new THREE.Vector2(e.clientX/innerWidth*2-1,-e.clientY/innerHeight*2+1),camera);if(raycaster.intersectObject(cushion,true).length)sit(true)}});
  el.addEventListener('pointercancel',()=>{drag=null;el.classList.remove('dragging')});
}

let lastCushionProjection='';
function projectCushion(){
  if(!embedded||seated||!cushion)return;
  const point=cushion.localToWorld(new THREE.Vector3(0,.42,0)).project(camera);
  const x=(point.x+1)/2,y=(1-point.y)/2,key=x.toFixed(3)+':'+y.toFixed(3);
  if(key!==lastCushionProjection){lastCushionProjection=key;notifyParent('cushion',{x,y});}
}
window.addEventListener('message',event=>{
  if(!embedded||event.source!==parent||event.origin!==location.origin||event.data?.source!=='cijian-parent')return;
  const data=event.data;
  if(data.action==='sit')sit(Boolean(data.value));
  if(data.action==='settings'){lang=data.lang==='en'?'en':'zh';setStill(Boolean(data.reduce)||media.matches);translate();}
});

translate();build().catch(fail);
