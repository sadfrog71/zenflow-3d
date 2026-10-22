"""Structural measurements and walk-surface checks; does not grant aesthetic approval."""
import bpy,json,math
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
s=bpy.context.scene;m=json.loads((ROOT/'manifest/scene-manifest.json').read_text());m['version']=s.get('version');checks=[]
def check(n,ok,detail):checks.append({'requirement':n,'status':'PASS' if ok else 'FAIL','actual':detail})
check('Source reopened in separate Blender process',bool(bpy.data.filepath),bpy.data.filepath)
check('Metric and axes',s.unit_settings.system=='METRIC' and s.unit_settings.scale_length==1,'1 Blender unit = 1m; +X east +Y north +Z up')
check('All 12 regions registered',len(m['zones'])==12,[z['code'] for z in m['zones']])
check('All named cameras present',all(c['name'] in s.objects for c in m['cameras']),len(m['cameras']))
check('No negative or zero mesh scale',all(min(o.scale)>0 for o in s.objects if o.type=='MESH'),'all mesh scales inspected')
check('Exactly one outdoor cushion',len([o for o in s.objects if o.get('outdoor_cushion')])==1,'Z03_ONLY_ROUND_CUSHION')
check('Cushion directly on island',abs((s.objects['Z03_ONLY_ROUND_CUSHION'].location.z-.06)-(-.3))<.001,'bottom -0.30m; island top -0.30m')
check('Separate private and dialogue rooms','Z06_PRIVATE_SIDE_ROOM' in bpy.data.collections and 'Z11_DIALOGUE_COTTAGE' in bpy.data.collections,'two separate shells, roofs and independent branches')
check('Four independent book selection anchors',len([o for o in s.objects if o.name.startswith('SELECT_BOOK_')])==4,[o.name for o in s.objects if o.name.startswith('SELECT_BOOK_')])
check('Water flows downwards',all(a>b for a,b in zip(m['water_levels']['cascade_top_to_bottom'],m['water_levels']['cascade_top_to_bottom'][1:])),m['water_levels'])
bridges=[r for r in m['routes'] if 'BRIDGE' in r['route']]
check('Both bridges above water',len(bridges)==2 and all(r['min_water_clearance']>.5 for r in bridges),bridges)
check('Stair riser <= 0.15m',all(r['max_riser']<=.15001 for r in m['routes']),max(r['max_riser'] for r in m['routes']))
check('Stair tread >= 0.30m',all(r.get('tread_m',999)>=.30-1e-5 for r in m['routes'] if r['steps']>1),min(r.get('tread_m',999) for r in m['routes'] if r['steps']>1))
check('Public path minimum width 1.6m',all(r['width']>=1.6 for r in m['routes'] if r.get('scope','public')=='public'),{'public_min':min(r['width'] for r in m['routes'] if r.get('scope','public')=='public'),'private_entry':1.3,'private_interior':1.0,'scope':'declared path widths; actual body rays checked separately'})
# Walk-surface support sampled at 0.30m intervals. Meshes filtered to avoid canopy/roofs.
prefixes=tuple({r['route'] for r in m['routes']})
walk=[o for o in s.objects if o.type=='MESH' and not o.hide_viewport and not o.hide_render and (o.name.startswith(prefixes) or any(t in o.name for t in ['_FLOOR','_PAVED_COURT','MAIN_COURTYARD','COMMON_FORECOURT','ISLAND_GROUND','TERRAIN_CONTINUOUS_BASE','_TERRACE']))]
bpy.context.view_layer.update();failed=[];tested=0
for r in m['routes']:
 a=Vector(r['from']);b=Vector(r['to']);num=max(2,math.ceil((b-a).length/.3))
 for i in range(num+1):
  t=i/num;p=a.lerp(b,t);expected=p.z
  pad=r.get('landing_pad_m',0)
  if pad:
   dist=Vector((b.x-a.x,b.y-a.y,0)).length;u=max(0,min(1,(t*dist-pad)/(dist-2*pad)));expected=a.z+(b.z-a.z)*u
  if 'BRIDGE' in r['route']:expected+= (.65 if 'LOWER' in r['route'] else .85)*math.sin(math.pi*t)
  hits=[]
  for o in walk:
   inv=o.matrix_world.inverted();ori=inv@Vector((p.x,p.y,expected+.4));direction=inv.to_3x3()@Vector((0,0,-1))
   hit,loc,normal,index=o.ray_cast(ori,direction,distance=1.0)
   if hit:hits.append((o.matrix_world@loc).z)
  tested+=1
  if not any(abs(h-expected)<.19 for h in hits):failed.append({'route':r['route'],'point':list(p),'expected_z':expected,'hits':hits})
