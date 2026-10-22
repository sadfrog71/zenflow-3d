"""Local material and construction polish of v028. Preserve shared world, cameras and interaction states."""
import bpy,sys,math,random,json
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'))
from phase2_materials import box,beam
s=bpy.context.scene;out=R/'zen-space-p3-v029.blend'
if s.get('version')!='p3-v028' or out.exists():raise RuntimeError('Requires v028 and unused v029 output')
rng=random.Random(211029);C=bpy.data.collections.new('P5_NEAR_VIEW_CRAFT_20261021');s.collection.children.link(C);stats={}
def mat(name,color,rough=.7):
 m=bpy.data.materials.new(name);m.use_nodes=True;p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(*color,1);p.inputs['Roughness'].default_value=rough;m.diffuse_color=(*color,1);return m
def maps(name,family,normal=.3):
 m=mat(name,(.5,.5,.5));nt=m.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED')
 for channel,socket in [('base','Base Color'),('rough','Roughness'),('normal',None)]:
  image=bpy.data.images.load(str(R/'assets/textures/detail-20261021'/f'{family}-{channel}.png'),check_existing=True);image.pack()
  if channel!='base':image.colorspace_settings.name='Non-Color'
  n=nt.nodes.new('ShaderNodeTexImage');n.image=image;n.label=family+' '+channel
  if channel=='normal':b=nt.nodes.new('ShaderNodeNormalMap');b.inputs['Strength'].default_value=normal;nt.links.new(n.outputs['Color'],b.inputs['Color']);nt.links.new(b.outputs['Normal'],p.inputs['Normal'])
  else:nt.links.new(n.outputs['Color'],p.inputs[socket])
 return m
def put(o,m,own=True):
 if own and o.data.users>1:o.data=o.data.copy()
 o.data.materials.clear();o.data.materials.append(m)
def flat_uv(o,scale=1.0,offset=None):
 me=o.data;uv=me.uv_layers.active or me.uv_layers.new();ox,oy=offset or (rng.random()*13,rng.random()*13)
 for f in me.polygons:
  dims=[i for i in range(3) if i!=max(range(3),key=lambda j:abs(f.normal[j]))]
  for li in f.loop_indices:
   v=me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(v[dims[0]]*scale+ox,v[dims[1]]*scale+oy)
def mesh_obj(name,vs,fs,m):
 me=bpy.data.meshes.new(name+'_mesh');me.from_pydata(vs,[],fs);me.update();o=bpy.data.objects.new(name,me);C.objects.link(o);me.materials.append(m);return o
def lathe_mesh(name,profile,n=64):
 vs=[(r*math.cos(i*math.tau/n),r*math.sin(i*math.tau/n),z) for r,z in profile for i in range(n)];fs=[]
 for j in range(len(profile)-1):
  for i in range(n):a=j*n+i;b=j*n+(i+1)%n;fs.append((a,b,b+n,a+n))
 fs.extend([tuple(reversed(range(n))),tuple((len(profile)-1)*n+i for i in range(n))]);me=bpy.data.meshes.new(name);me.from_pydata(vs,[],fs);me.update()
 for f in me.polygons:f.use_smooth=len(f.vertices)==4
 return me
def replace_world_mesh(o,me,center):
 inv=o.matrix_world.inverted()
 for v in me.vertices:v.co=inv@(v.co+center)
 o.data=me
 for mod in list(o.modifiers):
  if mod.type=='BEVEL':o.modifiers.remove(mod)
def bbox(o):
 vv=[o.matrix_world@v.co for v in o.data.vertices];lo=Vector(tuple(min(v[i] for v in vv) for i in range(3)));hi=Vector(tuple(max(v[i] for v in vv) for i in range(3)));return (lo+hi)*.5,lo,hi
