"""Reuse Zenflow koi appearance/motion in a volume-backed 3D pond; add two quiet original cats and fitted roof rafters."""
import bpy,math,json,sys,random
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import camera
s=bpy.context.scene;out=R/'zen-space-p3-v033.blend'
if s.get('version')!='p3-v032' or out.exists():raise RuntimeError('Requires v032/new v033')
rng=random.Random(211033);C=bpy.data.collections.new('P6_POND_LIFE');s.collection.children.link(C);newclips=[]
def root(name):o=bpy.data.objects.new(name,None);C.objects.link(o);o.empty_display_type='PLAIN_AXES';o.empty_display_size=.1;return o

def mesh(name,vs,fs,mat,parent=None):
 me=bpy.data.meshes.new(name+'_mesh');me.from_pydata(vs,[],fs);me.update();o=bpy.data.objects.new(name,me);C.objects.link(o);me.materials.append(mat)
 for p in me.polygons:p.use_smooth=True
 if parent:o.parent=parent
 return o

def mat(name,col,rough=.5):
 m=bpy.data.materials.new(name);m.use_nodes=True;m.diffuse_color=(*col,1);p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(*col,1);p.inputs['Roughness'].default_value=rough;return m
# Correct existing timber to the new concave roof. Old convex rafters must not protrude through tiles.
retired=[]
for ob in s.objects:
 if ob.type=='MESH' and not ob.hide_render and ('RAFTER' in ob.name) and not ob.name.startswith('SM_P6_'):
  ob.hide_render=True;ob.hide_viewport=True;retired.append(ob.name)
rafter_count=0
for roof in [ob for ob in s.objects if ob.type=='MESH' and ob.data.name.endswith('_P6_CONCAVE_SHELL')]:
 pts=[roof.matrix_world@v.co for v in roof.data.vertices];rings=len(pts)//4;vs=[];fs=[]
 for side in range(4):
  width=(pts[(side+1)%4]-pts[side]).length;count=max(2,round(width/.56))
  for j in range(count):
   u=(j+.5)/count;path=[pts[i*4+side].lerp(pts[i*4+(side+1)%4],u)-Vector((0,0,.18)) for i in range(rings)];start=len(vs);n=8
   for k,p in enumerate(path):
    tangent=(path[min(k+1,len(path)-1)]-path[max(0,k-1)]).normalized();x=tangent.cross(Vector((0,0,1))).normalized();y=tangent.cross(x).normalized()
    for q in range(n):a=q*math.tau/n;vs.append(tuple(p+x*.052*math.cos(a)+y*.052*math.sin(a)))
   for k in range(len(path)-1):
    for q in range(n):a=start+k*n+q;b=start+k*n+(q+1)%n;fs.append((a,b,b+n,a+n))
   fs.extend([tuple(start+q for q in reversed(range(n))),tuple(start+(len(path)-1)*n+q for q in range(n))]);rafter_count+=1
 me=bpy.data.meshes.new(roof.name+'_FITTED_RAFTERS');me.from_pydata(vs,[],fs);me.update();me.materials.append(bpy.data.materials['P2_Cedar']);ob=bpy.data.objects.new('SM_P6_'+roof.name+'_UNDER_ROOF_RAFTERS',me);roof.users_collection[0].objects.link(ob)
 for p in me.polygons:p.use_smooth=len(p.vertices)==4
