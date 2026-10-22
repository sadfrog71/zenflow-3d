"""Create auditable lower-density tree modules from fixed CC0 source glTF."""
import bpy,math,json
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/polyhaven/tree_small_02/tree-lods.blend'
if OUT.exists():raise RuntimeError('LOD source exists, do not overwrite')
if bpy.data.filepath:raise RuntimeError('Use factory startup only')
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False);bpy.ops.import_scene.gltf(filepath=str(ROOT/'assets/polyhaven/tree_small_02/tree_small_02_1k.gltf'))
o=next(o for o in bpy.context.scene.objects if o.type=='MESH');bpy.context.view_layer.objects.active=o;o.select_set(True);bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.mesh.separate(type='MATERIAL');bpy.ops.object.mode_set(mode='OBJECT')
parts=[ob for ob in bpy.context.scene.objects if ob.type=='MESH'];rows=[]
for ob in parts:
 mat=ob.data.materials[0];role='leaves' if 'leaves' in mat.name else 'branches' if 'branches' in mat.name else 'trunk';ob.name='CC0_TREE_'+role+'_FULL';source=ob.data
 for lod,ratios in [('NEAR',{'leaves':.025,'branches':.10,'trunk':.15}),('MID',{'leaves':.008,'branches':.035,'trunk':.06}),('FAR',{'leaves':.004,'branches':.015,'trunk':.03})]:
  n=ob.copy();n.data=source.copy();bpy.context.scene.collection.objects.link(n);n.name='CC0_TREE_'+role+'_'+lod;bpy.context.view_layer.objects.active=n;bpy.ops.object.select_all(action='DESELECT');n.select_set(True);mod=n.modifiers.new('Density_LOD','DECIMATE');mod.ratio=ratios[role];mod.use_collapse_triangulate=True;print('DECIMATE_START',n.name,len(n.data.polygons),flush=True);bpy.ops.object.modifier_apply(modifier=mod.name)
  rows.append({'name':n.name,'role':role,'lod':lod,'source_faces':len(source.polygons),'faces':len(n.data.polygons),'vertices':len(n.data.vertices),'ratio':ratios[role]});n['source']='https://polyhaven.com/a/tree_small_02';n['license']='CC0';print('DECIMATE_DONE',rows[-1],flush=True)
 ob.hide_render=True;ob.hide_viewport=True
for im in bpy.data.images:
 if im.source=='FILE':im.pack()
bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True);(ROOT/'evidence/tree-lod-derivation.json').write_text(json.dumps({'source':'tree_small_02_1k.gltf','license':'CC0','author':'Rico Cilliers','method':'material-separated source; collapse decimation retains UVs; multiple densities remain editable','parts':rows},indent=2));print('TREE_LOD_SAVED',OUT,flush=True)
