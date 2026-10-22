"""Reference-driven surface/foliage and true shoreline corrections; one shared world."""
import bpy,math,json,sys,random
from pathlib import Path
from mathutils import Vector,Matrix
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase3_pbr import pbr
from phase2_materials import box
s=bpy.context.scene;out=R/'zen-space-p3-v023.blend';rng=random.Random(21023)
if s.get('version')!='p3-v022' or out.exists():raise RuntimeError('Requires new v023 from v022')
# Whole-leaf sampling LOD must preserve canopy opacity, not only geometry area per surviving card.
leafgrowth=[]
for lod,factor in [('NEAR',1.25),('MID',1.4),('FAR',1.9)]:
 me=bpy.data.meshes.get('CC0_LEAF_CARDS_'+lod)
 if not me:continue
 for face in me.polygons:
  center=sum((me.vertices[i].co for i in face.vertices),Vector())/len(face.vertices)
  for i in face.vertices:me.vertices[i].co=center+(me.vertices[i].co-center)*factor
 leafgrowth.append({'lod':lod,'leaf_linear_growth':factor,'cards':len(me.polygons)})
for m in bpy.data.materials:
 if m.name.startswith('CC0_TREE_LEAF_CARDS') and m.use_nodes:
  p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Subsurface Weight'].default_value=.06;p.inputs['Roughness'].default_value=.65
# Photographed ground replaces the flat green generated colour. Repeating 2m UV keeps leaf scale readable.
ground=pbr('P4_PHOTOGRAPHED_LEAFY_GROUND','leafy_grass/leafy_grass_diff_2k.jpg','leafy_grass/leafy_grass_nor_gl_2k.jpg','leafy_grass/leafy_grass_rough_2k.jpg',.55)
soil=pbr('P4_PHOTOGRAPHED_FOREST_SOIL','forest_ground_04/forest_ground_04_diff_2k.jpg','forest_ground_04/forest_ground_04_nor_gl_2k.jpg','forest_ground_04/forest_ground_04_rough_2k.jpg',.5)
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];terrain.data.materials.clear();terrain.data.materials.append(ground);terrain.data.materials.append(soil)
for face in terrain.data.polygons:
 center=sum((terrain.data.vertices[i].co for i in face.vertices),Vector())/len(face.vertices);face.material_index=1 if center.z<-1.0 else 0
outer=s.objects['SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE'];outer.data.materials.clear();outer.data.materials.append(ground);uv=outer.data.uv_layers.active or outer.data.uv_layers.new()
for face in outer.data.polygons:
 for li in face.loop_indices:
  v=outer.data.vertices[outer.data.loops[li].vertex_index].co;uv.data[li].uv=(v.x*.5,v.y*.5)
# A genuine soft shoreline: smooth original control polygon, grade terrain, water underneath real banks.
controls=[(-42,-11),(-34,-15),(-23,-13),(-17,-8),(-16,6),(-18,17),(-23,24),(-34,27),(-41,20),(-44,5)];shore=[]
for i in range(len(controls)):
 p0,p1,p2,p3=[Vector(controls[j%len(controls)]) for j in [i-1,i,i+1,i+2]]
 for j in range(12):
  t=j/12;p=.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t);shore.append(tuple(p))
