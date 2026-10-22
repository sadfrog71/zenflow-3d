// Small shared geometry kit for the existing garden and reading room.
// The caller supplies its current Three.js instance; no second engine is loaded.
export function timberFrame(T, {parent, timber, stone, width=8, height=3.1, depth=6, posts=[-1.02,3.6], z=.1}) {
  const group=new T.Group();group.name='quiet-timber-frame';parent.add(group);
  const add=(geometry,material,x,y,zz)=>{const m=new T.Mesh(geometry,material);m.position.set(x,y,zz);m.castShadow=m.receiveShadow=true;group.add(m);return m;};
  const beam=(w,h,d,x,y,zz)=>add(new T.BoxGeometry(w,h,d),timber,x,y,zz);
  // Slightly swelling columns, stone shoes and bearing blocks give each joint weight.
  const column=new T.LatheGeometry([[.095,0],[.104,.12],[.11,.65],[.108,1.5],[.094,height-.36],[.10,height-.27]].map(p=>new T.Vector2(...p)),32);
  for(const x of posts){
    add(column,timber,x,.07,z);
    add(new T.CylinderGeometry(.137,.155,.075,32),stone,x,.037,z);
    beam(.30,.10,.32,x,height-.29,z);
    beam(.54,.10,.25,x,height-.19,z);
    beam(.29,.09,.62,x,height-.10,z);
  }
  beam(width,.18,.28,0,height,z);
  beam(width,.075,.43,0,height+.12,z);
  beam(width,.08,depth,0,height+.30,z+depth/2);
  const count=Math.ceil(width/.23),rafters=new T.InstancedMesh(new T.CylinderGeometry(.038,.038,depth,10),timber,count),dummy=new T.Object3D();
  for(let i=0;i<count;i++){dummy.position.set(-width/2+.12+i*.23,height+.22,z+depth/2-.12);dummy.rotation.x=Math.PI/2;dummy.updateMatrix();rafters.setMatrixAt(i,dummy.matrix);}
  rafters.castShadow=rafters.receiveShadow=true;group.add(rafters);
  return group;
}

export function softSeat(T, geometry) {
  // Compressed cloth, soft gathers around the seam, and a shallow centre depression.
  const p=geometry.attributes.position;
  for(let i=0;i<p.count;i++){
    const x=p.getX(i),y=p.getY(i),z=p.getZ(i),r=Math.hypot(x,z),a=Math.atan2(z,x);
    const gather=1+.018*Math.cos(a*24)*Math.sin(Math.min(1,y/.18)*Math.PI);
    p.setXYZ(i,x*gather,y-.018*Math.exp(-r*r/.022)*Math.max(0,y/.17),z*gather);
  }
  geometry.computeVertexNormals();return geometry;
}

export function pineNeedles(T, {parent, clusters, random, count=4200}) {
  // Curved needle fans replace intersecting photographic cards at the silhouette.
  const geo=new T.BufferGeometry();const vertices=[];
  for(let i=0;i<5;i++){
    const a=(i-2)*.30,dx=Math.sin(a)*.14,dz=Math.cos(a)*.18;
    vertices.push(-.004,0,0,.004,0,0,dx*.5+.007,.018,dz*.5, - .004,0,0,dx*.5+.007,.018,dz*.5,dx*.5-.007,.018,dz*.5, dx*.5-.007,.018,dz*.5,dx*.5+.007,.018,dz*.5,dx,.027,dz);
  }
  geo.setAttribute('position',new T.Float32BufferAttribute(vertices,3));geo.computeVertexNormals();
  const mat=new T.MeshStandardMaterial({color:'#9baa84',roughness:1,side:T.DoubleSide});
  const needles=new T.InstancedMesh(geo,mat,count),o=new T.Object3D();needles.name='pine-needle-fans';
  for(let i=0;i<count;i++){
    const c=clusters[i%clusters.length],a=random()*Math.PI*2,r=Math.sqrt(random())*c.s;
    o.position.set(c.p[0]+Math.cos(a)*r,c.p[1]+(random()-.5)*.24+.08*(1-r/c.s),c.p[2]+Math.sin(a)*r*.62);
    o.rotation.set((random()-.5)*1.3,random()*Math.PI*2,(random()-.5)*.7);o.scale.setScalar(.65+random()*.8);o.updateMatrix();needles.setMatrixAt(i,o.matrix);
    needles.setColorAt(i,new T.Color().setHSL(.22+random()*.035,.13+random()*.15,.30+random()*.17));
  }
  needles.castShadow=true;needles.receiveShadow=true;parent.add(needles);return needles;
}

