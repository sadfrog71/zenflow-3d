"""Photographed tree modules at measured LODs; structural and dressing corrections from real renders."""
import bpy,sys,math,json,random
from pathlib import Path
from mathutils import Vector,Matrix
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import box,beam,assign,material
s=bpy.context.scene;OUT=ROOT/'zen-space-p3-v005.blend';R=random.Random(202610207)
if s.get('version')!='p3-v004' or OUT.exists():raise RuntimeError('Requires p3-v004/new output')
C=bpy.data.collections.new('P3_PHOTO_TREE_MODULES');s.collection.children.link(C);LIB=bpy.data.collections.new('P3_TREE_LOD_LIBRARY');s.collection.children.link(LIB)
need=[f'CC0_TREE_{role}_{lod}' for role in ['branches','leaves','trunk'] for lod in ['NEAR','MID','FAR']]
with bpy.data.libraries.load(str(ROOT/'assets/polyhaven/tree_small_02/tree-lods-v002.blend'),link=False) as (src,dst):dst.objects=[n for n in src.objects if n in need]
parts={}
for o in dst.objects:
 if not o:continue
 LIB.objects.link(o);o.hide_render=True;o.hide_viewport=True;parts[o.name]=o
leaf=parts['CC0_TREE_leaves_NEAR'].data;pv=[v.co for v in leaf.vertices];mn=Vector(tuple(min(v[k] for v in pv) for k in range(3)));mx=Vector(tuple(max(v[k] for v in pv) for k in range(3)));center=(mn+mx)/2;size=mx-mn
# Instance source meshes, preserving original UV/materials and all measured low-density variants.
def inst(n,me,matrix,parent=C):
 o=bpy.data.objects.new(n,me);parent.objects.link(o);o.matrix_world=matrix;o['source']='https://polyhaven.com/a/tree_small_02';o['license']='CC0';return o
heroes=[('Z01_COURTYARD_TREE',0,9,0,8.0,7.4,2.8,4.7),('Z08_WISH_TREE',-26,31,4.5,8.0,7.2,2.8,4.7),('Z09_LANDSCAPE_TREE',0,36,7.5,12.0,10.5,4.2,7.0),('Z11_GARDEN_TREE',35,25,0,4.5,4.2,2.3,3.2)]
for prefix,x,y,z,w,d,h,ch in heroes:
 for o in list(s.objects):
  if o.name.startswith('SM_'+prefix) and any(k in o.name for k in ['FOLIAGE','TWIG']):o.hide_render=True;o.hide_viewport=True
 scale=Vector((w/size.x,d/size.y,h/size.z));angle=R.random()*math.tau;mat=Matrix.Translation(Vector((x,y,z+ch)))@Matrix.Rotation(angle,4,'Z')@Matrix.Diagonal((*scale,1))@Matrix.Translation(-center)
 for role in ['leaves','branches']:inst('SM_'+prefix+'_CC0_'+role.upper(),parts['CC0_TREE_'+role+'_NEAR'].data,mat)
# Whole-tree source root and height measured from actual imported trunk.
tr=parts['CC0_TREE_trunk_NEAR'].data;pts=[v.co for v in tr.vertices];zmin=min(v.z for v in pts);roots=[v for v in pts if v.z<zmin+.08];root=Vector((sum(v.x for v in roots)/len(roots),sum(v.y for v in roots)/len(roots),zmin));height=mx.z-zmin
context=0
for old in list(s.objects):
 if old.name.startswith('SM_MIDGROUND_TREE_LOD') and not old.hide_viewport:
  sc=Vector((3.5/size.x*old.scale.x,3.5/size.y*old.scale.y,4.1/height*old.scale.z));mat=Matrix.Translation(old.location)@Matrix.Rotation(old.rotation_euler.z,4,'Z')@Matrix.Diagonal((*sc,1))@Matrix.Translation(-root)
  for role in ['leaves','branches','trunk']:inst('SM_CONTEXT_TREE_'+str(context)+'_'+role.upper(),parts['CC0_TREE_'+role+'_FAR'].data,mat)
  old.hide_render=True;old.hide_viewport=True;context+=1
# Replace stripy procedural bark on named hero trunks/roots with actual photographed bark.
bark=parts['CC0_TREE_trunk_NEAR'].data.materials[0]
for o in s.objects:
 if o.type=='MESH' and o.data.materials and o.data.materials[0] and o.data.materials[0].name=='P3_OLD_BARK':
  o.data.materials[0]=bark
  if any(x in o.name for x in ['TRUNK','ROOT','BRANCH']):
   me=o.data;uv=me.uv_layers.active
   if uv:
    cx=(min(v.co.x for v in me.vertices)+max(v.co.x for v in me.vertices))/2;cy=(min(v.co.y for v in me.vertices)+max(v.co.y for v in me.vertices))/2;zz=min(v.co.z for v in me.vertices)
    for f in me.polygons:
     for li in f.loop_indices:
      v=me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(math.atan2(v.y-cy,v.x-cx)/math.tau+.5,(v.z-zz)/2)
# Opening frames at side-wall bay joints eliminate floating grille ends.
cedar=bpy.data.materials['P2_Cedar']
for code,cx,cy,w,d in [('Z04',-11,-6.5,18,10.8),('Z05',12,-6.5,19.8,12.6),('Z06',20,16,7.2,5.4),('Z11',29,24,5.4,4.5)]:
 count=max(1,round(d/3.6))
 for i in range(1,count):
  yy=cy-d/2+i*d/count
  for xx in [cx-w/2,cx+w/2]:box('SM_'+code+'_SIDE_WINDOW_STILE',(xx,yy,1.925),(.14,.14,3.25),C,cedar,.008)