stone=maps('P5_FINE_DRESSED_LIMESTONE','dressed-limestone',.36);stone['rain_roughness']=.46;grout=mat('P5_RECESSED_STONE_JOINT',(.16,.17,.145),.96)
paper=maps('P5_WASHI_FIBRE_LIGHT','washi',.14);p=next(n for n in paper.node_tree.nodes if n.type=='BSDF_PRINCIPLED');base=next(n for n in paper.node_tree.nodes if n.type=='TEX_IMAGE' and n.image.name.endswith('washi-base.png'));paper.node_tree.links.new(base.outputs['Color'],p.inputs['Emission Color']);p.inputs['Emission Strength'].default_value=.68;p.inputs['Subsurface Weight'].default_value=.08;p.inputs['Subsurface Radius'].default_value=(.12,.08,.03)
flax=maps('P5_FINE_FLAX_WEAVE','flax-weave',.55);glaze=maps('P5_IRON_SPECKLED_TEA_GLAZE','iron-speckled-glaze',.18);p=next(n for n in glaze.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Coat Weight'].default_value=.24;p.inputs['Coat Roughness'].default_value=.23
wood=bpy.data.materials['P2_Cedar'];dark=bpy.data.materials['P2_Darkwood'];thread=mat('P5_FLAX_EDGE_PIPING',(.29,.23,.15),.9)
# Dressed masonry has fine grain; retain cracked stone only on natural boulders and cliffs.
count=0
for o in list(s.objects):
 if o.type!='MESH' or o.hide_render:continue
 if ('PAVER' in o.name or o.name.startswith('SM_Z02_BRIDGE_STONE_PARAPET') or 'COLUMN_BASE' in o.name or o.name in ['Z01_VERANDA_STONE_SEAT','Z02_LOWER_BRIDGE_DECK','Z02_UPPER_BRIDGE_DECK']):
  put(o,stone);flat_uv(o,1.5);count+=1
  if 'PAVER' in o.name:
   # Low physical variation keeps all walking elevations, dimensions and clearances intact.
   o['finish']='fine dressed limestone, per-object UV offset'
stats['dressed_stone_objects']=count
# Individually fitted bridge paving: 6mm joints and shallow underside contacts over unchanged load-bearing deck.
count=0
for name in ['Z02_LOWER_BRIDGE_DECK','Z02_UPPER_BRIDGE_DECK']:
 deck=s.objects[name];v=[deck.matrix_world@p.co for p in deck.data.vertices]
 for i in range(24):
  a,b,c,d=v[i*4],v[i*4+1],v[(i+1)*4],v[(i+1)*4+1]
  for j in range(3):
   span=(c-a).length;t=.003/max(span,.01);w=.003/(b-a).length;y0=j/3+w;y1=(j+1)/3-w
   aa=a.lerp(c,t);cc=a.lerp(c,1-t);bb=b.lerp(d,t);dd=b.lerp(d,1-t);q=[aa.lerp(bb,y0),cc.lerp(dd,y0),cc.lerp(dd,y1),aa.lerp(bb,y1)]
   vs=[tuple(p+Vector((0,0,z))) for z in [-.025,.009] for p in q];o=mesh_obj('SM_P5_BRIDGE_FITTED_PAVING',vs,[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)],stone);flat_uv(o,1.5);be=o.modifiers.new('Hand-dressed stone edge','BEVEL');be.width=.003;be.segments=2;count+=1
 for i in range(1,24):
  for side in [0,1]:
   p=v[i*4+side].copy();p.y+=-.0007 if side==0 else .0007;o=box('SM_P5_BRIDGE_VOUSSOIR_JOINT',(p.x,p.y,p.z-.14),(.006,.004,.265),C,grout,.001)
for o in list(s.objects):
 if o.name.startswith('SM_Z02_BRIDGE_STONE_PARAPET') and not o.hide_render:o.scale.x*=.965
stats['fitted_bridge_pavers']=count
# Turn crude cylindrical column bases into restrained stepped/chamfered stone shoes.
count=0
for o in list(s.objects):
 if o.type!='MESH' or o.hide_render or 'COLUMN_BASE' not in o.name:continue
 center,lo,hi=bbox(o);rad=(hi.x-lo.x)/2;h=hi.z-lo.z
 if rad<.10 or h<.06:continue
 profile=[(rad*.91,-h/2),(rad,-h/2+.012),(rad,-h*.18),(rad*.94,h*.14),(rad*.92,h/2-.012),(rad*.85,h/2)]
 replace_world_mesh(o,lathe_mesh(o.name+'_P5_PROFILE',profile),center);put(o,stone,False);flat_uv(o,1.5);count+=1