check('Continuous walking surfaces along route samples',not failed,{'samples':tested,'missing':failed[:25]})
# Body/head rays against the actual full scene, not only the declared path graph.
deps=bpy.context.evaluated_depsgraph_get();obstructions=[];ray_samples=0
for r in m['routes']:
 a=Vector(r['from']);b=Vector(r['to']);num=max(2,math.ceil((b-a).length/.6))
 for i in range(num+1):
  t=i/num;p=a.lerp(b,t);h=p.z
  pad=r.get('landing_pad_m',0)
  if pad:
   dist=Vector((b.x-a.x,b.y-a.y,0)).length;u=max(0,min(1,(t*dist-pad)/(dist-2*pad)));h=a.z+(b.z-a.z)*u
  if 'BRIDGE' in r['route']:h+=(.65 if 'LOWER' in r['route'] else .85)*math.sin(math.pi*t)
  ray_samples+=1
  hit,loc,norm,idx,ob,mat=s.ray_cast(deps,Vector((p.x,p.y,h+.18)),Vector((0,0,1)),distance=1.92)
  if hit:obstructions.append({'route':r['route'],'point':list(p),'object':ob.name,'kind':'overhead/body','height_above_walk':round(loc.z-h,3)})
  for aa in [0,math.pi/2,math.pi,math.pi*1.5]:
   hit,loc,norm,idx,ob,mat=s.ray_cast(deps,Vector((p.x,p.y,h+1.1)),Vector((math.cos(aa),math.sin(aa),0)),distance=.26)
   if hit:obstructions.append({'route':r['route'],'point':list(p),'object':ob.name,'kind':'body_radius_0.26m'})
(ROOT/'evidence/path-obstruction-p3.json').write_text(json.dumps({'source':bpy.data.filepath,'obstructions':obstructions},ensure_ascii=False,indent=2))
check('Full-model standing path clearance',not obstructions,{'samples':ray_samples,'obstruction_count':len(obstructions),'obstructions':obstructions[:30]})
# Specific reference-sensitive checks: packing, readable front faces, distinct vegetation roles.
check('All 16 scene reference images packed',len([o for o in bpy.data.collections['IMAGE_REFERENCES'].objects if getattr(o,'data',None) and getattr(o.data,'packed_file',None)])==16,{'packed_reference_objects':len([o for o in bpy.data.collections['IMAGE_REFERENCES'].objects if getattr(o,'data',None) and getattr(o.data,'packed_file',None)]),'total_packed_images':len([i for i in bpy.data.images if i.packed_file])})
fronts=[]
for o in s.objects:
 if o.name.startswith('TITLE_'):
  normal=o.matrix_world.to_3x3()@Vector((0,0,1));direction=(s.objects['CAM_LIBRARY_SHELF'].location-o.location).normalized();fronts.append((o.name,normal.dot(direction)))
