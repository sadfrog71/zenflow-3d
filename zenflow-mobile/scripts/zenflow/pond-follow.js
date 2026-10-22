// Match a forgiving corridor and its forward direction, never a score or diagnosis.
function matchTrail(point, previous, trail, tolerance=.55) {
  if(!previous||trail.length<2)return 0;
  const mx=point.x-previous.x,mz=point.z-previous.z,move=Math.hypot(mx,mz);
  if(move<.015)return 0;
  let nearest=Infinity,alignment=0;
  for(let i=1;i<trail.length;i++){
    const a=trail[i-1],b=trail[i],dx=b.x-a.x,dz=b.z-a.z,len2=dx*dx+dz*dz;
    if(len2<1e-6)continue;
    const t=Math.max(0,Math.min(1,((point.x-a.x)*dx+(point.z-a.z)*dz)/len2));
    const distance=Math.hypot(point.x-a.x-t*dx,point.z-a.z-t*dz);
    if(distance<nearest){nearest=distance;alignment=(mx*dx+mz*dz)/(move*Math.sqrt(len2));}
  }
  return Math.max(0,1-nearest/tolerance)*Math.max(0,alignment);
}
module.exports={matchTrail};

// One physical gesture owns the pond until release/cancel. Track motion before
// visual throttling so a quick swipe is never mistaken for a selection tap.
function createPointerGesture() {
  let active=null;
  return {
    get id(){return active?.id ?? null;},
    get moved(){return active?.moved ?? false;},
    begin(event){
      if(active||event.isPrimary===false||(event.button!==undefined&&event.button!==0))return false;
      active={id:event.pointerId,x:event.clientX,y:event.clientY,moved:false};return true;
    },
    move(event){
      if(!active||active.id!==event.pointerId)return false;
      active.moved ||= Math.hypot(event.clientX-active.x,event.clientY-active.y)>=6;
      return true;
    },
    end(event){
      if(!active||active.id!==event.pointerId)return null;
      const result={moved:active.moved,cancelled:event.type!=='pointerup'};
      active=null;return result;
    },
    cancel(){const id=active?.id ?? null;active=null;return id;},
  };
}
module.exports.createPointerGesture=createPointerGesture;

// Screen-space capsules include the head and long tail, with a finger-sized
// margin at every viewport size. Water outside a fish remains free to stir.
function pickFish(point, candidates) {
  let selected=-1, nearest=Infinity;
  candidates.forEach(({head,tail,radius},index)=>{
    const dx=head.x-tail.x,dy=head.y-tail.y,len2=dx*dx+dy*dy;
    const t=len2?Math.max(0,Math.min(1,((point.x-tail.x)*dx+(point.y-tail.y)*dy)/len2)):0;
    const distance=Math.hypot(point.x-tail.x-t*dx,point.y-tail.y-t*dy);
    if(distance<=radius&&distance<nearest){nearest=distance;selected=index;}
  });
  return selected;
}
module.exports.pickFish=pickFish;