# A light, physically present mineral bed makes fish legible through the water.
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];bed=bpy.data.materials['P5_FINE_DRESSED_LIMESTONE'].copy();bed.name='P6_SUBMERGED_PALE_MINERAL_BED';p=next(n for n in bed.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Roughness'].default_value=.9;slot=len(terrain.data.materials);terrain.data.materials.append(bed)
for p in terrain.data.polygons:
 c=sum((terrain.data.vertices[i].co for i in p.vertices),Vector())/len(p.vertices)
 if -44<c.x<-16 and -15<c.y<25 and c.z<-.85:p.material_index=slot
water=s.objects['WATER_LOWER_LEVEL_-1.20']
if hasattr(water,'visible_shadow'):water.visible_shadow=False
wp=next(n for n in bpy.data.materials['P2_Water'].node_tree.nodes if n.type=='BSDF_PRINCIPLED');wp.inputs['Base Color'].default_value=(.56,.72,.65,1);wp.inputs['Transmission Weight'].default_value=.86;wp.inputs['Roughness'].default_value=.055
# Source atlas is copied without raster changes; colours are per-vertex tints on the 3D fish.
atlas=bpy.data.images.load(str(R/'assets/living/zenflow-koi/koi-atlas.webp'),check_existing=True);atlas.pack();bellymat=mat('P6_KOI_PEARL_BELLY',(.63,.67,.55),.34)
def koi_material(name,fins):
 m=mat(name,(1,1,1),.30 if not fins else .38);nt=m.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Specular IOR Level'].default_value=.46;p.inputs['Coat Weight'].default_value=.10;p.inputs['Coat Roughness'].default_value=.23;tx=nt.nodes.new('ShaderNodeTexImage');tx.image=atlas;vc=nt.nodes.new('ShaderNodeVertexColor');vc.layer_name='P6_KOI_COLOR';mix=nt.nodes.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=1;nt.links.new(tx.outputs['Color'],mix.inputs[1]);nt.links.new(vc.outputs['Color'],mix.inputs[2]);nt.links.new(mix.outputs[0],p.inputs['Base Color']);bum=nt.nodes.new('ShaderNodeBump');bum.inputs['Distance'].default_value=.0004;bum.inputs['Strength'].default_value=.16;nt.links.new(tx.outputs['Color'],bum.inputs['Height']);nt.links.new(bum.outputs[0],p.inputs['Normal'])
 if fins:nt.links.new(tx.outputs['Alpha'],p.inputs['Alpha']);m.surface_render_method='DITHERED';m.use_transparent_shadow=True;m.use_backface_culling=False
 return m
finmat=koi_material('P6_ZENFLOW_KOI_ATLAS_FINS',True);bodymat=koi_material('P6_ZENFLOW_KOI_ATLAS_BODY',False)
def colors(o,k,variant):
 me=o.data;uv=me.uv_layers.new(name='UVMap');col=me.color_attributes.new(name='P6_KOI_COLOR',type='BYTE_COLOR',domain='CORNER')
 for face in me.polygons:
  for li in face.loop_indices:
   v=me.vertices[me.loops[li].vertex_index].co;u=(v.x/(k*1.41)+.5);vv=.5-v.y/(k*1.88);uv.data[li].uv=(u*.5+(.5 if variant else 0),vv)
   if o.get('red_pattern',0):
    # Three broad Kohaku-style patches retain the atlas scale texture under red pigment.
    q=v.y/k;xx=v.x/k;seed=o['red_pattern'];g=max(math.exp(-((q+.51+seed*.035)/.18)**2-((xx-.025)/.16)**2),math.exp(-((q+.02)/.22)**2-((xx+.045)/.17)**2),math.exp(-((q-.30)/.17)**2-((xx-.01)/.10)**2));a=max(0,min(1,(g-.32)*3));base=(1,.78,.33) if seed==2 else (1,1,.97);red=(1,.075,.027);rgb=tuple(base[i]*(1-a)+red[i]*a for i in range(3));col.data[li].color=(*rgb,1)
   else:col.data[li].color=(1,1,1,1)

def wave(o,k,phase):
 o.shape_key_add(name='Basis');left=o.shape_key_add(name='TailLeft');right=o.shape_key_add(name='TailRight')
 for i,v in enumerate(o.data.vertices):
  u=max(0,min(1,(v.co.y/k+.94)/1.88));dx=.12*k*u**1.8*math.sin(u*4.2);dz=.02*k*(abs(v.co.x)/(k*.705))**1.5*math.sin(u*3)
  left.data[i].co.x+=dx;left.data[i].co.z+=dz;right.data[i].co.x-=dx;right.data[i].co.z-=dz
 for fr in range(1,50,4):
  a=math.sin((fr-1)*math.tau/48+phase);left.value=max(0,a);right.value=max(0,-a);left.keyframe_insert(data_path='value',frame=fr);right.keyframe_insert(data_path='value',frame=fr)
 a=o.data.shape_keys.animation_data.action;a.name='P6_'+o.name+'_TAIL_LOOP';a.use_fake_user=True
 for layer in a.layers:
  for st in layer.strips:
   for bag in st.channelbags:
    for f in bag.fcurves:f.modifiers.new('CYCLES')
 newclips.append({'name':a.name,'object':o.name,'target_kind':'SHAPE_KEYS','seconds':2,'loop':True})
fish=[]
configs=[('BLACK',0,0,.44,(-32,10),3.5,2.2,52),('PEARL',1,0,.45,(-33,1),2.1,1.7,48),('RED_WHITE_A',1,1,.43,(-24,9),2.2,3,56),('RED_GOLD',1,2,.40,(-36,14),2.5,1.8,46),('RED_WHITE_B',1,3,.42,(-37,-3),2.2,2.4,60)]
for idx,(label,variant,red,k,center,rx,ry,period) in enumerate(configs):
 rt=root('P6_KOI_'+label+'_ROOT');rt['living_role']='koi';rt['colour']='red' if red else label.lower();phase=idx*1.25
 # Raised shoulder and flexible atlas fins match the existing Zenflow fish geometry in metres.
 vs=[];fs=[];nx,ny=28,60
 for j in range(ny+1):
  y=(-.94+j/ny*1.88)*k;yp=-y/k;along=max(0,min(1,(yp+.42)/1.32));width=.055+.12*math.sqrt(max(0,math.sin(along*math.pi)));volume=.16*max(0,math.sin(along*math.pi))**.55
  for i in range(nx+1):
   x=(-.705+i/nx*1.41)*k;shoulder=math.sqrt(max(0,1-((x/k-.025)/width)**2));z=volume*shoulder*k+.0015;vs.append((x,y,z))
 for j in range(ny):
  for i in range(nx):a=j*(nx+1)+i;fs.append((a,a+1,a+nx+2,a+nx+1))
 fin=mesh('SM_P6_KOI_'+label+'_FINS',vs,fs,finmat,rt);fin['red_pattern']=red;colors(fin,k,variant);wave(fin,k,phase)
 # Closed torpedo body underneath the photographed relief; sides and belly are visible from low angles.
 vs=[];fs=[];sections=34;n=24
 for j in range(sections):
  t=j/(sections-1);y=(-.83+t*1.25)*k;yp=-y/k;along=max(0,min(1,(yp+.42)/1.32));width=(.055+.12*math.sqrt(max(0,math.sin(along*math.pi))))*k*.87;tip=min(1,t/.08,(1-t)/.06);width=max(.002,width*tip);height=.16*max(0,math.sin(along*math.pi))**.55*k*.96
  for i in range(n):a=i*math.tau/n;vs.append((.025*k+width*math.cos(a),y,(height if math.sin(a)>0 else .045*k)*math.sin(a)))
 for j in range(sections-1):
  for i in range(n):a=j*n+i;b=j*n+(i+1)%n;fs.append((a,a+n,b+n,b))
 fs.extend([tuple(reversed(range(n))),tuple((sections-1)*n+i for i in range(n))]);body=mesh('SM_P6_KOI_'+label+'_VOLUME',vs,fs,bodymat,rt);body['red_pattern']=red;colors(body,k,variant);wave(body,k,phase)
 # Gentle non-circular loop, inherited from the original phase-driven ellipse/secondary-harmonic motion.
 previous=None
 for fr in range(1,period*24+2,24):
  t=(fr-1)/24;a=math.tau*t/period+phase;x=center[0]+rx*math.cos(a)+.18*math.sin(2*a);y=center[1]+ry*math.sin(a)+.16*math.cos(3*a);dx=-rx*math.sin(a)+.36*math.cos(2*a);dy=ry*math.cos(a)-.48*math.sin(3*a);angle=math.atan2(dx,-dy)
  if previous is not None:
   while angle-previous>math.pi:angle-=math.tau
   while angle-previous<-math.pi:angle+=math.tau
  previous=angle;rt.location=(x,y,-1.43+.025*math.sin(a*2));rt.rotation_euler.z=angle;rt.keyframe_insert(data_path='location',frame=fr);rt.keyframe_insert(data_path='rotation_euler',frame=fr)
 action=rt.animation_data.action;action.name='P6_KOI_'+label+'_SWIM_LOOP';action.use_fake_user=True
 for layer in action.layers:
  for st in layer.strips:
   for bag in st.channelbags:
    for f in bag.fcurves:f.modifiers.new('CYCLES')
 newclips.append({'name':action.name,'object':rt.name,'seconds':period,'loop':True});fish.append({'root':rt.name,'label':label,'red':bool(red),'length_m':1.88*k,'period_s':period})
# Retire only the former single proxy fish, preserving its old action library for history.
for ob in s.objects:
 if ob.name.startswith(('SM_Z02_FISH_BODY','SM_Z02_FISH_TAIL')):ob.hide_render=True;ob.hide_viewport=True
# Append local original cat poses; no linked asset library or external dependency remains.
with bpy.data.libraries.load(str(R/'assets/living/cats-original-v001.blend'),link=False) as (src,dst):dst.collections=['P6_ORIGINAL_CATS']
catcol=dst.collections[0];s.collection.children.link(catcol);watch=s.objects['P6_CAT_WATCH_ROOT'];sleep=s.objects['P6_CAT_SLEEP_ROOT'];watch.location=(-14.85,12.1,.02);watch.rotation_euler.z=-math.pi/2;sleep.location=(-12.95,16.1,.02);sleep.rotation_euler.z=.2
# Fur colours lie on the anatomy, replacing a visibly separate bib surface.
bib=s.objects.get('SM_P6_CAT_WATCH_CHEST_BIB')
if bib:bib.hide_render=True;bib.hide_viewport=True
for ob in catcol.objects:
 if 'MUZZLE' in ob.name:ob.scale*=.74
 if 'NOSE' in ob.name:ob.scale*=.82
body=s.objects['SM_P6_CAT_WATCH_SCULPT'];me=body.data;vc=me.color_attributes.new(name='P6_COAT_COLOR',type='BYTE_COLOR',domain='CORNER')
for p in me.polygons:
 for li in p.loop_indices:
  v=body.matrix_basis@me.vertices[me.loops[li].vertex_index].co;mask=max(0,min(1,(1-(v.x/.060)**2-((v.z-.255)/.105)**2)*5)) if v.y<-.07 else 0
  if v.z<.05 and v.y<-.07:mask=1
  base=(.055,.065,.058);white=(.64,.61,.52);vc.data[li].color=(*(base[i]*(1-mask)+white[i]*mask for i in range(3)),1)
fm=body.data.materials[0].copy();fm.name='P6_CAT_WATCH_CONFORMING_COAT';nt=fm.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');cn=nt.nodes.new('ShaderNodeVertexColor');cn.layer_name='P6_COAT_COLOR';nt.links.new(cn.outputs['Color'],p.inputs['Base Color']);body.data.materials[0]=fm
# Source breathing is subtle and remains individually controllable.
for name in ['SM_P6_CAT_WATCH_SCULPT','SM_P6_CAT_SLEEP_SCULPT']:
 ob=s.objects[name];base=ob.scale.copy()
 for fr,mul in [(1,1),(49,1.010),(97,1),(145,1.008),(193,1)]:ob.scale=(base.x,base.y,base.z*mul);ob.keyframe_insert(data_path='scale',frame=fr)
 action=ob.animation_data.action;action.name='P6_'+name+'_BREATH_LOOP';action.use_fake_user=True
 for layer in action.layers:
  for st in layer.strips:
   for bag in st.channelbags:
    for f in bag.fcurves:f.modifiers.new('CYCLES')
 newclips.append({'name':action.name,'object':ob.name,'seconds':8,'loop':True})
# Dedicated inspection views show actual pond life and full cat geometry, not a composited animal plate.
CAM=bpy.data.collections['70_CAMERAS'];camera('CAM_POND_LIFE',(-36.3,8.2,2.4),(-31.8,10,-1.35),CAM,52);camera('CAM_CATS_VERANDA',(-15.9,9.8,.78),(-14.6,12.8,.23),CAM,48);camera('CAM_CAT_SLEEP',(-13.8,14.7,.62),(-12.95,16.1,.12),CAM,60)
s['version']='p3-v033';s['phase_status']='P6_LIVING_GARDEN_RENDER_REVIEW';s.render.fps=24;s.frame_start=1;s.frame_end=1441;s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'manifest/living-garden-clips.json').write_text(json.dumps({'source_version':'p3-v033','fps':24,'clips':newclips,'default_playing':True,'pause_policy':'all living clips can be stopped or time frozen; no mandatory surprises'},indent=2));(R/'evidence/pond-life-v033.json').write_text(json.dumps({'version':'p3-v033','source_koi':'assets/living/zenflow-koi/source.json','fish':fish,'red_koi_count':3,'cats':[{'root':watch.name,'pose':'watching water','position':list(watch.location)},{'root':sleep.name,'pose':'curled asleep','position':list(sleep.location)}],'roof_rafters_replaced':len(retired),'new_fitted_rafters':rafter_count,'water_shadow_note':'thin preview water does not block all direct shadow rays; underwater mineral bed is actual terrain material','new_inspection_cameras':['CAM_POND_LIFE','CAM_CATS_VERANDA','CAM_CAT_SLEEP']},indent=2));print('LIVING_GARDEN_SAVED',len(fish),'koi, 3 red; 2 cats;',len(newclips),'living clips; rafters',rafter_count,flush=True)