segments=list(zip(shore,shore[1:]+shore[:1]));routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes']
def distseg(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(.000001,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
def inside(x,y):
 hit=False
 for a,b in segments:
  if (a[1]>y)!=(b[1]>y) and x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0]:hit=not hit
 return hit
def smooth(t):t=max(0,min(1,t));return t*t*(3-2*t)
changed=0
for v in terrain.data.vertices:
 x,y,z=v.co
 if not (-48<x<-13 and -18<y<29):continue
 dd=min(distseg(x,y,a,b) for a,b in segments)
 if dd>3.5:continue
 # Existing walking envelopes remain fixed; slab/water/bridges keep their original elevations.
 if any(distseg(x,y,r['from'],r['to'])<r['width']/2+.18 for r in routes if 'BRIDGE' not in r['route']):continue
 h=-.8-1.42*smooth(dd/2.5) if inside(x,y) else -.8*(1-smooth(dd/2))
 v.co.z=h;changed+=1
terrain.data.update();old=s.objects['WATER_LOWER_LEVEL_-1.20'];vv=[];ff=[];nx,ny=82,112
for j in range(ny+1):
 y=-18+j*47/ny
 for i in range(nx+1):
  x=-48+i*35/nx;vv.append((x,y,-1.2+.004*math.sin(x*7+y*4)+.002*math.cos(y*11)))
for j in range(ny):
 for i in range(nx):a=j*(nx+1)+i;ff.append((a,a+1,a+nx+2,a+nx+1))
me=bpy.data.meshes.new('P4_LOWER_WATER_UNDER_TRUE_BANKS');me.from_pydata(vv,[],ff);me.update();me.materials.append(bpy.data.materials['P2_Water']);old.data=me
for face in me.polygons:face.use_smooth=True
water=bpy.data.materials['P2_Water'];p=next(n for n in water.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Metallic'].default_value=0;p.inputs['Transmission Weight'].default_value=.32;p.inputs['IOR'].default_value=1.333;p.inputs['Roughness'].default_value=.075;p.inputs['Base Color'].default_value=(.10,.24,.19,1)
# Physical stone piers reach ground beneath the waterside library floor, rather than a floating slab.
C=bpy.data.collections.new('P4_SURFACE_AND_DISTANT_CONTEXT');s.collection.children.link(C);rock=bpy.data.materials['P3_Weathered_Gray_Rock']
for yy in [-11.4,-7.9,-4.3,-1.3]:box('SM_Z04_WATERSIDE_FOUNDATION_PIER',(-19.65,yy,-1.05),(.62,.72,2.35),C,rock,.035)
# Correct overly neutral timber, retaining the source photographs and real UV grain.
for name in ['P2_Cedar','P3_Honey_Wood']:
 mat=bpy.data.materials[name];nt=mat.node_tree;h=next((n for n in nt.nodes if n.type=='HUE_SAT'),None)
 if h:
  h.inputs['Value'].default_value=1.6
  if h.inputs['Color'].links:
   src=h.inputs['Color'].links[0].from_socket;mix=nt.nodes.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=1;mix.inputs[2].default_value=(1,.84,.65,1);nt.links.new(src,mix.inputs[1]);nt.links.new(mix.outputs[0],h.inputs['Color'])
# Moss in irregular upward-facing pockets, not a uniformly green stone wash.
nt=rock.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');base=p.inputs['Base Color'].links[0].from_socket;rock['exchange_base_image']=base.node.image.name
geo=nt.nodes.new('ShaderNodeNewGeometry');no=nt.nodes.new('ShaderNodeTexNoise');no.inputs['Scale'].default_value=.65;no.inputs['Detail'].default_value=3;nt.links.new(geo.outputs['Position'],no.inputs['Vector']);r=nt.nodes.new('ShaderNodeValToRGB');r.color_ramp.elements[0].position=.64;r.color_ramp.elements[0].color=(0,0,0,1);r.color_ramp.elements[1].position=.8;r.color_ramp.elements[1].color=(.75,.75,.75,1);nt.links.new(no.outputs[0],r.inputs[0]);sep=nt.nodes.new('ShaderNodeSeparateXYZ');nt.links.new(geo.outputs['Normal'],sep.inputs[0]);gt=nt.nodes.new('ShaderNodeMath');gt.operation='GREATER_THAN';gt.inputs[1].default_value=.45;nt.links.new(sep.outputs['Z'],gt.inputs[0]);mul=nt.nodes.new('ShaderNodeMath');mul.operation='MULTIPLY';nt.links.new(r.outputs[0],mul.inputs[0]);nt.links.new(gt.outputs[0],mul.inputs[1]);mix=nt.nodes.new('ShaderNodeMixRGB');mix.inputs[2].default_value=(.075,.12,.035,1);nt.links.new(mul.outputs[0],mix.inputs[0]);nt.links.new(base,mix.inputs[1]);nt.links.new(mix.outputs[0],p.inputs['Base Color'])
# Open a real, permanent eastward view corridor. Remove only known generated context plants.
pruned=[];origin=Vector((27.5,36));direction=Vector((1,.192)).normalized()
def inview(x,y):
 v=Vector((x,y))-origin;d=v.length;return 8<d<80 and v.normalized().dot(direction)>math.cos(math.radians(22))
tr=next(o.data for o in bpy.data.objects if o.name=='CC0_TREE_trunk_FAR');zmin=min(v.co.z for v in tr.vertices);rootpts=[v.co for v in tr.vertices if v.co.z<zmin+.08];root=Vector((sum(v.x for v in rootpts)/len(rootpts),sum(v.y for v in rootpts)/len(rootpts),zmin))
for ob in s.objects:
 if ob.name.startswith(('SM_CONTEXT_GROVE_','SM_CONTEXT_TREE_')) and not ob.hide_render:
  pos=ob.matrix_world@root
  if inview(pos.x,pos.y):ob.hide_render=True;ob.hide_viewport=True;pruned.append(ob.name)
pines=[o.location.copy() for o in s.objects if o.name.startswith('SM_BACKGROUND_PINE_TRUNK') and inview(o.location.x,o.location.y)]
for ob in s.objects:
 if ob.name.startswith(('SM_BACKGROUND_PINE_TRUNK','SM_BACKGROUND_PINE_TUFT')) and not ob.hide_render and any(math.hypot(ob.location.x-p.x,ob.location.y-p.y)<2 for p in pines):ob.hide_render=True;ob.hide_viewport=True;pruned.append(ob.name)
# Distant forest uses baked source-tree sprites outside the functional site; near model trees stay real meshes.
outerpts=[outer.matrix_world@v.co for v in outer.data.vertices];bvh=BVHTree.FromPolygons(outerpts,[tuple(f.vertices) for f in outer.data.polygons]);sprites=[]
for i in range(3):
 im=bpy.data.images.load(str(R/'assets/derived/forest-impostors'/f'tree-{i+1}.png'),check_existing=True);im.pack();mat=bpy.data.materials.new('P4_FAR_FOREST_SPRITE_'+str(i));mat.use_nodes=True;nt=mat.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');t=nt.nodes.new('ShaderNodeTexImage');t.image=im;nt.links.new(t.outputs['Color'],p.inputs['Base Color']);nt.links.new(t.outputs['Alpha'],p.inputs['Alpha']);p.inputs['Roughness'].default_value=1;mat.surface_render_method='DITHERED';mat.use_backface_culling=False;sprites.append(mat)
vs=[[],[],[]];fs=[[],[],[]];uvs=[[],[],[]];count=0
for ix in range(76):
 for iy in range(69):
  x=-420+ix*13+rng.uniform(-4,4);y=-260+iy*13+rng.uniform(-4,4)
  if -110<x<130 and -80<y<160:continue
  hit,n,idx,d=bvh.ray_cast(Vector((x,y,180)),Vector((0,0,-1)),400)
  if not hit or hit.z<-5 or n.z<.6 or rng.random()<.15:continue
  slot=count%3;h=rng.uniform(5,9);w=h;angle=rng.random()*math.tau
  for a in [angle,angle+math.pi/2]:
   u=Vector((math.cos(a)*w/2,math.sin(a)*w/2,0));p=Vector((x,y,hit.z-h*.06));k=len(vs[slot]);vs[slot].extend([p-u,p+u,p+u+Vector((0,0,h)),p-u+Vector((0,0,h))]);fs[slot].append((k,k+1,k+2,k+3));uvs[slot].extend([(0,0),(1,0),(1,1),(0,1)])
  count+=1
for i in range(3):
 me=bpy.data.meshes.new('P4_FAR_FOREST_'+str(i));me.from_pydata(vs[i],[],fs[i]);me.materials.append(sprites[i]);uv=me.uv_layers.new()
 for face in me.polygons:
  for li in face.loop_indices:uv.data[li].uv=uvs[i][me.loops[li].vertex_index]
 ob=bpy.data.objects.new('SM_FAR_FOREST_IMPOSTORS_'+str(i),me);C.objects.link(ob);ob['source']='https://polyhaven.com/a/tree_small_02';ob['license']='CC0';ob['lod_use']='outside functional site only, paired tree sprites baked from actual geometry'
def find(l,n):
 if l.name==n:return l
 for c in l.children:
  q=find(c,n)
  if q:return q
lc=find(s.view_layers['LAYOUT_CUTAWAY'].layer_collection,C.name)
# Keep foundation piers in cutaway; hide only the far sprite objects there through a separate collection.
far=bpy.data.collections.new('P4_FAR_FOREST_CUTAWAY_EXCLUSION');s.collection.children.link(far)
for ob in list(C.objects):
 if ob.name.startswith('SM_FAR_FOREST_IMPOSTORS_'):C.objects.unlink(ob);far.objects.link(ob)
lc=find(s.view_layers['LAYOUT_CUTAWAY'].layer_collection,far.name)
if lc:lc.exclude=True
s['version']='p3-v023';s['phase_status']='P4_NATURAL_SURFACE_REFERENCE_REVIEW';s.frame_set(1);s.camera=s.objects['CAM_ENTRY_SEATED'];t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v022','p3-v023')+'\nFar forest sprites are orthographic bakes of the source CC0 trees; never used in functional near-field views.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v023';m['source_version']='p3-v023';m['source_snapshot']=out.name;(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(R/'evidence/natural-surface-repair.json').write_text(json.dumps({'version':'p3-v023','leaf_coverage':leafgrowth,'photographed_ground':['leafy_grass','forest_ground_04'],'shoreline_control_points':len(shore),'graded_shore_vertices':changed,'water':'physical surface below intersecting banks, IOR1.333/transmission.32','library_foundation_piers':4,'east_view_corridor_pruned_generated_parts':len(pruned),'far_tree_sprite_instances':count,'forests_source':'manifest/far-forest-impostor-source.json','geometry_contract':'same shared world in every view; no per-camera geometry changes'},indent=2));print('NATURAL_SURFACE_FINISHED',changed,count,len(pruned),flush=True)
