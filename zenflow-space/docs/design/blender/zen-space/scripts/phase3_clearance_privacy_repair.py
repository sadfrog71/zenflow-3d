"""Repair executed path/seat-sightline failures. No plants counted as privacy enclosure."""
import bpy,sys,math,json,shutil
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import box,assign
s=bpy.context.scene;OUT=ROOT/'zen-space-p3-v006.blend'
if s.get('version')!='p3-v005' or OUT.exists():raise RuntimeError('Requires p3-v005/new output')
m=json.loads((ROOT/'manifest/scene-manifest.json').read_text())
if not (ROOT/'manifest/scene-manifest-p1-v011.json').exists():shutil.copy2(ROOT/'manifest/scene-manifest.json',ROOT/'manifest/scene-manifest-p1-v011.json')
for src in ['structure-check-p3.json','path-obstruction-p3.json','cottage-sightline-p3.json']:
 p=ROOT/'evidence'/src
 if p.exists():shutil.copy2(p,ROOT/'evidence'/src.replace('.json','-v005-failed.json'))
# Preserve the pine itself; prune only the two measured low needle tufts.
pruned=[]
for name in ['SM_BACKGROUND_PINE_TUFT.438','SM_BACKGROUND_PINE_TUFT.439']:
 o=bpy.data.objects.get(name)
 if o:o.hide_viewport=True;o.hide_render=True;o['reason']='measured route body clearance';pruned.append(name)
# Move the real upholstered chair and its seated eye east by 1m, still within the same room.
for o in s.objects:
 if o.name.startswith(('SM_Z11_CHAIR_','SM_Z11_SEAT_CUSHION','SM_Z11_BACK_CUSHION')):o.location.x+=1
for name in ['CAM_CHAT_SEATED','ANCHOR_Z11_CHAT_SEAT']:
 if name in s.objects:s.objects[name].location.x+=1
for o in s.objects:
 if o.name.startswith(('SM_Z11_SIDE_TABLE','SM_Z11_TABLE_LEG','SM_Z11_PAPER_LAMP','SM_Z11_LAMP_RIB','SM_Z11_CUP')) or o.name=='P3_COTTAGE_LAMP':o.location+=Vector((1.0,-.2,0))
cam=s.objects['CAM_CHAT_SEATED'];cam.rotation_euler=(Vector((35.5,24.3,.9))-cam.location).to_track_quat('-Z','Y').to_euler()
# West facade: 1.30m entry at the south side, opaque wall alongside public path.
sh=bpy.data.collections['Z11_SHELL'];plaster=bpy.data.materials['P2_Plaster'];wood=bpy.data.materials['P2_Cedar']
box('SM_Z11_WEST_OPAQUE_SOUTH',(26.3,21.925,1.925),(.16,.35,3.25),sh,plaster)
box('SM_Z11_WEST_OPAQUE_NORTH',(26.3,24.825,1.925),(.16,2.85,3.25),sh,plaster)
for yy in [22.1,23.4]:box('SM_Z11_SIDE_ENTRY_STILE',(26.3,yy,1.675),(.18,.12,2.75),sh,wood,.01)
box('SM_Z11_SIDE_ENTRY_LINTEL',(26.3,22.75,3.10),(.18,1.42,.15),sh,wood,.01)
screen=s.objects['Z11_ENTRY_PRIVACY_SCREEN'];screen.location=(27.65,23.9,1.5);screen.data=screen.data.copy();screen.dimensions=(.10,2.0,2.4);bpy.context.view_layer.objects.active=screen;bpy.ops.object.select_all(action='DESELECT');screen.select_set(True);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);screen.select_set(False)
# Actual side-turn route. Entry uses 1.3m, private internal turn 1.0m; public paths unchanged.
old_routes=m['routes'];m['routes']=[r for r in old_routes if r['route']!='CHAT_BRANCH']
for aa,bb,width,scope in [((25,22.75,0),(25.9,22.75,.3),1.3,'private_entry'),((25.9,22.75,.3),(26.45,22.75,.3),1.3,'private_entry'),((26.45,22.75,.3),(28.4,22.3,.3),1.0,'private_interior'),((28.4,22.3,.3),(28.4,23.1,.3),1.0,'private_interior')]:
 rise=abs(bb[2]-aa[2]);steps=2 if rise>.18 else 1;length=math.dist(aa[:2],bb[:2]);m['routes'].append({'route':'CHAT_BRANCH' if scope=='private_entry' else 'CHAT_PRIVATE_INTERIOR','from':list(aa),'to':list(bb),'width':width,'rise':rise,'steps':steps,'max_riser':rise/steps,'tread_m':length/steps,'landing_pad_m':0,'scope':scope,'profile':'linear_stair' if steps>1 else 'flat','walk_support':'stone threshold' if scope=='private_entry' else 'existing timber floor'})
