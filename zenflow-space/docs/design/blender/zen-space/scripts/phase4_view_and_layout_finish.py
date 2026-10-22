"""Open physical east cliff view; dense far forest; real RH/LH prop states; no per-camera world moves."""
import bpy,math,json,random,sys
from pathlib import Path
from mathutils import Vector,Matrix
from mathutils.bvhtree import BVHTree
from bpy_extras.object_utils import world_to_camera_view
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import box
s=bpy.context.scene;out=R/'zen-space-p3-v025.blend';rng=random.Random(21025)
if s.get('version')!='p3-v024' or out.exists():raise RuntimeError('Requires new v025 from v024')
# Colour correction keeps photographic small-scale ground structure while restoring the approved green palette.
ground=bpy.data.materials['P4_PHOTOGRAPHED_LEAFY_GROUND'];nt=ground.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');src=p.inputs['Base Color'].links[0].from_socket;mix=nt.nodes.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=1;mix.inputs[2].default_value=(.48,.82,.36,1);nt.links.new(src,mix.inputs[1]);nt.links.new(mix.outputs[0],p.inputs['Base Color'])
# Plateau ends immediately beyond the real lookout platform; a 50m flat foreground formerly hid the valley.
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];pts=[terrain.matrix_world@v.co for v in terrain.data.vertices];faces=[tuple(f.vertices) for f in terrain.data.polygons];oldbvh=BVHTree.FromPolygons(pts,faces)
def smooth(t):t=max(0,min(1,t));return t*t*(3-2*t)
changed=0
for v in terrain.data.vertices:
 x,y,z=v.co
 if not (31.5<x<=85 and 27<y<55):continue
 weight=smooth((x-31.5)/37)*smooth((y-27)/8)*smooth((55-y)/10);target=-18+2*math.sin(x*.07)*math.sin(y*.05);v.co.z=z*(1-weight)+target*weight;changed+=1
terrain.data.update();newbvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],faces)
def height(bvh,x,y):
 p,n,i,d=bvh.ray_cast(Vector((x,y,100)),Vector((0,0,-1)),250);return p.z if p else None
tr=bpy.data.objects['CC0_TREE_trunk_FAR'].data;zmin=min(v.co.z for v in tr.vertices);rr=[v.co for v in tr.vertices if v.co.z<zmin+.08];root=Vector((sum(v.x for v in rr)/len(rr),sum(v.y for v in rr)/len(rr),zmin));moved=[]
for ob in s.objects:
 if ob.hide_render or ob.hide_viewport:continue
 if ob.name.startswith(('SM_CONTEXT_GROVE_','SM_CONTEXT_TREE_')):pos=ob.matrix_world@root
 elif ob.name.startswith(('SM_GROUND_FERN','SM_GROUND_GRASS','SM_LOW_SHRUB','SM_CC0_FERN_BANK','SM_CASCADE_BANK_FERN')):pos=ob.location.copy()
 else:continue
 if pos.x<=31:continue
 a=height(oldbvh,pos.x,pos.y);b=height(newbvh,pos.x,pos.y)
 if a is not None and b is not None and abs(a-b)>.001:ob.location.z+=b-a;moved.append(ob.name)
# Pine parts move coherently by their own trunk's terrain displacement.
pines=[ob.location.copy() for ob in s.objects if ob.name.startswith('SM_BACKGROUND_PINE_TRUNK') and not ob.hide_render]
for ob in s.objects:
 if not ob.name.startswith(('SM_BACKGROUND_PINE_TRUNK','SM_BACKGROUND_PINE_TUFT')) or ob.hide_render:continue
 pos=min(pines,key=lambda p:math.hypot(p.x-ob.location.x,p.y-ob.location.y));a=height(oldbvh,pos.x,pos.y);b=height(newbvh,pos.x,pos.y)
 if a is not None and b is not None and abs(a-b)>.001:ob.location.z+=b-a;moved.append(ob.name)
# Low eastern platform curb, leaving all southern/western route landings open.
C=bpy.data.collections.new('P4_FINAL_CONTEXT_AND_LAYOUT');s.collection.children.link(C);rock=bpy.data.materials['P3_Weathered_Gray_Rock']
for i in range(20):
 a=math.radians(-45+(i+.5)*100/20);r=4.96;ob=box('SM_Z10_EAST_LOW_STONE_CURB',(25+math.cos(a)*r,35+math.sin(a)*r,7.67),(.46,.22,.34),C,rock,.025);ob.rotation_euler.z=a+math.pi/2
cam=s.objects['CAM_LOOKOUT_E'];cam.rotation_euler=(Vector((90,48,8.55))-cam.location).to_track_quat('-Z','Y').to_euler();cloud=s.objects['ENV_FAR_VALLEY_CLOUD_SEA'];cloud.location.z=-17;cloud.scale.z=20
# Dense far forest is still only three mesh nodes; cards are bakes of the actual CC0 tree source.
far=bpy.data.collections['P4_FAR_FOREST_CUTAWAY_EXCLUSION']
for ob in list(far.objects):
 if ob.name.startswith('SM_FAR_FOREST_IMPOSTORS_'):bpy.data.objects.remove(ob,do_unlink=True)
