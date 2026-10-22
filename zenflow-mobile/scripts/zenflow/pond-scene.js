import * as THREE from 'three';
import { mergeVertices } from './vendor/BufferGeometryUtils.js';
import { createPondClock } from './pond-state';
import { PondAudio } from './pond-audio';
import { matchTrail, createPointerGesture, pickFish } from './pond-follow';

const $ = id => document.getElementById(id);
const media = matchMedia('(prefers-reduced-motion: reduce)');
const audio = new PondAudio(window.AudioContext || window.webkitAudioContext, async()=>Object.fromEntries(Object.entries(window.InvellePondAudio).map(([key,value])=>[key,Uint8Array.from(atob(value),c=>c.charCodeAt(0)).buffer])));
let audioBusy=false, soundPreset='drop';
const clock = createPondClock();
const gesture = createPointerGesture();
const state = { lang:'zh', active:true, paused:false, nativeReduce:false, still:false, surprises:true, failed:false };
const words = {
  zh:{followInvite:'轻点一条鱼，跟着它慢慢划水。',followReady:'跟着鱼前方的光圈，慢慢滑动。',followMoving:'顺着鱼游动的方向，慢慢滑动；松手即可。',followSound:'开启跟随水声',followEnd:'这一段，就到这里。',soundTitle:'听见安静',soundIntro:'选一种水声，也给安静留一点位置。',dropName:'轻拨水面',dropHint:'一点轻柔水声，缓缓散开。',lapName:'水波荡漾',lapHint:'听水轻轻拍岸，缓缓流动。',play:'开启声音',stop:'关闭声音',chooseSound:'选择水庭声音',back:'返回首页',title:'解压一会儿',settings:'设置',loading:'正在走近水边…',failed:'水庭暂时无法打开，请重试。',retry:'重新打开',hint:'轻轻拨水，也可以只是看看。',volume:'水声音量',surprises:'偶遇小惊喜',still:'静止画面',soundHint:'声音由你开启，暂停或离开时停止。',done:'好了',on:'声音已开',off:'声音已关',pause:'暂停',resume:'继续',quiet:'静静看看',audioError:'暂时无法播放声音，可以继续静静看鱼。',water:'浅水庭院。轻点一条鱼，沿鱼前方的光圈滑动，松手结束；也可轻触拨水，按空格泛起涟漪。',options:'水庭设置'},
  en:{followInvite:'Tap a fish and follow the glowing ring.',followReady:'Glide toward the glowing ring ahead of the fish.',followMoving:'Move in the fish’s direction. Lift to let go.',followSound:'Turn on water sounds',followEnd:'Let this little journey end here.',soundTitle:'A sound to rest with',soundIntro:'Choose a water sound. Leave a little room for silence.',dropName:'Soft ripples',dropHint:'A little water moving, gently fading away.',lapName:'Gentle water',lapHint:'Small waves softly lapping the shore.',play:'Play this sound',stop:'Turn sound off',chooseSound:'Choose a water sound',back:'Home',title:'Unwind a while',settings:'Settings',loading:'Approaching the water…',failed:'The pond could not open. Please try again.',retry:'Try again',hint:'Stir the water, or simply watch.',volume:'Water volume',surprises:'Little surprises',still:'Still view',soundHint:'Sound starts with you. Pausing or leaving turns it off.',done:'Done',on:'Sound on',off:'Sound off',pause:'Pause',resume:'Continue',quiet:'Still view',audioError:'Sound is unavailable. You can still enjoy the pond.',water:'Shallow water garden. Tap a fish and glide toward the glowing ring ahead; release to finish. Touch to stir, or press Space for a ripple.',options:'Pond settings'},
};
const send = (type, extra={}) => window.ReactNativeWebView?.postMessage(JSON.stringify({type,...extra}));
const reduced = () => media.matches || state.nativeReduce || state.still;
const running = () => state.active && !document.hidden && !state.paused && !state.failed;
let renderer, scene, camera, water, floor, frame=0, previous=null, time=0, lastRender=0, ready=false, disposed=false;
let pointer=null, pointerSince=0, lastTouch=-10, eventTime=-100, togetherUntil=-1, leafBorn=-1;
const fish=[], ripples=Array.from({length:3},()=>new THREE.Vector4(0,0,-100,0));
let rippleIndex=0, leaf, splash, banks, bankLayout=[];
let follow=null,followGlow=0,followNoticeUntil=-1;
function followWords(){
  $('invitation').textContent=words[state.lang][follow?(follow.dragging?'followMoving':'followReady'):followNoticeUntil>time?'followEnd':reduced()?'hint':'followInvite'];
  document.body.classList.toggle('following',!!follow||followNoticeUntil>time);
  const offerSound=!!follow&&!audio.enabled;
  $('follow-sound').hidden=!offerSound;$('follow-sound').disabled=audioBusy;
  $('follow-sound').textContent=audioBusy?'…':words[state.lang].followSound;
  document.body.classList.toggle('offer-follow-sound',offerSound);
}
function endFollow(notice=false){follow=null;followGlow=0;followNoticeUntil=notice?time+4:-1;followWords();}
function selectFish(index){const f=fish[index];if(f.trail.length<50){f.trail=Array.from({length:71},(_,i)=>({...positionFish(f,f.swimTime-(70-i)*.1),t:time-(70-i)*.1}));}follow={index,started:time,dragging:false,lastPoint:null};followNoticeUntil=-1;$('follow-guide').style.opacity='1';followWords();}

