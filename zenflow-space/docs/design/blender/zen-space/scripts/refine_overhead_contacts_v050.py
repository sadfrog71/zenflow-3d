import bpy,json,math
from pathlib import Path
from mathutils import Vector,Matrix
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;assert s.get('version')=='p3-v049';out=R/'zen-space-p3-v050.blend';assert not out.exists();base=json.loads(Path('/tmp/overhead-baseline-048-geometry.json').read_text());mfile=R/'manifest/scene-manifest.json';m=json.loads(mfile.read_text());routes=m['routes']
def sm(t):t=max(0,min(1,t));return t*t*(3-2*t)
def dist(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-8,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
def clear(x,y):return all(dist(x,y,r['from'],r['to'])>r['width']/2+.8 for r in routes)
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];changes=0
for v,old in zip(terrain.data.vertices,base['TERRAIN_CONTINUOUS_BASE']):
 q=terrain.matrix_world@Vector(old);x,y,z=q;d=math.hypot(x-25,(y-35)*1.08)
 if d>=12.8:continue
 q.z=z
 if clear(x,y):
  dx=max(24-x,0,x-34);dy=max(20-y,0,y-29.5);building_fade=sm(math.hypot(dx,dy)/3.0);w=sm((12.8-d)/6.4)*building_fade;q.z=z+(max(z,7.1)-z)*w
 if abs(q.z-z)>.001:changes+=1
 v.co=terrain.matrix_world.inverted()@q
terrain.data.update();bvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(p.vertices) for p in terrain.data.polygons]);baseverts=[terrain.matrix_world@Vector(v) for v in base['TERRAIN_CONTINUOUS_BASE']];oldbvh=BVHTree.FromPolygons(baseverts,[tuple(p.vertices) for p in terrain.data.polygons])
def height(b,x,y):
 hit,n,i,d=b.ray_cast(Vector((x,y,80)),Vector((0,0,-1)),180);return hit.z if hit else None
rooted=0
for name,pos in base['roots'].items():
 o=s.objects.get(name)
 if not o or math.hypot(pos[0]-25,pos[1]-35)>13:continue
 a,b=height(oldbvh,*pos[:2]),height(bvh,*pos[:2]);o.location=pos
 if a is not None and b is not None:o.location.z+=b-a;rooted+=1
# Retain the previously cut terrace/stair join; reshape the land around it, not its opening.
terr=s.objects['Z10_TERRACE']
for v,co in zip(terr.data.vertices,base['Z10_TERRACE']):v.co=co
terr.data.update()
# Flat seat stays at -0.30; the shoreline expands through submerged, irregular stone shoulders.
N=96;vs=[];fs=[];rings=[(2.65,-.3),(3.03,-.57),(3.64,-1.13),(4.20,-1.7)]
for j,(radius,z) in enumerate(rings):
 for i in range(N):
  a=math.tau*i/N;wav=1+.045*math.sin(3*a+.3)+.025*math.sin(7*a);r=radius*wav;vs.append((-25+r*math.cos(a),3+r*math.sin(a),z))
fs.append(tuple(range(N)))
for j in range(len(rings)-1):
 for i in range(N):fs.append((j*N+i,(j+1)*N+i,(j+1)*N+(i+1)%N,j*N+(i+1)%N))
me=bpy.data.meshes.new('P11_LOW_ISLAND_SHOULDERED_STONE');me.from_pydata(vs,[],fs);me.update();me.materials.append(bpy.data.materials['P3_Weathered_Gray_Rock']);me.materials.append(bpy.data.materials['P4_MOSSY_BANK_ROCK']);uv=me.uv_layers.new()
for p in me.polygons:
 p.material_index=0 if len(p.vertices)>4 else 1
 for li in p.loop_indices:q=me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(q.x*.6,q.y*.6)
s.objects['Z03_LOW_ISLAND_GROUND'].data=me
for name,pos in base['flowers'].items():
 if name in s.objects:s.objects[name].location=pos
cam=s.objects['CAM_GARDEN_COMPOSITION'];cam.data.ortho_scale=90;cam.location=(8,-148,113);cam.rotation_euler=(Vector((-6,11,2))-cam.location).to_track_quat('-Z','Y').to_euler()
s['version']='p3-v050';m.update(version='p3-v050',source_version='p3-v050',source_snapshot=out.name);m['overhead_contact_finish_v050']={'terrain_vertices_regraded':changes,'garden_instances_rerooted':rooted,'cottage_protection_rectangle':[24,34,20,29.5],'fade_distance_m':3,'island_rings_radius_z_m':rings,'flowers_original_positions_restored':len(base['flowers']),'lookout_stair_cut_geometry_restored':True,'composition_ortho_scale_m':90};mfile.write_text(json.dumps(m,ensure_ascii=False,indent=2));s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/overhead-contact-finish-v050.json').write_text(json.dumps(m['overhead_contact_finish_v050'],indent=2));print('OVERHEAD_CONTACT_FINISH',changes,rooted,flush=True)