# Rebuild only the union of known generated road solids, excluding obsolete straight chat branch.
oldnet=s.objects['PUBLIC_EAST_ROUTE_RESOLVED_NETWORK'];oldnet.name='ARCHIVE_ROAD_NETWORK_P3_V003';oldnet.hide_render=True;oldnet.hide_viewport=True
prefix=tuple({r['route']+'_S' for r in old_routes if r['route']!='CHAT_BRANCH'});roads=[o for o in s.objects if o.type=='MESH' and o.name.startswith(prefix) and not o.name.startswith('CSG_')]
cc=bpy.data.collections.new('P3_ROAD_NETWORK_V006');s.collection.children.link(cc);oper=bpy.data.collections.new('P3_ROAD_INPUTS_V006');s.collection.children.link(oper);copies=[]
for i,o in enumerate(roads):
 ob=o.copy();ob.data=o.data.copy();ob.name='CSG_V006_'+o.name
 for mod in list(ob.modifiers):ob.modifiers.remove(mod)
 ob.hide_viewport=False;ob.hide_render=False;(cc if i==0 else oper).objects.link(ob);copies.append(ob)
stone=bpy.data.materials['P3_Weathered_Gray_Rock']
# Physical entry stair and threshold; private interior is supported by the existing wooden floor.
for i in range(2):
 x=25+(i+.5)*.45;top=.15*(i+1)+.018;ob=box('CSG_V006_CHAT_ENTRY_STEP_'+str(i),(x,22.75,top-.3),(.46,1.3,.60),oper,stone);copies.append(ob)
ob=box('CSG_V006_CHAT_THRESHOLD',(26.175,22.75,.118),(.56,1.3,.40),oper,stone);copies.append(ob)
base=copies[0];base.name='PUBLIC_EAST_ROUTE_RESOLVED_NETWORK';bpy.context.view_layer.objects.active=base;bpy.ops.object.select_all(action='DESELECT');base.select_set(True);mod=base.modifiers.new('Exact_union_v006','BOOLEAN');mod.operation='UNION';mod.operand_type='COLLECTION';mod.collection=oper;mod.solver='EXACT'
if hasattr(mod,'use_self'):mod.use_self=True
bpy.ops.object.modifier_apply(modifier=mod.name);assign(base,stone);oper.hide_render=True;oper.hide_viewport=True
for o in copies[1:]:o.hide_render=True;o.hide_viewport=True
# Entry camera now shows the actual door and turn, not the former wide open west wall.
cam=s.objects['CAM_CHAT_ENTRY'];cam.location=(24.7,20.5,1.5);cam.rotation_euler=(Vector((26.3,22.75,1.4))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=30
# Two writing cameras target the same active paper and retain left/right hand workspace.
for name,x in [('CAM_COPY_RH',9.16),('CAM_COPY_LH',9.04)]:
 cam=s.objects[name];cam.location=(x,-2.8,1.24);cam.rotation_euler=(Vector((9.1,-3,.695))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=31
for o in s.objects:
 if o.type=='FONT' and o.name.startswith('SAMPLE_Z05_'):o.rotation_euler.z=math.pi
m['version']='p3-v006';m['source_snapshot']=OUT.name;m['phase']='full model production, not final acceptance';m['cameras']=[]
for o in s.objects:
 if o.type=='CAMERA':
  direction=o.rotation_euler.to_matrix()@Vector((0,0,-1));m['cameras'].append({'name':o.name,'region':o.name.split('_')[1] if '_' in o.name else '', 'position':list(o.location),'target':list(o.location+direction*10),'lens_mm':o.data.lens,'ortho_m':o.data.ortho_scale if o.data.type=='ORTHO' else None,'display_state':'LAYOUT_CUTAWAY' if o.name.startswith('CAM_LAYOUT') else 'WORLD_FULL' if o.name in ['CAM_WORLD_FULL','CAM_P2_CORE_OVERVIEW'] else 'EYE_LEVEL'})
m.setdefault('review_corrections',[]).append('P3 v006: opaque west facade + 1.30m side door, actual chair moved east, internal 1.0m turn separated from >=1.6m public paths; two measured low pine tufts pruned; route union regenerated')
(ROOT/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
s['version']='p3-v006';s['phase_status']='P3_CLEARANCE_PRIVACY_RECHECK';s.camera=s.objects['CAM_WORLD_FULL'];bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True)
(ROOT/'evidence/phase3-clearance-privacy-repair.json').write_text(json.dumps({'version':'p3-v006','failed_baseline':'structure-check-p3-v005-failed.json','pruned_pine_tufts':pruned,'privacy':'real west opaque enclosure and side-turn screen; no plant or chair dependency','seated_eye':list(s.objects['CAM_CHAT_SEATED'].location),'public_path_widths':'unchanged >=1.6m','small_house_entry_width_m':1.3,'private_internal_turn_width_m':1.0,'physical_route_union_faces':len(base.data.polygons),'pending':['body clearance recheck','public path sightline recheck','actual entry and seated renders']},ensure_ascii=False,indent=2));print('PRIVACY_AND_CLEARANCE_REPAIRED',OUT,flush=True)