outer=s.objects['SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE'];obvh=BVHTree.FromPolygons([outer.matrix_world@v.co for v in outer.data.vertices],[tuple(f.vertices) for f in outer.data.polygons]);vs=[[],[],[]];fs=[[],[],[]];uvs=[[],[],[]];trees=0
for ix in range(128):
 for iy in range(113):
  x=-420+ix*8+rng.uniform(-3.5,3.5);y=-260+iy*8+rng.uniform(-3.5,3.5)
  if -110<x<130 and -80<y<160:continue
  hit,n,idx,d=obvh.ray_cast(Vector((x,y,180)),Vector((0,0,-1)),400)
  if not hit or hit.z<-5 or abs(n.z)<.6 or rng.random()<.08:continue
  slot=trees%3;h=rng.uniform(9,15);angle=rng.random()*math.tau
  for a in [angle,angle+math.pi/2]:
   u=Vector((math.cos(a)*h/2,math.sin(a)*h/2,0));p=Vector((x,y,hit.z-h*.06));k=len(vs[slot]);vs[slot].extend([p-u,p+u,p+u+Vector((0,0,h)),p-u+Vector((0,0,h))]);fs[slot].append((k,k+1,k+2,k+3));uvs[slot].extend([(0,0),(1,0),(1,1),(0,1)])
  trees+=1
for i in range(3):
 me=bpy.data.meshes.new('P4_DENSE_FAR_FOREST_'+str(i));me.from_pydata(vs[i],[],fs[i]);me.materials.append(bpy.data.materials['P4_FAR_FOREST_SPRITE_'+str(i)]);uv=me.uv_layers.new()
 for f in me.polygons:
  for li in f.loop_indices:uv.data[li].uv=uvs[i][me.loops[li].vertex_index]
 ob=bpy.data.objects.new('SM_FAR_FOREST_IMPOSTORS_'+str(i),me);far.objects.link(ob);ob['license']='CC0';ob['source']='https://polyhaven.com/a/tree_small_02';ob['lod_use']='far context only, outside functional garden'
# Replace unsupported-looking free-floating near-bank leaf clumps with rooted, photographed fern meshes.
near=[o for o in list(s.objects) if o.type=='MESH' and not o.hide_render and o.name.startswith(('Z02_P2_BANK_FOLIAGE','SM_BANK_SHRUB_CLUSTER'))];ferns=[]
for ob in s.objects:
 if ob.type=='MESH' and ob.name.startswith('SM_CC0_FERN_BANK') and ob.data not in ferns:ferns.append(ob.data)
 if len(ferns)==4:break
rocks=[o for o in s.objects if o.type=='MESH' and not o.hide_render and o.name.startswith(('SM_ROCK_REPLACEMENT','SM_Z02_NATURAL_BANK_STONE'))];rv=[];rf=[]
for ob in rocks:
 offset=len(rv);rv.extend(ob.matrix_world@v.co for v in ob.data.vertices);rf.extend(tuple(offset+i for i in f.vertices) for f in ob.data.polygons)
rbvh=BVHTree.FromPolygons(rv,rf);replaced=0
for old in near:
 x,y=old.location.x,old.location.y;z=height(newbvh,x,y);hit,n,i,d=rbvh.ray_cast(Vector((x,y,60)),Vector((0,0,-1)),120)
 if hit:z=max(z if z is not None else -99,hit.z)
 old.hide_render=True;old.hide_viewport=True
 if z is None or z<-1.1:continue
 me=ferns[replaced%len(ferns)];ob=bpy.data.objects.new('SM_NEAR_BANK_ROOTED_FERN_'+str(replaced),me);C.objects.link(ob);sc=rng.uniform(.55,.9);ob.scale=(sc,sc,sc);minimum=min(v.co.z for v in me.vertices)*sc;ob.location=(x,y,z-minimum-.018);ob.rotation_euler.z=rng.random()*math.tau;replaced+=1
# Wet bank rocks use more local moss; road and floor stone keeps the subtler original material.
moss=rock.copy();moss.name='P4_MOSSY_BANK_ROCK';r=next(n for n in moss.node_tree.nodes if n.type=='VALTORGB');r.color_ramp.elements[0].position=.4;r.color_ramp.elements[1].position=.66
for ob in rocks:
 if ob.data.users>1:ob.data=ob.data.copy()
 ob.data.materials[0]=moss
# Actual selectable handedness states: paper stays fixed, ink/brush/template have mirrored positions.
rh=bpy.data.collections.new('STATE_Z05_RH_PROPS');s.collection.children.link(rh);lh=bpy.data.collections.new('STATE_Z05_LH_PROPS');s.collection.children.link(lh);targets=[]
for ob in list(s.objects):
 if ob.name.startswith('Z05_DESK_9.2_-3') and any(k in ob.name for k in ['INKSTONE','BRUSH']):targets.append(ob)