export function cinematicBackdrop(T, texture) {
  // Concentrate image detail in the visible 86-degree arc instead of spreading
  // the same pixels around a complete 360-degree sphere. Horizon is at 62%.
  const group=new T.Group();group.name='cinematic-morning-landscape';
  const radius=110,angle=1.5,aspect=texture.image.width/texture.image.height;
  const height=radius*angle/aspect*.64;
  const geometry=new T.CylinderGeometry(radius,radius,height,160,1,true,Math.PI-angle/2,angle);
  const uv=geometry.attributes.uv;for(let i=0;i<uv.count;i++)uv.setX(i,1-uv.getX(i));
  const landscape=new T.Mesh(geometry,new T.ShaderMaterial({side:T.BackSide,toneMapped:false,uniforms:{map:{value:texture},zenith:{value:new T.Color('#7fabd2')},horizon:{value:new T.Color('#e4e6df')}},vertexShader:'varying vec2 vUv;varying vec3 world;void main(){vUv=uv;world=(modelMatrix*vec4(position,1.)).xyz;gl_Position=projectionMatrix*viewMatrix*vec4(world,1.);}',fragmentShader:`varying vec2 vUv;varying vec3 world;uniform sampler2D map;uniform vec3 zenith,horizon;
  void main(){vec3 photo=texture2D(map,vUv).rgb;float h=clamp(normalize(world).y,0.,1.);vec3 sky=mix(horizon,zenith,smoothstep(0.,.30,h));float edge=max(smoothstep(.77,1.,vUv.y),1.-smoothstep(0.,.045,min(vUv.x,1.-vUv.x)));gl_FragColor=vec4(mix(photo,sky,edge),1.);
  #include <colorspace_fragment>
  }`}));
  landscape.position.y=height*.12;group.add(landscape);
  const sky=new T.Mesh(new T.SphereGeometry(165,32,20),new T.ShaderMaterial({side:T.BackSide,depthWrite:false,uniforms:{zenith:{value:new T.Color('#7fabd2')},horizon:{value:new T.Color('#e4e6df')}},vertexShader:'varying vec3 p;void main(){p=position;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}',fragmentShader:'varying vec3 p;uniform vec3 zenith,horizon;void main(){float h=clamp(normalize(p).y,0.,1.);gl_FragColor=vec4(mix(horizon,zenith,smoothstep(0.,.30,h)),1.);\n#include <colorspace_fragment>\n}'}));
  group.add(sky);return group;
}

// One planar reflection shader for both existing spaces; geometric near objects
// and the distant landscape share a continuous water reflection.
export function quietLakeShader(T, Reflector) {
  const uniforms=T.UniformsUtils.clone(Reflector.ReflectorShader.uniforms);uniforms.time={value:0};
  return {uniforms,vertexShader:Reflector.ReflectorShader.vertexShader.replace('varying vec4 vUv;','varying vec4 vUv; varying vec3 surfaceWorld;').replace('vUv = textureMatrix','surfaceWorld=(modelMatrix*vec4(position,1.0)).xyz; vUv = textureMatrix'),fragmentShader:`
  uniform sampler2D tDiffuse;uniform float time;varying vec4 vUv;varying vec3 surfaceWorld;
  void main(){vec2 uv=vUv.xy/vUv.w;float w=sin(surfaceWorld.z*2.7+time*.5)*sin(surfaceWorld.x*1.4+time*.28);uv.x+=w*.0005;uv.y+=sin(surfaceWorld.z*4.1+time*.36)*.00028;vec3 reflected=texture2D(tDiffuse,uv).rgb;float facing=abs(normalize(cameraPosition-surfaceWorld).y);float fresnel=.30+.55*pow(1.-facing,3.);gl_FragColor=vec4(mix(vec3(.065,.12,.115),reflected,fresnel)+w*.0015,1.0);
  #include <tonemapping_fragment>
  #include <colorspace_fragment>
  }`};
}
