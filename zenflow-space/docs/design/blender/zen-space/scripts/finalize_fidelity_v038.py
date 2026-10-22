"""Final source correction after render and clearance review; no route-standard changes."""
import bpy,json,math,random
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v038.blend';rng=random.Random(211038)
if s.get('version')!='p3-v037' or out.exists():raise RuntimeError('Requires new v038')
retired=[]
for o in s.objects:
 if o.name in ['SM_P7_POND_PATH_RUBBLE_SUPPORT.023','SM_P7_POND_PATH_RUBBLE_SUPPORT.051','SM_P7_POND_PATH_RUBBLE_SUPPORT.083'] or (o.name.startswith('SM_P7_POND_PATH_RUBBLE_SUPPORT') and o.location.x>-20 and 5<o.location.y<16):o.hide_render=True;o.hide_viewport=True;retired.append(o.name)
# Actual rock-rooted vegetation interrupts the new cliff's continuous bands.
C=bpy.data.collections['P7_REFERENCE_LANDSCAPE_REPAIR'];ferns=[o.data for o in s.objects if o.type=='MESH' and o.name.startswith('SM_CC0_FERN_BANK')][:3];shrubs=[bpy.data.meshes['P6_SHARED_LOW_SHRUB_'+str(i)] for i in range(3)];plants=0
for i,rock in enumerate([o for o in s.objects if o.name.startswith('SM_P7_Z08_INTERLOCKED_CLIFF')]):
 if i%4:continue
 top=max((rock.matrix_world@Vector(v)).z for v in rock.bound_box);p=rock.location.copy()
 if top>4.35:continue
 o=bpy.data.objects.new('SM_P7_CLIFF_ROOTED_UNDERSTORY',rng.choice(shrubs));C.objects.link(o);o.location=(p.x,p.y-.2,top-.12);k=rng.uniform(.45,.75);o.scale=(k,k,k*.8);o.rotation_euler.z=rng.random()*math.tau;plants+=1
# Reframe from the existing reading seat, retaining true eye position and desk, rather than shifting room geometry.
c=s.objects['CAM_LIBRARY_SEATED'];c.rotation_euler=(Vector((-16.7,-1.2,1.2))-c.location).to_track_quat('-Z','Y').to_euler();c.data.lens=22
# The island remains a low single cushion; look across the pond toward the hall, away from the bridge's close side wall.
c=s.objects['CAM_ISLAND_SEATED'];c.rotation_euler=(Vector((-13.5,-5.0,1.35))-c.location).to_track_quat('-Z','Y').to_euler();c.data.lens=24
s['version']='p3-v038';s['phase_status']='P7_REFERENCE_REPAIR_DELIVERY_CANDIDATE';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/fidelity-final-v038.json').write_text(json.dumps({'base':'p3-v037','retired_clearance_or_veranda_stones':retired,'rock_rooted_plant_groups':plants,'seated_camera_changes':['CAM_LIBRARY_SEATED','CAM_ISLAND_SEATED'],'camera_positions_changed':False,'buildings_routes_actors_moved':False},indent=2));print('FIDELITY_FINAL_V038',len(retired),plants)