const raycaster=new THREE.Raycaster(), plane=new THREE.Plane(new THREE.Vector3(0,1,0),0), hit=new THREE.Vector3();
function translate() {
  const w=words[state.lang]; document.documentElement.lang=state.lang==='zh'?'zh-CN':'en'; document.title=w.title;
  document.querySelectorAll('[data-word]').forEach(node=>{node.textContent=w[node.dataset.word];});
  $('sound-label').textContent=audio.enabled?w[soundPreset==='drop'?'dropName':'lapName']:w.off; $('sound').setAttribute('aria-label',w.chooseSound);
  $('sound-play').textContent=audioBusy?'…':audio.enabled?w.stop:w.play;$('sound-play').disabled=audioBusy||state.failed;$('sound-play').setAttribute('aria-pressed',String(audio.enabled));
  document.querySelectorAll('[data-preset]').forEach(button=>{button.setAttribute('aria-pressed',String(button.dataset.preset===soundPreset));button.disabled=audioBusy;});
  $('pause').textContent=state.paused?w.resume:reduced()?w.quiet:w.pause;
  $('pause').setAttribute('aria-pressed',String(state.paused));
  $('water').setAttribute('aria-label',w.water); $('options').setAttribute('aria-label',w.options);
  $('pond').setAttribute('aria-label',w.title);
  followWords();
  $('still').checked=reduced(); $('still').disabled=media.matches||state.nativeReduce;
}
function stopSound(){audio.stop();translate();}
function clearInteraction(){const id=gesture.cancel();if(id!==null&&$('water').hasPointerCapture(id))$('water').releasePointerCapture(id);endFollow();pointer=null; eventTime=-100; togetherUntil=-1; ripples.forEach(r=>r.z=-100); $('touch-feedback').replaceChildren(); if(splash)splash.visible=false;}
function lifecycle(){
  previous=null; clock.step(performance.now(),false);
  if(!running()){clearInteraction();stopSound();}
  if(reduced())clearInteraction();$('follow-guide').style.opacity=state.active&&!document.hidden&&!state.failed?'1':'0';document.body.classList.toggle('motionless',reduced());
  if(frame)cancelAnimationFrame(frame); frame=0;
  if(ready && !disposed && state.active && !document.hidden){draw();if(running()&&!reduced())frame=requestAnimationFrame(tick);}
  translate();
}
window.pondNative=data=>{
  if(data.action==='background'){state.active=false;lifecycle();return;}
  if(data.action==='settings'){
    state.lang=data.lang==='en'?'en':'zh'; state.nativeReduce=!!data.reduce;
    state.active=data.active!==false; lifecycle();
  }
};
$('back').onclick=()=>{state.active=false;lifecycle();send('exit');};
$('pause').onclick=()=>{state.paused=!state.paused;lifecycle();};
async function startSound(){
  if(!running()||audioBusy)return;
  audioBusy=true;$('status').textContent='';translate();
  try{await audio.enable(soundPreset);if(!running())audio.stop();}
  catch{audio.stop();$('status').textContent=words[state.lang].audioError;}
  finally{audioBusy=false;translate();}
}
$('follow-sound').onclick=()=>void startSound();
$('sound').onclick=()=>{endFollow();$('sound-dialog').showModal();translate();};
$('sound-close').onclick=()=>{$('sound-dialog').close();$('sound').focus();};
$('sound-play').onclick=()=>{if(audio.enabled)stopSound();else void startSound();};
document.querySelectorAll('[data-preset]').forEach(button=>{button.onclick=()=>{
  if(audioBusy)return;const wasEnabled=audio.enabled;soundPreset=button.dataset.preset;
  stopSound();if(wasEnabled)void startSound();else translate();
};});
$('volume').oninput=e=>audio.setVolume(Number(e.target.value));
$('surprises').onchange=e=>{state.surprises=e.target.checked;if(!state.surprises){clearInteraction();leafBorn=-1;}draw();};
$('still').onchange=e=>{state.still=e.target.checked;lifecycle();};
function options(open){if(open)endFollow();$('options').hidden=!open;$('settings').setAttribute('aria-expanded',String(open));if(open)$('surprises').focus();else $('settings').focus();}
$('settings').onclick=()=>options($('options').hidden); $('close-options').onclick=()=>options(false);
$('retry').onclick=()=>location.reload();
document.addEventListener('keydown',e=>{if(e.key==='Escape'){if($('sound-dialog').open){e.preventDefault();$('sound-dialog').close();$('sound').focus();return;}if(!$('options').hidden)options(false);else $('back').click();}});
media.addEventListener('change',lifecycle);
document.addEventListener('visibilitychange',lifecycle);
window.addEventListener('pagehide',()=>{disposed=true;lifecycle();audio.dispose();renderer?.dispose();});