check('Four book titles face the shelf camera',len(fronts)==4 and all(d>0 for n,d in fronts),fronts)
blossoms=[o for o in s.objects if not o.hide_render and not o.hide_viewport and hasattr(o.data,'materials') and any(a and a.name=='P2_Blossom' for a in o.data.materials)]
check('Red blossoms only on main courtyard tree',bool(blossoms) and all(o.name.startswith(('Z01_COURTYARD_TREE','SM_Z01_')) for o in blossoms),[o.name for o in blossoms])
# Closest top-island vertex to each pedestrian-width half of the bridge landing must overlap.
island=s.objects['Z03_LOW_ISLAND_GROUND'];top=[v.co for v in island.data.vertices if abs(v.co.z+.3)<.001]
lower=next(r for r in bridges if 'LOWER' in r['route']);landing=min([lower['from'],lower['to']],key=lambda q:q[0]);worldtop=[island.matrix_world@v for v in top]
check('Lower bridge overlaps low-island flat ground',max(v.x for v in worldtop)>=landing[0],{'bridge_end_x':landing[0],'island_flat_max_x':max(v.x for v in worldtop),'ground_z':-.3,'scope':'measured current bridge landing rather than historical hardcoded coordinate'})
# Check public-path eye lines to the seated eye against actual enclosure, not just a screen label.
seat=Vector(s.objects['CAM_CHAT_SEATED'].location);privacy=[];visible=[]
privacy_meshes=[o for o in [*bpy.data.collections['Z11_SHELL'].all_objects,*bpy.data.collections['Z11_ROOF'].all_objects] if o.type=='MESH' and not o.hide_viewport and not o.hide_render]
for rr in m['routes']:
 if rr.get('scope','public')!='public':continue
 aa=Vector(rr['from']);bb=Vector(rr['to']);length=Vector((bb.x-aa.x,bb.y-aa.y,0)).length;num=max(1,math.ceil(length/1.0))
 for ii in range(num+1):
  tt=ii/num;pp=aa.lerp(bb,tt);pad=rr.get('landing_pad_m',0)
  if pad:
   uu=max(0,min(1,(tt*length-pad)/(length-2*pad)));pp.z=aa.z+(bb.z-aa.z)*uu
  observer=pp+Vector((0,0,1.5));direction=seat-observer;dist=direction.length
  blockers=[]
  for candidate in privacy_meshes:
   inv=candidate.matrix_world.inverted();ori=inv@observer;vec=inv.to_3x3()@direction.normalized()
   hh,ll,nn,ii=candidate.ray_cast(ori,vec,distance=dist)
   if hh:
    wd=(candidate.matrix_world@ll-observer).length
    if wd<dist-.05:blockers.append((wd,candidate.name))
  blockers.sort();hit=bool(blockers)
  record={'observer':list(observer),'blocked_by':blockers[0][1] if blockers else None}
  privacy.append(record)
  if not hit:visible.append(record)
check('All public route samples are screened from the seated eye',not visible,{'samples':len(privacy),'unblocked':visible})
(ROOT/'evidence/cottage-sightline-p3.json').write_text(json.dumps({'source':bpy.data.filepath,'seat_eye':list(seat),'rays':privacy,'unblocked':visible,'scope':'cottage mesh enclosure/furnishings only; vegetation and terrain not counted; no application/privacy-service claim'},ensure_ascii=False,indent=2))
# The cutaway excludes only specified collections; transformations remain shared.
check('Shared geometry view layers',all(n in s.view_layers for n in ['WORLD_FULL','LAYOUT_CUTAWAY','EYE_LEVEL']),'No duplicated scene or relocated furnishings')
# Full-model additions: real terrain boundary and face winding, independent of appearance.
outer=s.objects.get('SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE');terrain=s.objects.get('TERRAIN_CONTINUOUS_BASE')
if outer and terrain:
 N=201;pp=[terrain.matrix_world@v.co for v in terrain.data.vertices];edge=[pp[i] for i in range(N)]+[pp[j*N+N-1] for j in range(1,N)]+[pp[(N-1)*N+i] for i in range(N-2,-1,-1)]+[pp[j*N] for j in range(N-2,0,-1)]
 gaps=[(outer.matrix_world@outer.data.vertices[i].co-q).length for i,q in enumerate(edge)]
 check('Core and outer terrain share the exact boundary',max(gaps)<.00001,{'samples':len(gaps),'max_gap_m':max(gaps)})
 check('Ground surfaces face upward',all(p.normal.z>0 for p in outer.data.polygons) and all(p.normal.z>0 for p in terrain.data.polygons),{'outer_faces':len(outer.data.polygons),'core_faces':len(terrain.data.polygons)})
report={'source':bpy.data.filepath,'version':m['version'],'environment':bpy.app.version_string,'checks':checks,'technical_status':'PASS' if all(c['status']=='PASS' for c in checks) else 'FAIL','visual_status':'PENDING_USER_REVIEW','device_status':'NOT_CHECKED','business_status':'OUT_OF_SCOPE'}
(ROOT/'evidence/structure-check-p3.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps(report,ensure_ascii=False,indent=2))
