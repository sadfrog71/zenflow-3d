import bpy,json,math,random
from pathlib import Path
from mathutils import Vector,Matrix
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;assert s.get('version')=='p3-v051';out=R/'zen-space-p3-v052.blend';assert not out.exists()
# Generate an independently branched second landmark, retaining its root, height and intended crown width.
text=(R/'scripts/rebuild_botanical_trees_v051.py').read_text();ns={'bpy':bpy,'math':math,'random':random,'Vector':Vector,'bark':bpy.data.materials[s.objects['SM_P12_Z09_LANDSCAPE_TREE_WOOD'].data.materials[0].name],'greens':[bpy.data.materials['P12_LIVING_LEAF_'+str(i)] for i in range(6)],'blossom':bpy.data.materials['P2_Blossom'],'prototypes':{}}
exec(text[text.index('def make_proto('):text.index('for i in range(3):make_proto')],ns);info=ns['make_proto']('OLD_WIDE_B',5271,'broad',True)
for role,me in info['parts'].items():
 o=s.objects.get('SM_P12_Z09_LANDSCAPE_TREE_'+role)
 if not o:continue
 loc,rot,sc=o.matrix_world.decompose();o.data=me;o.matrix_world=Matrix.Translation(loc)@rot.to_matrix().to_4x4()@Matrix.Diagonal((12/info['width'],12/info['width'],10.1/info['height'],1));o['prototype']='OLD_WIDE_B'
# More coherent foliage coverage at the unchanged leaf centres; keep true spatial crown gaps.
meshes={o.data for o in s.objects if o.type=='MESH' and not o.hide_render and o.data.name.startswith('P12_') and 'FOLDED_LEAVES' in o.data.name};stats=[]
for old in meshes:
 vs=[];fs=[];mi=[];count=len(old.vertices)//6
 for k in range(count):
  q=[old.vertices[k*6+j].co.copy() for j in range(6)];center=(q[0]+q[2])*.5;u=(q[2]-q[0]).normalized();v=(q[1]-q[3]).normalized();n=u.cross(v).normalized();pine='PINE' in old.name
  for j in range(5):
   delta=q[j]-center;du=delta.dot(u);dv=delta.dot(v);dn=delta.dot(n);vs.append(tuple(center+u*du*1.17+v*dv*(1.65 if not pine else 1.45)+n*dn))
  b=k*5;fs.extend([(b,b+1,b+4),(b+1,b+2,b+4),(b+2,b+3,b+4),(b+3,b,b+4)]);mi.extend([old.polygons[k*8+j].material_index for j in range(4)])
 me=bpy.data.meshes.new(old.name+'_COVERAGE_FINISH');me.from_pydata(vs,[],fs);me.update()
 for m in old.materials:me.materials.append(m)
 for p,slot in zip(me.polygons,mi):p.material_index=slot;p.use_smooth=True
 users=[o for o in s.objects if o.type=='MESH' and o.data==old]
 for o in users:o.data=me
 stats.append({'prototype':old.name,'leaves':count,'instances':len(users),'triangles_per_leaf':4,'opaque_leaf_geometry':True})
for i in range(6):
 m=bpy.data.materials['P12_LIVING_LEAF_'+str(i)];p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');col=p.inputs['Base Color'].default_value;p.inputs['Base Color'].default_value=(min(.30,col[0]*1.35),min(.34,col[1]*1.35),min(.18,col[2]*1.25),1);p.inputs['Subsurface Weight'].default_value=.09;m.diffuse_color=p.inputs['Base Color'].default_value;m.use_backface_culling=False
# Several flowers at each existing terminal cluster, with restrained coral colour and no emission.
m=bpy.data.materials['P2_Blossom'];p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(.64,.13,.07,1);p.inputs['Roughness'].default_value=.58
ob=s.objects['SM_Z01_BOTANICAL_FLOWERS'];old=ob.data;vs=[tuple(v.co) for v in old.vertices];fs=[tuple(p.vertices) for p in old.polygons];rng=random.Random(52052)
for copy in range(2):
 for start in range(0,len(old.vertices),6):
  delta=Vector((rng.uniform(-.14,.14),rng.uniform(-.14,.14),rng.uniform(-.05,.10)));b=len(vs)
  for j in range(6):vs.append(tuple(old.vertices[start+j].co+delta))
  for j in range(5):fs.append((b,b+1+j,b+1+(j+1)%5))
me=bpy.data.meshes.new('P12_COURT_BRANCH_TIP_FLOWER_GROUPS');me.from_pydata(vs,[],fs);me.update();me.materials.append(m);ob.data=me
# Finer bark repetition along the circumference, with existing licensed source texture untouched.
woodmeshes={o.data for o in s.objects if o.type=='MESH' and not o.hide_render and o.name.startswith(('SM_P12_','SM_Z01_BOTANICAL_WOOD')) and o.data.uv_layers.active}
for me in woodmeshes:
 for u in me.uv_layers.active.data:u.uv.x*=2.5
s['version']='p3-v052';s['phase_status']='P12_IMAGEGEN_BOTANICAL_COMPARISON';p=R/'manifest/scene-manifest.json';d=json.loads(p.read_text());d.update(version='p3-v052',source_version='p3-v052',source_snapshot=out.name);d['botanical_tree_revision']['prototype_count']=7;d['botanical_tree_revision']['landmark_skeletons_independent']=True;d['botanical_tree_revision']['coverage_finish']='1.17 blade length, 1.65 broadleaf width; redundant reverse triangles removed';p.write_text(json.dumps(d,ensure_ascii=False,indent=2));s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/botanical-canopy-finish-v052.json').write_text(json.dumps({'version':'p3-v052','leaf_prototypes':stats,'landmark_skeleton':'independent second old tree','court_flower_faces':len(fs),'source_layout_and_lighting_unchanged':True},indent=2));print('CANOPY_FINISH',len(stats),len(fs),flush=True)