function material(color, options={}){return new THREE.MeshStandardMaterial({color,roughness:.85,...options});}
function mesh(geometry, mat, x=0,y=0,z=0, parent=scene){const m=new THREE.Mesh(geometry,mat);m.position.set(x,y,z);parent.add(m);return m;}
let edgePlants;
function buildFish(atlas, index, phase){
  // Curved, lit relief retains the approved silhouette and scale detail.
  // Raised shoulders, a tapered spine and flexible fins respond to scene light.
  const texture=atlas.clone();texture.repeat.set(.5,1);texture.offset.set(index*.5,0);texture.needsUpdate=true;
  const group=new THREE.Group();group.scale.setScalar(1.2);scene.add(group);
  const geometry=new THREE.PlaneGeometry(1.41,1.88,36,72);
  const mat=new THREE.MeshStandardMaterial({map:texture,bumpMap:texture,bumpScale:.008,transparent:true,depthWrite:false,alphaTest:.025,side:THREE.DoubleSide,color:'#e0e8d9',roughness:.48,metalness:.02});
  const body=mesh(geometry,mat,0,0,0,group);body.rotation.x=-Math.PI/2;body.renderOrder=2;
  const shadow=mesh(geometry.clone(),new THREE.MeshBasicMaterial({map:texture,color:'#1b3329',transparent:true,opacity:.11,depthWrite:false,toneMapped:false}),0,-.52,0);
  shadow.rotation.x=-Math.PI/2;shadow.scale.setScalar(1.26);shadow.renderOrder=1;
  const eyeMat=material('#172820',{roughness:.20});for(const side of [-1,1]){const eye=mesh(new THREE.SphereGeometry(.018,10,8),eyeMat,side*.092,.071,-.69,group);eye.scale.set(1,.65,.8);}
  fish.push({group,body,shadow,phase,swimTime:0,trail:[],bindVertices:geometry.attributes.position.array.slice()});
}
function buildWater(){
  const uniforms={uTime:{value:0},uReveal:{value:0},uRipples:{value:ripples}};
  const mat=new THREE.ShaderMaterial({transparent:true,depthWrite:false,uniforms,
    vertexShader:`varying vec2 p; void main(){p=position.xy;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}`,
    fragmentShader:`precision highp float; varying vec2 p; uniform float uTime; uniform float uReveal; uniform vec4 uRipples[3];
    float ridge(float d,float width){return exp(-d*d/width);}
    void main(){
      vec2 q=p/1.12; float r=length(q); float R=1.73;
      // Complementary semicircles form the S, held nearly still rather than spun.
      float top=length(q-vec2(0.,R*.5));float bottom=length(q+vec2(0.,R*.5));
      float sd=q.x>0.?abs(top-R*.5):abs(bottom-R*.5);
      float s=ridge(sd,.00065);
      float outer=ridge(r-R,.0006)+.3*ridge(r-R-.18,.0006);
      float eyes=ridge(top-.18,.0003)+ridge(bottom-.18,.0003);
      float shimmer=.82+.18*sin(q.x*11.+q.y*7.+uTime*.38);
      float tide=.88+.12*sin(uTime*.22);float pattern=(s*.40+outer*.38+eyes*.32)*(.88+uReveal*.12)*tide*shimmer;
      float flow=sin(q.x*4.4+sin(q.y*3.2+uTime*.10))*sin(q.y*5.2-q.x+uTime*.13);
      float caustic=pow(max(0.,flow),12.)*.018;
      float touch=0.;
      for(int i=0;i<3;i++){float age=uTime-uRipples[i].z;float d=length(p-uRipples[i].xy);if(age>=0.&&age<2.8){float radius=.09+age*.44;float band=ridge(d-radius,.0018);touch+=band*(1.-age/2.8)*.36*uRipples[i].w;}}
      float light=pattern+touch+caustic;
      gl_FragColor=vec4(vec3(.95,.985,.88),light);
    }`});
  water=mesh(new THREE.PlaneGeometry(16,18),mat,0,0,0);water.rotation.x=-Math.PI/2;water.renderOrder=3;
}
async function buildBanks(){
  const loader=new THREE.TextureLoader();
  const [color,normal,rough]=await Promise.all(['color','normal','roughness'].map(k=>loader.loadAsync(window.InvellePondStone[k])));
  if(disposed){[color,normal,rough].forEach(t=>t.dispose());return;}
  color.colorSpace=THREE.SRGBColorSpace;
  const raw=new THREE.IcosahedronGeometry(1,3);raw.deleteAttribute('normal');raw.deleteAttribute('uv');
  const geometry=mergeVertices(raw);raw.dispose();const p=geometry.attributes.position,uv=[];
  for(let i=0;i<p.count;i++){const x=p.getX(i),y=p.getY(i),z=p.getZ(i);const d=1+.09*Math.sin(x*8+z*5)*Math.cos(y*7);p.setXYZ(i,x*d,y*d,z*d);uv.push(x*.48+.5,z*.48+.5);}
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));geometry.computeVertexNormals();
  const stoneMat=new THREE.MeshStandardMaterial({map:color,normalMap:normal,roughnessMap:rough,normalScale:new THREE.Vector2(.4,.4),color:'#e7e4d6',roughness:1});
  stoneMat.customProgramCacheKey=()=> 'pond-wet-stone-v2';
  stoneMat.onBeforeCompile=shader=>{shader.fragmentShader=shader.fragmentShader.replace('#include <map_fragment>','#include <map_fragment>\n diffuseColor.rgb=mix(diffuseColor.rgb,vec3(.27,.285,.235),.56);');};
  banks=new THREE.InstancedMesh(geometry,stoneMat,18);scene.add(banks);
  for(let i=0;i<18;i++){const corner=i<9?1:-1,j=i%9;bankLayout.push({corner,x:.87+(j%3)*.14,z:-.72-Math.floor(j/3)*.15,r:.28+(j%4)*.06,rotation:i*2.399});}
}
function buildEdgePlants(){
  edgePlants=new THREE.Group();scene.add(edgePlants);
  const leafMaterial=new THREE.MeshStandardMaterial({color:'#73836c',roughness:.77,side:THREE.DoubleSide,vertexColors:true});
  // A few cupped leaves with radial veins sit at the margin, leaving the fish path clear.
  for(let n=0;n<5;n++){
    const radius=[.31,.23,.18,.21,.15][n],geo=new THREE.BufferGeometry(),vertices=[],colors=[],indices=[];const rings=10,segments=64;
    for(let j=0;j<=rings;j++)for(let i=0;i<=segments;i++){
      const r=j/rings,a=.055+i/segments*(Math.PI*2-.11),edge=1+.028*Math.sin(a*7+n)+.016*Math.cos(a*11),rr=r*radius*edge;
      vertices.push(Math.cos(a)*rr,.035*r*r+.008*Math.cos(a*18)*r*r+.012*Math.sin(a+n)*r,Math.sin(a)*rr);
      const vein=Math.pow(Math.max(0,Math.cos(a*18)),18)*.085*r,c=new THREE.Color().setRGB(.74+vein-r*.08,.82+vein-r*.06,.65+vein-r*.06);colors.push(c.r,c.g,c.b);
      if(j<rings&&i<segments){const k=j*(segments+1)+i;indices.push(k,k+segments+1,k+1,k+1,k+segments+1,k+segments+2);}
    }
    geo.setAttribute('position',new THREE.Float32BufferAttribute(vertices,3));geo.setAttribute('color',new THREE.Float32BufferAttribute(colors,3));geo.setIndex(indices);geo.computeVertexNormals();
    const leaf=mesh(geo,leafMaterial,0,.018,0,edgePlants);leaf.userData={edge: n<3?-1:1,dx:[0,.30,-.19,0,-.21][n],dz:[0,.30,.48,0,-.30][n]};leaf.rotation.y=n*2.4;
  }
  // Slender bankside sedge is one instanced draw call.
  const bladeGeo=new THREE.PlaneGeometry(.028,.50,1,6);bladeGeo.translate(0,.25,0);const p=bladeGeo.attributes.position;
  for(let i=0;i<p.count;i++){const y=p.getY(i);p.setXYZ(i,p.getX(i)*(1-y/.52)+y*y*.5,y,-y*y*.4);}bladeGeo.computeVertexNormals();
  const sedge=new THREE.InstancedMesh(bladeGeo,material('#a2ad8b',{side:THREE.DoubleSide}),64),o=new THREE.Object3D();sedge.name='sedge';
  for(let i=0;i<64;i++){const a=i*2.399,r=.07+Math.sin(i*3.7)*.07;o.position.set(Math.cos(a)*r,0,Math.sin(a)*r);o.rotation.y=a;o.scale.setScalar(.6+(Math.sin(i*2.1)+1)*.35);o.updateMatrix();sedge.setMatrixAt(i,o.matrix);}sedge.userData={edge:1,dx:.15,dz:-.5};edgePlants.add(sedge);
}

