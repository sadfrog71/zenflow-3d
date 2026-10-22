"""Resolve confirmed coplanar path intersections using an exact solid union.
Retain individual generated sources hidden; result is an editable mesh, not an image workaround.
"""
import bpy,sys,math,json,random,bmesh
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import box,beam,assign,light
s=bpy.context.scene;OUT=ROOT/'zen-space-p3-v003.blend';R=random.Random(202610205)
if s.get('version')!='p3-v002' or OUT.exists():raise RuntimeError('Requires p3-v002/new output')
m=json.loads((ROOT/'manifest/scene-manifest.json').read_text());prefix=tuple({r['route']+'_S' for r in m['routes']});roads=[o for o in s.objects if o.type=='MESH' and o.name.startswith(prefix) and not o.hide_viewport]
c=bpy.data.collections.new('P3_RESOLVED_ROAD_NETWORK');s.collection.children.link(c);oper=bpy.data.collections.new('P3_ROAD_BOOLEAN_INPUTS');s.collection.children.link(oper)
inputs=[]
for i,o in enumerate(roads):
 ob=o.copy();ob.data=o.data.copy();ob.name='CSG_INPUT_'+o.name
 for mod in list(ob.modifiers):ob.modifiers.remove(mod)
 (c if i==0 else oper).objects.link(ob);ob.hide_viewport=False;ob.hide_render=False;inputs.append(ob)
base=inputs[0];base.name='PUBLIC_EAST_ROUTE_RESOLVED_NETWORK';bpy.context.view_layer.objects.active=base;bpy.ops.object.select_all(action='DESELECT');base.select_set(True)
mod=base.modifiers.new('Exact_path_volume_union','BOOLEAN');mod.operation='UNION';mod.operand_type='COLLECTION';mod.collection=oper;mod.solver='EXACT'
if hasattr(mod,'use_self'):mod.use_self=True
print('CSG_START',len(inputs),flush=True);bpy.ops.object.modifier_apply(modifier=mod.name);print('CSG_APPLIED',len(base.data.vertices),len(base.data.polygons),flush=True)
assign(base,bpy.data.materials['P3_Weathered_Gray_Rock'])
for o in roads:o.hide_render=True;o.hide_viewport=True;o['replaced_by']='PUBLIC_EAST_ROUTE_RESOLVED_NETWORK'
oper.hide_render=True;oper.hide_viewport=True
for o in inputs[1:]:o.hide_render=True;o.hide_viewport=True
bm=bmesh.new();bm.from_mesh(base.data);nonmanifold=sum(not e.is_manifold for e in bm.edges);volume=bm.calc_volume(signed=True);bm.free()
# Flat top and natural island stone, retaining exact cushion-ground contact.
island=bpy.data.objects['Z03_LOW_ISLAND_GROUND'];assign(island,bpy.data.materials['P3_Weathered_Gray_Rock'])
cu=bpy.data.objects['Z03_ONLY_ROUND_CUSHION'];be=cu.modifiers.new('Rounded_woven_edge','BEVEL');be.width=.022;be.segments=4
cam=bpy.data.objects['CAM_ISLAND_SEATED'];cam.location=(-28.45,3.25,.35);cam.rotation_euler=(Vector((-10,-17,-3.1))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=20
# Readable water view through the actual west library wall: sill and lattice remain in same facade.
for o in s.objects:
 if not o.name.startswith('Z04_EW_WALL') or abs(o.location.x+20)>.1:continue
 if '_SILL' in o.name:
  top=.55;o.data=o.data.copy();o.dimensions.z=.25;o.location.z=.425;bpy.context.view_layer.objects.active=o;o.select_set(True);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);o.select_set(False)
 elif '_LATTICE_V' in o.name:
  o.data=o.data.copy();o.dimensions.z+=.5;o.location.z-=.25;bpy.context.view_layer.objects.active=o;o.select_set(True);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);o.select_set(False)
 elif '_LATTICE_H' in o.name and abs(o.location.z-1.05)<.05:o.location.z=.55
cam=bpy.data.objects['CAM_LIBRARY_SEATED'];cam.location=(-18.55,-8.2,1.15);cam.rotation_euler=(Vector((-31,6,-.6))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=27
# Existing small-room windows and stone seat clearances remain unchanged.
s['version']='p3-v003';s['phase_status']='P3_SURFACE_REPAIR_REVIEW_PENDING';s.camera=bpy.data.objects['CAM_WORLD_FULL'];bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True)
(ROOT/'evidence/phase3-surface-repair.json').write_text(json.dumps({'version':'p3-v003','cause_evidence':'coplanar-path-diagnosis.json','method':'Exact solid union of generated route cubes; previous editable sources retained hidden','source_objects':len(inputs),'union_vertices':len(base.data.vertices),'union_faces':len(base.data.polygons),'non_manifold_edges':nonmanifold,'signed_volume_m3':volume,'other_changes':['island natural ground material and rounded cushion edge, no added pedestal','island view aims at true hall positions with slight downward seated framing','west library sill lowered to 0.55m absolute; actual matching lattice extended','reading view aimed through real west opening toward actual pond'],'pending':['same-camera black-face regression render','route support/clearance check','full reference comparison']},ensure_ascii=False,indent=2));print('SURFACE_REPAIR_SAVED',OUT,flush=True)