# The reference places the small table on the visible right side of the seated view.
move_prefix=('SM_Z11_SIDE_TABLE','SM_Z11_TABLE_LEG','SM_Z11_PAPER_LAMP','SM_Z11_LAMP_RIB','SM_Z11_CUP')
for o in s.objects:
 if o.name.startswith(move_prefix) or o.name=='P3_COTTAGE_LAMP':o.location+=Vector((.45,-1.5,0))
soft=material('P3_COTTAGE_SHADE',(.74,.61,.42),None,.78,0,False,.10)
for o in s.objects:
 if o.name.startswith('SM_Z11_PAPER_LAMP'):assign(o,soft)
light=bpy.data.objects['P3_COTTAGE_LAMP'];light.data.energy=2.0
cam=bpy.data.objects['CAM_CHAT_SEATED'];cam.rotation_euler=(Vector((34.5,24.8,.9))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=26
# The bronze bell is old, not a polished gold display object. Remove raised wear plate.
bronze=bpy.data.materials['P3_BRONZE'];p=next(n for n in bronze.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(.095,.11,.075,1);p.inputs['Metallic'].default_value=.65;p.inputs['Roughness'].default_value=.62
wear=bpy.data.materials['P3_WORN_BRONZE'];p=next(n for n in wear.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(.19,.17,.10,1);p.inputs['Roughness'].default_value=.4
old=bpy.data.objects.get('SM_Z07_STRIKE_WEAR')
if old:old.hide_render=True;old.hide_viewport=True
# A small shallow oval worn patch hugs the shell instead of a square plate.
bpy.ops.mesh.primitive_uv_sphere_add(segments=24,ring_count=12,radius=1,location=(-3,21.52,1.84));patch=bpy.context.object;patch.name='SM_Z07_OVAL_WORN_STRIKE';patch.scale=(.08,.003,.065)
for cc in list(patch.users_collection):cc.objects.unlink(patch)
C.objects.link(patch);assign(patch,wear)
for f in patch.data.polygons:f.use_smooth=True
# Keep wish canopy and low wood tags simultaneously visible from the real platform eye point.
cam=bpy.data.objects['CAM_WISH_APPROACH'];cam.rotation_euler=(Vector((-25.5,31,6.5))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=22
# Organize roof additions into their real roof collections so cutaway does not leave tiled shells hovering.
relinked=[]
for o in list(s.objects):
 target=None
 if o.name.startswith('Z04_P2_TILE_COURSE'):target=bpy.data.collections['Z04_ROOF']
 elif o.name.startswith('Z05_P2_TILE_COURSE'):target=bpy.data.collections['Z05_ROOF']
 elif o.name.startswith('Z01_P2_VISIBLE_RAFTER'):target=bpy.data.collections['Z01_ROOF']
 if target:
  for cc in list(o.users_collection):cc.objects.unlink(o)
  target.objects.link(o);relinked.append(o.name)
# Explicit canopy-only groups: cutaway hides leaves and context plants, retaining architecture coordinates.
canopy=bpy.data.collections.new('P3_CANOPIES_CUTAWAY_EXCLUSION');s.collection.children.link(canopy)
for o in list(s.objects):
 if o.type=='MESH' and not o.hide_viewport and (('_CC0_LEAVES' in o.name) or o.name.startswith(('SM_CONTEXT_TREE_','SM_Z01_SPARSE_RED_FLOWER','SM_CC0_FERN_BANK','SM_BANK_SHRUB_CLUSTER','SM_MIDGROUND_TREE','SM_BACKGROUND_PINE_TUFT'))):
  for cc in list(o.users_collection):cc.objects.unlink(o)
  canopy.objects.link(o)
def find(root,name):
 if root.name==name:return root
 for ch in root.children:
  got=find(ch,name)
  if got:return got
for vl in s.view_layers:
 item=find(vl.layer_collection,canopy.name)
 if item:item.exclude=vl.name=='LAYOUT_CUTAWAY'
# Main file portability: packed source textures, reference images stay packed too.
for im in bpy.data.images:
 if im.source=='FILE' and not im.packed_file:
  try:im.pack()
  except RuntimeError:pass
s['version']='p3-v005';s['phase_status']='P3_ALL_VIEW_SELF_CHECK_PENDING';s.camera=bpy.data.objects['CAM_WORLD_FULL'];bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True)
(ROOT/'evidence/phase3-tree-detail-finish.json').write_text(json.dumps({'version':'p3-v005','base':'p3-v004','asset_source':'https://polyhaven.com/a/tree_small_02','license':'CC0','hero_canopies':4,'context_trees':context,'LOD_derivation':'tree-lod-v002.json','roof_additions_relinked':len(relinked),'cutaway_canopy_objects':len(canopy.objects),'other_corrections':['side-window bay stiles','small-table placement at right of cottage seated view','softened cottage shade emission','aged bronze color and shallow oval strike wear','wish canopy in actual platform view'],'pending':['all-region visual/ref comparisons','path/room clearances','states/animation and final export']},ensure_ascii=False,indent=2));print('PHOTO_TREES_AND_DETAILS_SAVED',OUT,flush=True)