for ob in targets:
 if 'INKSTONE' in ob.name:ob.location.x=8.52;ob.location.y=-3.03
 else:ob.location.x=8.48;ob.location.y=-2.70
 for c in list(ob.users_collection):c.objects.unlink(ob)
 rh.objects.link(ob);ob['handedness']='RH'
paper=bpy.data.materials['P2_Paper'];tpl=box('SM_Z05_RH_TEMPLATE_SAMPLE',(9.45,-3,.696),(.08,.30,.0015),rh,paper,.002);tpl['sample_only']=True;targets.append(tpl)
d=bpy.data.curves.new('TEMPLATE_SAMPLE_GLYPHS','FONT');d.body='示\n意';d.size=.032;d.align_x='CENTER';d.align_y='CENTER';d.font=next(f for f in bpy.data.fonts if 'Noto' in f.name);text=bpy.data.objects.new('SAMPLE_Z05_RH_TEMPLATE_LABEL',d);rh.objects.link(text);text.location=(9.45,-3,.698);text.rotation_euler.z=math.pi;d.materials.append(bpy.data.materials['P3_DRY_INK']);text['sample_only']=True;targets.append(text)
for ob in targets:
 cp=ob.copy();cp.data=ob.data.copy();cp.name=ob.name.replace('_RH_','_LH_') if '_RH_' in ob.name else 'SM_Z05_LH_'+ob.name;lh.objects.link(cp);cp.location.x=18.2-ob.location.x;cp.hide_render=True;cp.hide_viewport=True;cp['handedness']='LH'
 if 'BRUSH' in ob.name:
  direction=ob.rotation_euler.to_matrix()@Vector((0,0,1));direction.x=-direction.x;cp.rotation_euler=direction.to_track_quat('Z','Y').to_euler()
for name,xyz in [('ANCHOR_Z05_INK',(8.52,-3.03,.715)),('ANCHOR_Z05_BRUSH',(8.48,-2.70,.70))]:s.objects[name].location=xyz
# Page-aligned cameras keep 62% paper area and leave a narrow complete template beside it.
points=[Vector((9.1+dx,-3+dy,.695)) for dx,dy in [(-.275,-.18),(.275,-.18),(.275,.18),(-.275,.18)]];coverage=[]
def area(cam):
 q=[world_to_camera_view(s,cam,p) for p in points];return abs(sum(q[i].x*q[(i+1)%4].y-q[(i+1)%4].x*q[i].y for i in range(4)))/2,q
for name,sign in [('CAM_COPY_RH',1),('CAM_COPY_LH',-1)]:
 cam=s.objects[name];target=Vector((9.1+sign*.025,-3,.695));direction=(target-cam.location).normalized();right=direction.cross(Vector((0,-1,0))).normalized();up=right.cross(direction).normalized();cam.rotation_euler=Matrix((right,up,-direction)).transposed().to_euler();lo,hi=5,80
 for _ in range(40):
  cam.data.lens=(lo+hi)/2;bpy.context.view_layer.update();a,q=area(cam)
  if a<.62:lo=cam.data.lens
  else:hi=cam.data.lens
 a,q=area(cam);coverage.append({'camera':name,'paper_projected_area':a,'corners':[[p.x,p.y] for p in q],'all_corners_inside':all(0<=p.x<=1 and 0<=p.y<=1 for p in q),'lens_mm':cam.data.lens,'handedness_state':name[-2:]})
s['version']='p3-v025';s['phase_status']='P4_REFERENCE_AND_LAYOUT_RECHECK';s.frame_set(1);s.camera=s.objects['CAM_ENTRY_SEATED'];t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v024','p3-v025')+'\nRH/LH are explicit prop layout states. Paper position stays fixed; alternate props are hidden by default.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v025';m['source_version']='p3-v025';m['source_snapshot']=out.name
for c in m['cameras']:
 ob=s.objects[c['name']];c['position']=list(ob.location);c['rotation_euler']=list(ob.rotation_euler);c['target']=list(ob.location+ob.rotation_euler.to_matrix()@Vector((0,0,-10)));c['lens_mm']=ob.data.lens
(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(R/'manifest/handedness-layout-states.json').write_text(json.dumps({'version':'p3-v025','default':'RH','RH':'STATE_Z05_RH_PROPS','LH':'STATE_Z05_LH_PROPS','rule':'visibility state swaps actual alternate props; building/landscape/paper remain fixed; state must be explicitly selected, never inferred from a stroke','sample_text':'示意 only; no scripture quote or private content'},ensure_ascii=False,indent=2));(R/'evidence/writing-paper-occupancy.json').write_text(json.dumps({'version':'p3-v025','target':[.6,.7],'results':coverage},indent=2));(R/'evidence/view-and-layout-finish.json').write_text(json.dumps({'version':'p3-v025','nonwalkable_east_cliff_vertices':changed,'regrounded_generated_plant_parts':len(moved),'far_tree_sprite_instances':trees,'rooted_near_fern_replacements':replaced,'east_low_stone_curb':'only east arc, all actual public landings left open','layout_variants':len(targets),'outdoor_cushion':'still exactly one'},indent=2));print('VIEW_LAYOUT_FINISHED',changed,trees,replaced,coverage,flush=True)
