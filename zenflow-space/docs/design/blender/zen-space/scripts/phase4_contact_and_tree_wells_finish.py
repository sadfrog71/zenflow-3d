"""Physical contact audit corrections and planted tree wells, preserving all public routes."""
import bpy,math,json,sys
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import box,beam,assign
s=bpy.context.scene;out=R/'zen-space-p3-v028.blend'
if s.get('version')!='p3-v027' or out.exists():raise RuntimeError('Requires new v028 from v027')
# The old cup had 5mm of empty air; bird children had a 42.5mm floor offset while its animation pivot was valid.
cup=s.objects['Z01_P2_CERAMIC_CUP'];cup.location.z-=.005
bird=s.objects['ANCHOR_Z01_SPARROW']
for child in bird.children:child.location.z-=.0425
C=bpy.data.collections.new('P4_TREE_WELLS_AND_CONTACT_DETAILS');s.collection.children.link(C);soil=bpy.data.materials['P4_PHOTOGRAPHED_FOREST_SOIL'];stone=bpy.data.materials['P3_Weathered_Gray_Rock'];m=json.loads((R/'manifest/scene-manifest.json').read_text())
def dseg(x,y,r):
 a,b=r['from'],r['to'];dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(.00001,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy),a[2]+t*(b[2]-a[2])
wells=[]
for code,x,y,z,size in [('Z01',0,9,0,2.2),('Z08',-26,31,4.5,2.3),('Z09',0,36,7.5,3.0)]:
 gaps=[]
 for r in m['routes']:
  d,zz=dseg(x,y,r)
  if abs(zz-z)<1.0:gaps.append(d-r['width']/2)
 clearance=min(gaps) if gaps else 99
 # Raised edge only when wholly clear of the existing walking envelope; otherwise keep a flush planting well.
 edgeheight=.16 if clearance>size*.72+.2 else .035
 box('SM_'+code+'_TREE_WELL_SOIL',(x,y,z+.011),(size-.22,size-.22,.016),C,soil,.025)
 for sign in [-1,1]:
  box('SM_'+code+'_TREE_WELL_STONE_EDGE',(x+sign*(size/2-.06),y,z+edgeheight/2),(.12,size,edgeheight),C,stone,.018)
  box('SM_'+code+'_TREE_WELL_STONE_EDGE',(x,y+sign*(size/2-.06),z+edgeheight/2),(size-.24,.12,edgeheight),C,stone,.018)
 wells.append({'zone':code,'size_m':size,'edge_height_m':edgeheight,'nearest_same_level_route_edge_m':clearance})
# Increase only the landmark old/wish trunks below their first forks, keeping all crown/branch connections fixed.
flare=[]
for name,factor in [('SM_Z09_LANDSCAPE_TREE_ALIGNED_TRUNK',2.4),('SM_Z08_WISH_TREE_ALIGNED_TRUNK',1.8)]:
 ob=s.objects[name];ob.data=ob.data.copy();me=ob.data;zs=[v.co.z for v in me.vertices];z0,z1=min(zs),max(zs);span=z1-z0;bins=48;centers=[]
 for i in range(bins):
  z=z0+(i+.5)*span/bins;vs=[v.co for v in me.vertices if abs(v.co.z-z)<span/bins]
  centers.append(Vector((sum(v.x for v in vs)/len(vs),sum(v.y for v in vs)/len(vs))) if vs else None)
 for i,c in enumerate(centers):
  if c is None:centers[i]=min([(abs(j-i),q) for j,q in enumerate(centers) if q is not None],key=lambda p:p[0])[1]
 for v in me.vertices:
  t=(v.co.z-z0)/max(.001,span);weight=max(0,1-t/.58)**2
  if not weight:continue
  i=min(bins-1,max(0,int(t*bins)));center=centers[i];delta=v.co.xy-center;scale=1+(factor-1)*weight;v.co.x=center.x+delta.x*scale;v.co.y=center.y+delta.y*scale
 me.update();flare.append({'object':name,'base_radius_factor':factor,'forks_and_canopy_preserved':True})
# Connect the authored reachable wish branches to the actual imported trunk surface.
trunk=s.objects['SM_Z08_WISH_TREE_ALIGNED_TRUNK'];target=Vector((-26,31,6.75));local=trunk.matrix_world.inverted()@target;hit,point,normal,index=trunk.closest_point_on_mesh(local)
if hit:
 anchor=trunk.matrix_world@point
 if (anchor-target).length>.02:beam('SM_Z08_LOW_BRANCH_TRUNK_CONNECTOR',anchor,target,.045,C,bpy.data.materials['P2_Darkwood'],12)
s['version']='p3-v028';s['phase_status']='P4_MODEL_AND_FIDELITY_REVIEW';s.frame_set(1);s.camera=s.objects['CAM_ENTRY_SEATED'];t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v027','p3-v028')+'\nCup and sparrow foot contacts corrected. Landmark trees have explicit planting wells; old/wish trunks broaden only below their original forks.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
for fn in ['scene-manifest.json','object-animation-clips.json','weather-and-layout-states.json','handedness-layout-states.json']:
 p=R/'manifest'/fn;x=json.loads(p.read_text());x['source_version']='p3-v028'
 if fn=='scene-manifest.json':x['version']='p3-v028';x['source_snapshot']=out.name
 p.write_text(json.dumps(x,ensure_ascii=False,indent=2))
(R/'evidence/contact-and-tree-well-repair.json').write_text(json.dumps({'version':'p3-v028','cup_lowering_m':.005,'bird_child_geometry_lowering_m':.0425,'bird_animation_pivot_and_clips_unchanged':True,'tree_wells':wells,'landmark_trunk_flare':flare,'private_content_included':False},indent=2));print('CONTACT_WELLS_FINISHED',wells,flare,flush=True)