stats['profiled_column_bases']=count
# Slim paper lamps with a visible fibre sheet; preserve physical lamp and light-data action objects.
lamps=[o for o in s.objects if o.name.startswith('Z05_P2_LAMP_SHADE') and not o.hide_render];lamp_parts=[o for o in s.objects if o.name.startswith(('Z05_P2_LAMP_','P2_COPY_LAMP'))]
for shade in lamps:
 origin=shade.location.copy();pivot=Vector((origin.x,origin.y,.6325));parts=[o for o in lamp_parts if abs(o.location.x-origin.x)<.13 and abs(o.location.y-origin.y)<.13]
 for o in parts:
  o.location=pivot+(o.location-pivot)*.83
  if o.type=='MESH':o.scale*=.83
  elif o.type=='LIGHT':o.data.shadow_soft_size=.045
 put(shade,paper);me=shade.data.copy();me.clear_geometry();me.from_pydata([(-.1,-.1,-.15),(.1,-.1,-.15),(.1,.1,-.15),(-.1,.1,-.15),(-.1,-.1,.15),(.1,-.1,.15),(.1,.1,.15),(-.1,.1,.15)],[],[(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]);me.update();me.materials.clear();me.materials.append(paper);shade.data=me
 for mod in list(shade.modifiers):
  if mod.type=='BEVEL':shade.modifiers.remove(mod)
 uv=me.uv_layers.new()
 for f in me.polygons:
  for li,val in zip(f.loop_indices,[(0,0),(1,0),(1,1),(0,1)]):uv.data[li].uv=val
 so=shade.modifiers.new('Thin paper panel','SOLIDIFY');so.thickness=.0008
 for dz in [-.119,.119]:
  z=shade.location.z+dz
  for side in [-1,1]:box('SM_P5_LAMP_PAPER_BORDER',(shade.location.x,shade.location.y+side*.087,z),(.181,.006,.009),C,dark,.001)
 for dx in [-.091,.091]:
  for dy in [-.091,.091]:
   z=pivot.z+.83*.39;o=beam('SM_P5_LAMP_WOOD_PEG',(origin.x+dx,origin.y+dy,z),(origin.x+dx,origin.y+dy,z+.004),.003,C,wood,8)
stats['refined_paper_lamps']=len(lamps)
# Real rounded flax cushions, a subtle gathered centre and stitched edge. No extra island seat or pedestal.
count=0
for o in list(s.objects):
 if o.type!='MESH' or o.hide_render:continue
 if not (o.name.startswith('Z05_INDOOR_SEAT') or o.name=='Z03_ONLY_ROUND_CUSHION'):continue
 center,lo,hi=bbox(o);r=(hi.x-lo.x)/2;h=hi.z-lo.z
 profile=[(.005,-h*.45),(r*.65,-h*.49),(r*.94,-h*.34),(r,-h*.04),(r*.98,h*.25),(r*.84,h*.43),(r*.45,h*.50),(.005,h*.38)]
 replace_world_mesh(o,lathe_mesh(o.name+'_ROUNDED_FLAX',profile),center);put(o,flax,False);flat_uv(o,3)
 for dz in [-h*.20,h*.24]:
  verts=[];faces=[];n=96;k=6
  for i in range(n):
   a=i*math.tau/n;rr=r*.982+.0007*math.sin(i*.65)
   for j in range(k):b=j*math.tau/k;rrr=rr+.0025*math.cos(b);verts.append((center.x+rrr*math.cos(a),center.y+rrr*math.sin(a),center.z+dz+.0025*math.sin(b)))
  for i in range(n):
   for j in range(k):faces.append((i*k+j,((i+1)%n)*k+j,((i+1)%n)*k+(j+1)%k,i*k+(j+1)%k))
  edge=mesh_obj('SM_P5_CUSHION_PIPED_SEAM',verts,faces,thread)
  for f in edge.data.polygons:f.use_smooth=True
 count+=1
stats['rounded_woven_cushions']=count
# Ceramic body, inner glaze and tiny tea-cup foot: existing hollow geometry and content anchors retained.
count=0
for o in list(s.objects):
 if o.type!='MESH' or o.hide_render:continue
 if 'CUP' in o.name or o.name in ['SM_Z01_RICE_DISH']:
  put(o,glaze);flat_uv(o,4);count+=1
stats['glazed_ceramics']=count
# Desk underside rails and joinery pegs make solid furniture rather than floating slabs.
count=0
for o in list(s.objects):
 if o.type!='MESH' or o.hide_render or not (o.name.startswith('Z05_DESK_') and o.name.endswith('_TOP')):continue
 center,lo,hi=bbox(o)
 for yy in [lo.y+.065,hi.y-.065]:box('SM_P5_DESK_RECESSED_APRON',(center.x,yy,lo.z-.055),(hi.x-lo.x-.18,.045,.105),C,wood,.008)
 for xx in [lo.x+.12,hi.x-.12]:
  for yy in [lo.y+.038,hi.y-.038]:beam('SM_P5_DESK_JOINERY_PEG',(xx,yy,lo.z-.06),(xx,yy+(.004 if yy>center.y else -.004),lo.z-.06),.008,C,dark,12)
 count+=1
stats['joinery_desks']=count
# Fine, low shoreline planting: terrain-rooted, shared 3D leaf clusters; leave walking envelopes clear.
leaf_mats=[mat('P5_SHORE_LEAF_'+str(i),col,.59) for i,col in enumerate([(.13,.23,.058),(.20,.31,.08),(.26,.35,.105)])];stemmat=mat('P5_FINE_SHORE_STEM',(.13,.19,.055),.82)
plant_meshes=[]
for variant in range(3):
 vs=[];fs=[];slots=[]
 for k in range(11+variant*3):
  a=rng.random()*math.tau;length=rng.uniform(.09,.19);height=rng.uniform(.10,.32);root=Vector((0,0,.004));tip=Vector((math.cos(a)*length,math.sin(a)*length,height));mid=root.lerp(tip,.6);side=Vector((-math.sin(a),math.cos(a),0))*rng.uniform(.018,.034);base=len(vs)
  points=[root,root.lerp(tip,.32)-side*.75,mid-side,tip,mid+side,root.lerp(tip,.32)+side*.75,mid+Vector((0,0,.012))];vs.extend([tuple(p) for p in points]);fs.extend([(base+0,base+1,base+6),(base+1,base+2,base+6),(base+2,base+3,base+6),(base+3,base+4,base+6),(base+4,base+5,base+6),(base+5,base+0,base+6)]);slots.extend([k%3]*6)
 me=bpy.data.meshes.new('P5_SHARED_SHORE_LEAF_CLUSTER_'+str(variant));me.from_pydata(vs,[],fs);me.update()
 for m in leaf_mats:me.materials.append(m)
 for f,slot in zip(me.polygons,slots):f.material_index=slot;f.use_smooth=True
 plant_meshes.append(me)
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];bvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons]);routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes']
def dist(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-9,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
placed=0
# Selected bank patches complement, rather than replace, existing fern planting.
for cx,cy,spread in [(-39,-8,2.2),(-39,16,2),(-34,25,2.2),(-23,23,1.9),(-17,12,1.7),(-18,-7,1.8),(-32,-13,1.8),(-42,4,1.8)]:
 for k in range(22):
  x=cx+rng.uniform(-spread,spread);y=cy+rng.uniform(-spread,spread)
  if any(dist(x,y,r['from'],r['to'])<r['width']/2+.18 for r in routes):continue
  hit,n,idx,d=bvh.ray_cast(Vector((x,y,50)),Vector((0,0,-1)),100)
  if not hit or hit.z<-1.05 or abs(n.z)<.65:continue
  o=bpy.data.objects.new('SM_P5_LOW_SHORE_PLANT',plant_meshes[k%3]);C.objects.link(o);o.location=hit;o.rotation_euler.z=rng.random()*math.tau;o.scale=(rng.uniform(.8,1.4),)*3;placed+=1
stats['terrain_rooted_shore_plants']=placed
# Do not change the approved cameras, layout, content surfaces or action library.
s['version']='p3-v029';s['phase_status']='P5_NEAR_VIEW_DETAIL_REFINEMENT';s['detail_revision']='2026-10-21: fine masonry, bridge joints, washi lamps, flax seams, ceramics and joinery'
t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string()+'\n2026-10-21 p3-v029 local craft polish. Same world and cameras, no business-function changes.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/detail-refinement-v029.json').write_text(json.dumps({'source':'p3-v028','version':'p3-v029','changes':stats,'camera_changes':False,'layout_changes':False,'actions_preserved':len(bpy.data.actions),'new_materials':'original deterministic packed PBR maps; source recipe in assets/textures/detail-20261021/source.json'},indent=2));print('DETAIL_REFINEMENT_SAVED',stats,flush=True)
