"""Physical overlapping clay tiles over the existing shared hip-roof surfaces."""
import bpy,math,json,random
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v009.blend'
if s.get('version')!='p3-v008' or out.exists():raise RuntimeError('Requires v008 and new output')
rng=random.Random(210);mats=[]
for i,col in enumerate([(.075,.082,.078),(.10,.105,.095),(.12,.117,.10),(.083,.09,.087)]):
 m=bpy.data.materials.new('P3_FIRED_GREY_TILE_'+str(i));m.diffuse_color=(*col,1);m.use_nodes=True;p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(*col,1);p.inputs['Roughness'].default_value=.83;mats.append(m)
retired=[]
for o in s.objects:
 if '_P2_TILE_COURSE' in o.name or ('_SEAM' in o.name and ('ROOF' in o.name or 'CAP' in o.name)):
  o.hide_render=True;o.hide_viewport=True;retired.append(o.name)
roofs=[o for o in s.objects if o.type=='MESH' and len(o.data.vertices)==20 and any(k in o.name for k in ['FULL_ROOF','VERANDA_ROOF','SIDE_ROOF','WALL_CAP','GATE_CAP','TAG_BOX_CAP']) and not o.hide_render]
report=[]
for roof in roofs:
 vv=[];ff=[];mi=[];tiles=0
 pts=[roof.matrix_world@v.co for v in roof.data.vertices]
 # Each original band is a genuine sloping roof quad; caps follow its tangent and pan profile.
 for p in roof.data.polygons:
  if len(p.vertices)!=4 or min(p.vertices)>=16:continue
  a,b,c,d=[pts[i] for i in p.vertices];wide=(b-a).length;deep=((d-a).length+(c-b).length)/2
  if wide<.08 or deep<.08:continue
  nx=max(1,round(wide/.235));ny=max(1,math.ceil(deep/.32))
  def point(u,v):return a.lerp(b,u).lerp(d.lerp(c,u),v)
  for row in range(ny):
   va=row/ny;vb=min(1,(row+1)/ny+.16/ny)
   for k in range(nx):
    # alternating concave pans and convex covering tiles, modest authentic projection.
    for cap in [False,True]:
     u=(k+(.5 if cap else 0))/nx
     if u<=0 or u>=1:continue
     half=(.065 if cap else .092)/wide
     base=len(vv)
     for v in [va,vb]:
      cen=point(u,v);tangent=(point(min(.999,u+.001),v)-point(max(.001,u-.001),v)).normalized();slope=(point(u,min(.999,v+.001))-point(u,max(.001,v-.001))).normalized();normal=tangent.cross(slope).normalized()
      if normal.z<0:normal=-normal
      for arc in range(7):
       x=-1+arc/3;h=(math.sqrt(max(0,1-x*x))*.043 if cap else (1-math.sqrt(max(0,1-x*x)))*.022)
       vv.append(tuple(cen+tangent*(x*half*wide)+normal*(h+.025+(row%2)*.001)))
     for j in range(6):ff.append((base+j,base+j+1,base+j+8,base+j+7));mi.append(rng.randrange(len(mats)))
     tiles+=1
 me=bpy.data.meshes.new(roof.name+'_OVERLAPPING_TILE_MESH');me.from_pydata(vv,[],ff);me.update();o=bpy.data.objects.new('SM_'+roof.name+'_CURVED_CLAY_TILES',me);roof.users_collection[0].objects.link(o)
 for m in mats:me.materials.append(m)
 for p,i in zip(me.polygons,mi):p.material_index=i;p.use_smooth=True
 sol=o.modifiers.new('Actual clay thickness','SOLIDIFY');sol.thickness=.011;sol.offset=-1
 o['construction']='independent curved pan and cap surfaces, overlapping courses over existing hip profile';o['tile_count']=tiles
 report.append({'roof':roof.name,'tiles':tiles,'faces':len(ff)})
s['version']='p3-v009';s['phase_status']='P3_ROOF_DETAIL_REVIEW_PENDING';bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/curved-roof-construction.json').write_text(json.dumps({'version':'p3-v009','roofs':report,'retired_previous_courses':len(retired),'all_views_share_geometry':True},indent=2));print('CURVED_ROOFS',len(report),sum(x['tiles'] for x in report),flush=True)