function placeBanks(halfW,halfH){if(edgePlants)for(const child of edgePlants.children){const d=child.userData;child.position.x=d.edge*halfW*.82+d.dx;child.position.z=d.edge*halfH*.62+d.dz;}if(!banks)return;const o=new THREE.Object3D();bankLayout.forEach((b,i)=>{o.position.set(b.corner*b.x*halfW,-.29,b.corner*b.z*halfH*1.057);o.rotation.set(.12,b.rotation,.08);o.scale.set(b.r*1.35,b.r*.60,b.r);o.updateMatrix();banks.setMatrixAt(i,o.matrix);});banks.instanceMatrix.needsUpdate=true;banks.computeBoundingSphere();}
async function build(){
  renderer=new THREE.WebGLRenderer({canvas:$('water'),antialias:true,alpha:false,powerPreference:'low-power'});
  renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));renderer.setClearColor('#dfe4d5');renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=.95;
  scene=new THREE.Scene();scene.background=new THREE.Color('#dde2d3');scene.fog=new THREE.Fog('#e3e6d8',14,24);
  camera=new THREE.OrthographicCamera(-3,3,4,-4,.1,30);camera.position.set(0,10,3.4);camera.lookAt(0,0,0);
  scene.add(new THREE.HemisphereLight('#f1f5ed','#4d665c',1.4));const sun=new THREE.DirectionalLight('#fff9ec',1.4);sun.position.set(-4,8,-3);scene.add(sun);
  // A bundled environment plate provides the stone material; fish and water are real-time 3D.
  const texture=await new THREE.TextureLoader().loadAsync(window.InvellePondFloor);
  if(disposed){texture.dispose();return;}
  texture.colorSpace=THREE.SRGBColorSpace;
  floor=mesh(new THREE.PlaneGeometry(1,1),new THREE.MeshBasicMaterial({map:texture,toneMapped:false}),0,-.60,0);floor.rotation.x=-Math.PI/2;
  const atlas=await new THREE.TextureLoader().loadAsync(window.InvellePondKoi);atlas.colorSpace=THREE.SRGBColorSpace;
  if(disposed){atlas.dispose();return;}
  buildFish(atlas,0,0);buildFish(atlas,1,Math.PI);buildWater();await buildBanks();buildEdgePlants();
  leaf=mesh(new THREE.SphereGeometry(1,16,8),material('#a6ad70',{roughness:.65}),1.3,.045,-1.7);leaf.scale.set(.10,.022,.25);leaf.visible=false;
  splash=new THREE.Group();scene.add(splash);
  for(let i=0;i<10;i++){const drop=mesh(new THREE.SphereGeometry(.023,8,6),new THREE.MeshStandardMaterial({color:'#f0f4dc',transparent:true,opacity:.6,roughness:.12}),0,0,0,splash);drop.scale.y=1.5;}
  splash.visible=false;resize();ready=true;$('loading').hidden=true;send('ready');lifecycle();
}
function positionFish(f,t){const a=t*.095+f.phase;
  return new THREE.Vector3(Math.cos(a)*1.37+Math.sin(a*.73+f.phase)*.32,-.22+Math.sin(t*.21+f.phase)*.035,Math.sin(a*.87)*1.83+Math.cos(a*1.3+f.phase)*.23);
}
function tailPoint(f,t){const p=positionFish(f,t),next=positionFish(f,t+.1),direction=next.sub(p).normalize();return {x:p.x-direction.x*.82,z:p.z-direction.z*.82};}
function updateScene(){
  const approach=!follow && pointer && time-pointerSince>2 && state.surprises;
  for(const [i,f] of fish.entries()){
    const p=positionFish(f,f.swimTime),next=positionFish(f,f.swimTime+.1);
    if(approach && i===0){const blend=Math.min(.4,(time-pointerSince-2)*.06);p.lerp(new THREE.Vector3(pointer.x,-.22,pointer.z+.3),blend);next.lerp(new THREE.Vector3(pointer.x,-.22,pointer.z+.29),blend);}
    if(!f.trail.length||time-f.trail[f.trail.length-1].t>.08){f.trail.push({...positionFish(f,f.swimTime),t:time});while(f.trail.length&&time-f.trail[0].t>7)f.trail.shift();}
    f.group.position.copy(p);f.group.rotation.y=Math.atan2(-(next.x-p.x),-(next.z-p.z));
    const positions=f.body.geometry.attributes.position;
    for(let v=0;v<positions.count;v++){
      const x=f.bindVertices[v*3],y=f.bindVertices[v*3+1],u=(.94-y)/1.88;
      const sway=Math.sin(time*1.7+f.phase+u*4.2)*.14*Math.pow(u,1.7);
      const finLift=Math.pow(Math.abs(x)/.705,1.5)*Math.sin(time*1.5+u*3+f.phase)*.035;
      const along=THREE.MathUtils.clamp((y+.42)/1.32,0,1);
      const width=.055+.12*Math.pow(Math.sin(along*Math.PI),.5);
      const shoulders=Math.sqrt(Math.max(0,1-Math.pow((x-.025)/width,2)));
      const volume=.16*Math.pow(Math.sin(along*Math.PI),.55)*shoulders;
      positions.setXYZ(v,x+sway,y,volume+finLift);
    }
    positions.needsUpdate=true;f.body.geometry.computeVertexNormals();
    const shadowPositions=f.shadow.geometry.attributes.position;for(let v=0;v<positions.count;v++)shadowPositions.setXYZ(v,positions.getX(v),positions.getY(v),0);shadowPositions.needsUpdate=true;
    f.shadow.position.set(p.x+.11,-.53,p.z+.16);f.shadow.rotation.z=-f.group.rotation.y;

  }
  water.material.uniforms.uTime.value=time;
  water.material.uniforms.uReveal.value=reduced()?0:Math.max(0,1-(time-lastTouch)/5,time<togetherUntil?.75:0);
  if(leaf){leaf.visible=leafBorn>=0; if(leaf.visible){const age=time-leafBorn;leaf.position.set(Math.sin(age*.06)*1.25,.045,-1.6+Math.min(age*.014,3));leaf.rotation.y=age*.06;}}
  const age=time-eventTime;splash.visible=!reduced()&&age>=0&&age<1.6;
  if(splash.visible) splash.children.forEach((drop,i)=>{const a=i*Math.PI*2/10,t=age/1.6;drop.position.set(Math.cos(a)*t*.3,.02+Math.sin(t*Math.PI)*(.10+(i%3)*.035),-.865+Math.sin(a)*t*.3);drop.material.opacity=(1-t)*.6;});
}
function screenPoint(p){const v=new THREE.Vector3(p.x,0,p.z).project(camera);return {x:(v.x+1)*innerWidth/2,y:(1-v.y)*innerHeight/2};}
function fishTargets(){return fish.map(f=>{
  const center=f.group.position,angle=f.group.rotation.y;
  const forward=new THREE.Vector3(-Math.sin(angle),0,-Math.cos(angle));
  const head=screenPoint(center.clone().addScaledVector(forward,.95));
  const tail=screenPoint(center.clone().addScaledVector(forward,-1.05));
  const radius=Math.max(24,innerWidth/(camera.right-camera.left)*.42);
  return {head,tail,radius};
});}
function guideTrail(f){
  // Match the finger to the fish's actual path, including its current position,
  // rather than only to a wake that remains behind the fish's tail.
  return [...f.trail,...Array.from({length:12},(_,i)=>positionFish(f,f.swimTime+(i+1)*.4))];
}
function drawFollow(){
  const shown=ready&&state.active&&!state.failed&&!document.hidden;
  $('follow-guide').style.opacity=shown?'1':'0';if(!shown)return;
  if(follow&&time-follow.started>28)endFollow(true);
  const targets=fishTargets();
  fish.forEach((f,index)=>{
    const center=screenPoint(f.group.position),head=targets[index].head;
    const dx=head.x-center.x,dy=head.y-center.y,length=Math.hypot(dx,dy)||1;
    const pulse=(reduced()||state.paused) ? .5 :.5+.5*Math.sin(time*2.3+index*.8);
    const selected=follow?.index===index;
    const x=head.x+dx/length*29,y=head.y+dy/length*29;
    const marker=$(index===0?'follow-near':'follow-far');
    marker.setAttribute('transform',`translate(${x},${y}) scale(${(selected?1.12:1)+pulse*.08})`);
    marker.style.opacity=String(selected?.58+pulse*.40:.32+pulse*.30);
  });
}

