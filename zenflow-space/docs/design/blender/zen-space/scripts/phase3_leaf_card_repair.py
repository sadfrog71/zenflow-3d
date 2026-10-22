import bpy,sys,json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase3_pbr import pbr
s=bpy.context.scene;OUT=ROOT/'zen-space-p3-v007.blend'
if s.get('version')!='p3-v006' or OUT.exists():raise RuntimeError('Requires p3-v006/new output')
lib=bpy.data.collections.new('P3_AREA_PRESERVING_LEAF_LIBRARY');s.collection.children.link(lib)
with bpy.data.libraries.load(str(ROOT/'assets/polyhaven/tree_small_02/tree-leaf-cards.blend'),link=False) as (src,dst):dst.objects=[n for n in src.objects if n in ['CC0_LEAF_CARDS_NEAR','CC0_LEAF_CARDS_MID','CC0_LEAF_CARDS_FAR']]
meshes={}
for o in dst.objects:
 lib.objects.link(o);o.hide_render=True;o.hide_viewport=True;meshes[o.name.split('_')[-1]]=o.data
changed=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_viewport:continue
 if '_CC0_LEAVES' in o.name:o.data=meshes['NEAR'];changed.append(o.name)
 elif o.name.startswith('SM_CONTEXT_TREE_') and o.name.endswith('_LEAVES'):o.data=meshes['FAR'];changed.append(o.name)
# A seamless bark texture is appropriate on authored trunk/roots, unlike an imported atlas.
bark=pbr('P3_SEAMLESS_OLD_BARK','bark_brown_01_diff_reused.jpg','bark_brown_01_nor_gl_1k.jpg','bark_brown_01_rough_1k.jpg',.32)
for o in s.objects:
 if o.type!='MESH' or o.hide_viewport:continue
 if o.name.startswith(('SM_Z01_COURTYARD_TREE','SM_Z08_WISH_TREE','SM_Z09_LANDSCAPE_TREE','SM_Z11_GARDEN_TREE')) and any(k in o.name for k in ['_TRUNK','_ROOT','_BRANCH']) and '_CC0_' not in o.name:
  o.data=o.data.copy();o.data.materials.clear();o.data.materials.append(bark);uv=o.data.uv_layers.active
  if uv:
   for p in uv.data:p.uv.x*=3
# Entry stiles sit outside the stated 1.30m clear opening, not on its center lines.
for o in s.objects:
 if o.name.startswith('SM_Z11_SIDE_ENTRY_STILE'):o.location.y=22.04 if o.location.y<22.75 else 23.46
# Make a new source directly inspectable in shaded material view; no master overwrite until recheck.
for screen in bpy.data.screens:
 for area in screen.areas:
  if area.type=='VIEW_3D':
   for sp in area.spaces:
    if sp.type=='VIEW_3D':sp.shading.type='MATERIAL';sp.shading.use_scene_world=True;sp.shading.use_scene_lights=True;sp.region_3d.view_perspective='CAMERA';sp.overlay.show_extras=False
s['version']='p3-v007';s['phase_status']='P3_LEAF_AREA_AND_CLEARANCE_RECHECK';s.camera=s.objects['CAM_ENTRY_SEATED'];bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True)
p=ROOT/'manifest/scene-manifest.json';m=json.loads(p.read_text());m['version']='p3-v007';m['source_snapshot']=OUT.name;m['limitations']=['model in production, not final fidelity acceptance','iOS/device/application behavior not verified','state animations and full model export pending'];p.write_text(json.dumps(m,ensure_ascii=False,indent=2))
(ROOT/'evidence/phase3-leaf-card-repair.json').write_text(json.dumps({'version':'p3-v007','failed_visual_baseline':'p3-v005 renders: collapse-decimated leaves lost area and appeared bare','repair':'original leaf component planes and dimensions retained as alpha UV cards; separate whole-leaf sampling LODs','derivation':'tree-leaf-card-derivation.json','replaced_leaf_objects':len(changed),'source_asset':'https://polyhaven.com/a/tree_small_02','hero_bark':'seamless CC0 bark on authored geometry; imported atlas kept only on matching imported meshes','cottage_clear_opening':1.30,'pending':['actual rendered foliage comparison','recheck full path and private seat line of sight']},ensure_ascii=False,indent=2));print('LEAF_CARDS_REPAIRED_SOURCE',OUT,flush=True)
