"""Close measured wall-to-roof daylight gaps with fitted timber friezes, without touching public passage clearance."""
import bpy,json,math
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;assert s.get('version')=='p3-v047';out=R/'zen-space-p3-v048.blend';assert not out.exists();report=[]
# Actual wall rectangle dimensions from original building shells, retained through garden repairs.
for code,cx,cy,w,d in [('Z04',-11,-6.5,18,10.8),('Z05',12,-6.5,19.8,12.6),('Z06',20,16,7.2,5.4),('Z11',29,24,5.4,4.5)]:
 roof=s.objects[code+'_FULL_ROOF'];bvh=BVHTree.FromPolygons([roof.matrix_world@v.co for v in roof.data.vertices],[tuple(p.vertices) for p in roof.data.polygons]);corners=[(cx-w/2,cy-d/2),(cx+w/2,cy-d/2),(cx+w/2,cy+d/2),(cx-w/2,cy+d/2)];vs=[];fs=[];uvs=[];heights=[]
 for side in range(4):
  a=Vector((*corners[side],0));b=Vector((*corners[(side+1)%4],0));length=(b-a).length;N=max(2,math.ceil(length/.45))
  for i in range(N):
   aa=a.lerp(b,i/N);bb=a.lerp(b,(i+1)/N);top=[]
   for p in [aa,bb]:
    hit,n,ii,dist=bvh.ray_cast(Vector((p.x,p.y,20)),Vector((0,0,-1)),30);assert hit is not None,(code,list(p));top.append(hit.z-.10)
   start=len(vs);vs.extend([(aa.x,aa.y,3.38),(bb.x,bb.y,3.38),(bb.x,bb.y,top[1]),(aa.x,aa.y,top[0])]);fs.append((start,start+1,start+2,start+3));uvs.extend([(i*length/N*.5,0),((i+1)*length/N*.5,0),((i+1)*length/N*.5,(top[1]-3.38)*.5),(i*length/N*.5,(top[0]-3.38)*.5)]);heights+=top
 me=bpy.data.meshes.new(code+'_MEASURED_FRIEZE_PLANKS');me.from_pydata(vs,[],fs);me.update();me.materials.append(bpy.data.materials['P2_Cedar']);uv=me.uv_layers.new()
 for p in me.polygons:
  for li in p.loop_indices:uv.data[li].uv=uvs[me.loops[li].vertex_index]
 ob=bpy.data.objects.new('SM_P10_'+code+'_FITTED_WALL_HEAD_FRIEZE',me);roof.users_collection[0].objects.link(ob);mod=ob.modifiers.new('Actual timber thickness','SOLIDIFY');mod.thickness=.045;mod.offset=0;report.append({'building':code,'panels':len(fs),'wall_top_bottom':3.38,'fitted_top_range':[min(heights),max(heights)]})
s['version']='p3-v048';s['phase_status']='P10_REFERENCE_QUALITY_AND_WALL_HEAD_REPAIR';p=R/'manifest/scene-manifest.json';m=json.loads(p.read_text());m.update(version='p3-v048',source_version='p3-v048',source_snapshot=out.name);m['wall_head_repair']=report;p.write_text(json.dumps(m,ensure_ascii=False,indent=2));s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/wall-head-repair-v048.json').write_text(json.dumps(report,indent=2));print('WALL_HEADS_FITTED',len(report),flush=True)