function draw(){if(!renderer||state.failed||disposed)return;updateScene();renderer.render(scene,camera);drawFollow();if(!follow&&followNoticeUntil>=0&&time>followNoticeUntil){followNoticeUntil=-1;followWords();}}
function tick(now){
  frame=0;if(!running()||reduced()||disposed)return;
  if(now-lastRender<32){frame=requestAnimationFrame(tick);return;}lastRender=now;
  const dt=previous===null?0:Math.min((now-previous)/1000,1);previous=now;time+=dt;
  fish.forEach((f,i)=>{f.swimTime+=dt*(follow?.index===i ? .55 : 1);});followGlow*=Math.exp(-dt*1.3);
  const event=clock.step(now,true);
  if(event && state.surprises){
    if(event==='splash'){eventTime=time;addRipple(0,-.865,1);audio.drop(true);}
    if(event==='together'){togetherUntil=time+8;addRipple(0,0,.6);}
    if(event==='leaf')leafBorn=time;
  }
  draw();frame=requestAnimationFrame(tick);
}
function addRipple(x,z,strength=.6){ripples[rippleIndex++%ripples.length].set(x,-z,time,strength);}
let lastPointerAt=-Infinity;
function showTouch(x,y){
  const pulse=document.createElement('span');pulse.className='touch-pulse';pulse.style.left=x+'px';pulse.style.top=y+'px';
  pulse.innerHTML='<i></i>';
  const host=$('touch-feedback');while(host.childElementCount>=3)host.firstElementChild.remove();host.append(pulse);
  pulse.addEventListener('animationend',()=>pulse.remove(),{once:true});
}
function touch(e,force=false){
  if(!running()||!ready)return;
  const rect=$('water').getBoundingClientRect(),now=performance.now();
  const showRipple=force||now-lastPointerAt>=550;
  if(showRipple){lastPointerAt=now;if(reduced())showTouch(e.clientX-rect.left,e.clientY-rect.top);if(!follow)audio.drop();}
  document.body.classList.add('touched');
  if(reduced())return;
  raycaster.setFromCamera(new THREE.Vector2((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1),camera);
  if(!raycaster.ray.intersectPlane(plane,hit))return;
  if(!pointer || pointer.distanceTo(hit)>.18)pointerSince=time;
  if(force&&follow)follow.dragging=true;
  if(force&&!follow){
    const chosen=pickFish({x:e.clientX,y:e.clientY},fishTargets());
    if(chosen>=0)selectFish(chosen);
  }
  if(follow){
    if(!force){
      if(gesture.moved)follow.dragging=true;
      const amount=matchTrail(hit,follow.lastPoint,guideTrail(fish[follow.index]));
      followGlow=Math.max(followGlow,amount);
      if(gesture.moved)audio.follow(amount);
      followWords();
    }
    follow.lastPoint=hit.clone();
  }
  pointer=hit.clone();if(showRipple)addRipple(hit.x,hit.z,1);lastTouch=time;
  if(leafBorn>=0 && leaf.position.distanceTo(hit)<.6)leafBorn-=.2;
  draw();
}
$('water').addEventListener('pointerdown',e=>{
  if(!ready||!running()||!gesture.begin(e))return;
  try{$('water').setPointerCapture(e.pointerId);}catch{}
  touch(e,true);
});
$('water').addEventListener('pointermove',e=>{if(gesture.move(e))touch(e);});
for(const kind of ['pointerup','pointercancel','lostpointercapture'])$('water').addEventListener(kind,e=>{
  const result=gesture.end(e);if(!result)return;
  pointer=null;if(follow?.dragging||result.moved||result.cancelled)endFollow();
});
$('water').addEventListener('keydown',e=>{if((e.key===' '||e.key==='Enter')&&running()&&!reduced()){e.preventDefault();addRipple(0,0,1);lastTouch=time;audio.drop();document.body.classList.add('touched');draw();}});
function resize(){if(!renderer||!camera)return;const w=innerWidth,h=innerHeight,aspect=w/h;const halfW=aspect<.8?2.65:aspect<1.3?3.1:4.2;const halfH=halfW/aspect;camera.left=-halfW;camera.right=halfW;camera.top=halfH;camera.bottom=-halfH;camera.updateProjectionMatrix();renderer.setSize(w,h,false);
  if(floor){const height=halfH*2*1.057,width=halfW*2;floor.scale.set(width,height,1);floor.position.z=-.20;const imageAspect=2/3,viewAspect=width/height;const repeatX=Math.min(1,viewAspect/imageAspect),repeatY=Math.min(1,imageAspect/viewAspect);floor.material.map.repeat.set(repeatX,repeatY);floor.material.map.offset.set((1-repeatX)/2,(1-repeatY)/2);}
  placeBanks(halfW,halfH);draw();}
window.addEventListener('resize',()=>{clearInteraction();resize();});
$('water').addEventListener('webglcontextlost',e=>{e.preventDefault();fail('WEBGL_CONTEXT_LOST');});
function fail(error){state.failed=true;ready=false;lifecycle();$('loading').hidden=true;$('failed').hidden=false;$('sound').disabled=true;$('pause').disabled=true;send('failed',{reason:String(error?.message||error)});}
Object.defineProperty(window,'__INVELLE_POND__',{get:()=>({ready,paused:state.paused,active:state.active,reduced:reduced(),following:follow?.index??null,dragging:follow?.dragging??false,time,targets:camera?fishTargets():[],calls:renderer?.info.render.calls,triangles:renderer?.info.render.triangles,textures:renderer?.info.memory.textures,revision:'cinematic-20261015',geometries:renderer?.info.memory.geometries,pixelRatio:renderer?.getPixelRatio()})});
translate();build().catch(fail);
