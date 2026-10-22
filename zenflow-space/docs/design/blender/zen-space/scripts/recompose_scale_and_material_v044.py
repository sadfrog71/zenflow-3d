"""Coherent bridge/shore deformation, structural roof massing and multiscale physical surface refinement."""
import bpy,math,json,random,shutil
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v044.blend'
assert s.get('version')=='p3-v042' and not out.exists()
p=R/'manifest/scene-manifest.json';m=json.loads(p.read_text());backup=R/'manifest/scene-manifest-p3-v042.json'
if not backup.exists():shutil.copy2(p,backup)
routes=m['routes'];rng=random.Random(211043)
def sm(t):t=max(0,min(1,t));return t*t*(3-2*t)
def warp(q):
 x,y,z=q;wl=sm((y+5)/4.5)*sm((10.5-y)/4)
 if x<=-36:dx=0
 elif x<-28:dx=3*(x+36)/8
 elif x<-26.3:dx=3
 elif x<-15.8:dx=3*(-15.8-x)/10.5
 else:dx=0
 wu=sm((y-10.5)/6)*sm((29.5-y)/10)
 if x<=-50 or x>=-16.5:dxu=dyu=0
 elif x<-36.5:t=(x+50)/13.5;dxu=4.5*t;dyu=4*t
 elif x<-19:t=(x+36.5)/17.5;dxu=4.5-9.5*t;dyu=4-2*t
 else:t=(x+19)/2.5;dxu=-5*(1-t);dyu=2*(1-t)
 return Vector((x+dx*wl+wu*dxu,y+wu*dyu,z))
def distance(q,a,b):
 d=b-a;t=max(0,min(1,(Vector((q.x-a.x,q.y-a.y,0))).dot(Vector((d.x,d.y,0)))/max(1e-8,d.x*d.x+d.y*d.y)));v=a.lerp(b,t);return math.hypot(q.x-v.x,q.y-v.y),t,v
# Physically widen the affected original route strips before shortening; declarations alone are insufficient.
widen=[]
for row in routes:
 if row['route']=='ISLAND_SHORE_LINK':widen.append((row,2.0))
 elif row['route']=='EAST_BANK_TO_UPPER' and row['to'][0]<-18:widen.append((row,2.5))
net=s.objects['PUBLIC_EAST_ROUTE_RESOLVED_NETWORK'];inv=net.matrix_world.inverted();widened=0
# Refine the actual connected ground mesh so curved bank segments cannot cut across sparse old triangles.
import bmesh
bm=bmesh.new();bm.from_mesh(net.data);bmesh.ops.triangulate(bm,faces=list(bm.faces))
for _ in range(6):
 edges=[e for e in bm.edges if (e.verts[0].co-e.verts[1].co).length>.6 and any(-55<(net.matrix_world@v.co).x<-16.4 and -5<(net.matrix_world@v.co).y<30 and (net.matrix_world@v.co).z<.2 for v in e.verts)]
 if not edges:break
 bmesh.ops.subdivide_edges(bm,edges=edges,cuts=1,use_grid_fill=True)
bm.to_mesh(net.data);bm.free();net.data.update()
def surfacewarp(q):
 new=warp(q)
 if q.y>10.5 and q.z>.025:new=q+(new-q)*sm((.25-q.z)/.225)
 return new
for v in net.data.vertices:
 q=net.matrix_world@v.co;hits=[]
 for row,width in widen:
  a,b=Vector(row['from']),Vector(row['to']);d,t,c=distance(q,a,b)
  if d<row['width']/2+.06 and abs(q.z-c.z)<.20:hits.append((d,row,width,c))
 if hits:
  d,row,width,c=min(hits,key=lambda h:h[0]);f=width/row['width'];q.x=c.x+(q.x-c.x)*f;q.y=c.y+(q.y-c.y)*f;v.co=inv@q;widened+=1
# Broad surfaces/bridge components deform at vertices, while shared dressing meshes move by their roots.
explicit={'TERRAIN_CONTINUOUS_BASE','PUBLIC_EAST_ROUTE_RESOLVED_NETWORK','WATER_LOWER_LEVEL_-1.20','Z03_LOW_ISLAND_GROUND','Z02_LOWER_BRIDGE_DECK','Z02_UPPER_BRIDGE_DECK','Z08_TERRACE','Z09_TERRACE'}
changed=0;moved=[]
for o in list(s.objects):
 if o.hide_render or o.hide_viewport:continue
 if o.type=='MESH' and (o.name in explicit or o.name.startswith(('SM_Z02_BRIDGE_STONE_PARAPET','SM_P5_BRIDGE_FITTED_PAVING','SM_P5_BRIDGE_VOUSSOIR_JOINT'))):
  inv=o.matrix_world.inverted()
  if o.data.users>1:o.data=o.data.copy()
  for v in o.data.vertices:
   q=o.matrix_world@v.co;new=surfacewarp(q) if o.name in ['TERRAIN_CONTINUOUS_BASE','PUBLIC_EAST_ROUTE_RESOLVED_NETWORK','Z08_TERRACE','Z09_TERRACE'] else warp(q)
   if (new-q).length>1e-6:v.co=inv@new;changed+=1
  o.data.update()
 elif o.type in ['MESH','EMPTY'] and o.parent is None and o.animation_data is None:
  # Architectural kit must stay at its foundation; garden-only objects and low cushion follow the shore.
  garden=o.name.startswith(('SM_Z02_','SM_P6_Z02_','SM_P7_Z02_','SM_ROCK_','SM_GROUND_','SM_P6_GARDEN_','SM_P7_GARDEN_','SM_P7_POND_PATH_RUBBLE_SUPPORT','SM_P8_','SM_P6_Z08_','SM_P7_Z08_')) or o.name=='Z03_ONLY_ROUND_CUSHION'
  if garden:
   q=o.matrix_world.translation;new=warp(q)
   if (new-q).length>1e-6:o.location+=new-q;moved.append(o.name)
for o in s.objects:
 if o.type=='CAMERA' and o.name.startswith(('CAM_ISLAND','CAM_LOW_ISLAND')):o.location=warp(o.location)
# Curved route definition follows the actual geometry; keep whole bridge rows for their single arch parameter.
newroutes=[];widths=[]
for row in routes:
 a,b=Vector(row['from']),Vector(row['to']);base_width=row['width'];
 if row['steps']>1:newroutes.append(row);continue
 working_width=next((w for r,w in widen if r is row),base_width)
 N=1 if 'BRIDGE' in row['route'] else max(1,math.ceil((b-a).length/.65))
 if all((warp(a.lerp(b,i/10))-a.lerp(b,i/10)).length<1e-6 for i in range(11)):newroutes.append(row);continue
 actual=[]
 d=Vector((b.x-a.x,b.y-a.y,0)).normalized();n=Vector((-d.y,d.x,0))
 for i in range(41):
  q=a.lerp(b,i/40);tangent=(warp(q+d*.005)-warp(q-d*.005)).normalized();nn=Vector((-tangent.y,tangent.x,0));across=warp(q+n*working_width/2)-warp(q-n*working_width/2);actual.append(abs(across.dot(nn)))
 width=min(base_width,min(actual)) if working_width==base_width else min(actual)
 if 'UPPER_BRIDGE' in row['route']:width=min(width,2.1)
 if row.get('scope','public')=='public':assert width>=1.5999,(row['route'],width)
 widths.append({'route':row['route'],'old_width':base_width,'physically_widened_before_mapping_to':working_width,'minimum_sampled_edge_projection':min(actual),'registered_width':width})
 for i in range(N):
  rr=dict(row);rr['from']=list(warp(a.lerp(b,i/N)));rr['to']=list(warp(a.lerp(b,(i+1)/N)));rr['width']=max(1.6,width) if row.get('scope','public')=='public' else width;rr['curve_source']='shared bridge/shore deformation v044';newroutes.append(rr)
m['routes']=newroutes;m['version']='p3-v044';m['source_version']='p3-v044';m['source_snapshot']=out.name
# Main roofs: reduce excessive apron depth and lift ridge coherently, including all authored clay/timber derivatives.
roofstats=[]
for name in ['Z04_FULL_ROOF','Z05_FULL_ROOF','Z06_FULL_ROOF','Z11_FULL_ROOF']:
 roof=s.objects[name];pts=[roof.matrix_world@v.co for v in roof.data.vertices];cx=(min(v.x for v in pts)+max(v.x for v in pts))/2;cy=(min(v.y for v in pts)+max(v.y for v in pts))/2
 affected=[o for o in s.objects if o.type=='MESH' and not o.hide_render and (o==roof or o.name.startswith('SM_P6_'+name+'_'))];count=0
 for o in affected:
  if o.data.users>1:o.data=o.data.copy()
  inv=o.matrix_world.inverted()
  for v in o.data.vertices:
   q=o.matrix_world@v.co;q.x=cx+(q.x-cx)*.965;q.y=cy+(q.y-cy)*.91;q.z+=.18*max(0,q.z-3.9);v.co=inv@q;count+=1
  o.data.update()
 roofstats.append({'roof':name,'components':len(affected),'vertices':count,'xy_scale':[.965,.91],'ridge_rise_above_eave_scale':1.18})
# Retain scanned textures, adding coherent world-space variation instead of replacing them with flat colour.
def principal(m):return next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
def shade_variation(m,scale,lo,hi,roughlo,roughhi,strength=0):
 nt=m.node_tree;p=principal(m);geo=nt.nodes.new('ShaderNodeNewGeometry');noise=nt.nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=scale;noise.inputs['Detail'].default_value=3;nt.links.new(geo.outputs['Position'],noise.inputs['Vector']);r=nt.nodes.new('ShaderNodeValToRGB');r.color_ramp.elements[0].position=.22;r.color_ramp.elements[0].color=(*lo,1);r.color_ramp.elements[1].position=.78;r.color_ramp.elements[1].color=(*hi,1);nt.links.new(noise.outputs['Fac'],r.inputs['Fac']);mix=nt.nodes.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=1
 if p.inputs['Base Color'].links:nt.links.new(p.inputs['Base Color'].links[0].from_socket,mix.inputs[1])
 else:mix.inputs[1].default_value=p.inputs['Base Color'].default_value
 nt.links.new(r.outputs['Color'],mix.inputs[2]);nt.links.new(mix.outputs[0],p.inputs['Base Color']);rm=nt.nodes.new('ShaderNodeMapRange');rm.inputs['From Min'].default_value=0;rm.inputs['From Max'].default_value=1;rm.inputs['To Min'].default_value=roughlo;rm.inputs['To Max'].default_value=roughhi;nt.links.new(noise.outputs['Fac'],rm.inputs['Value']);nt.links.new(rm.outputs['Result'],p.inputs['Roughness'])
 if strength:
  micro=nt.nodes.new('ShaderNodeTexNoise');micro.inputs['Scale'].default_value=65;nt.links.new(geo.outputs['Position'],micro.inputs['Vector']);bump=nt.nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=strength;bump.inputs['Distance'].default_value=.012
  if p.inputs['Normal'].links:nt.links.new(p.inputs['Normal'].links[0].from_socket,bump.inputs['Normal'])
  nt.links.new(micro.outputs['Fac'],bump.inputs['Height']);nt.links.new(bump.outputs['Normal'],p.inputs['Normal'])
for name in ['P5_FINE_DRESSED_LIMESTONE','P3_Weathered_Gray_Rock']:
 shade_variation(bpy.data.materials[name],.55,(.48,.49,.45),(.88,.86,.77),.60,.89,.12)
shade_variation(bpy.data.materials['P4_PHOTOGRAPHED_LEAFY_GROUND'],.22,(.40,.46,.30),(.91,.87,.68),.79,.98)
shade_variation(bpy.data.materials['P4_MOSSY_BANK_ROCK'],.75,(.47,.53,.47),(.92,.92,.83),.37,.84,.15)
for ma in bpy.data.materials:
 if ma.name.startswith('P6_FIRED_CLAY_'):
  pp=principal(ma);col=pp.inputs['Base Color'].default_value;pp.inputs['Base Color'].default_value=(*(c*.60 for c in col[:3]),1);pp.inputs['Specular IOR Level'].default_value=.38;shade_variation(ma,3.5,(.76,.78,.79),(1.12,1.10,1.04),.42,.66,.14)
wood=bpy.data.materials['P2_Cedar']
for n in wood.node_tree.nodes:
 if n.type=='HUE_SAT':n.inputs['Value'].default_value=1.22
principal(wood).inputs['Roughness'].default_value=.47;principal(wood).inputs['Coat Weight'].default_value=.10;principal(wood).inputs['Coat Roughness'].default_value=.35
s['day_sun_energy']=2.3;s['day_world_strength']=.42;s['day_exposure']=.38;s.objects['DAY_SUN'].data.energy=2.3;s.objects['DAY_SUN'].data.angle=.025;s.view_settings.exposure=.38;next(n for n in s.world.node_tree.nodes if n.type=='BACKGROUND').inputs['Strength'].default_value=.42
m['quality_review_v044']={'bridge_and_shore_recomposition':True,'roof_mass_corrections':roofstats,'quality_benchmark':'official Game Science images via Unreal Engine and PlayStation; reference quality only, original v8 garden layout remains governing'}
p.write_text(json.dumps(m,ensure_ascii=False,indent=2));s['version']='p3-v044';s['phase_status']='P9_REFERENCE_QUALITY_REVIEW';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/recomposition-v044.json').write_text(json.dumps({'version':'p3-v044','deformed_vertices':changed,'widened_network_vertices':widened,'moved_garden_roots':moved,'width_samples':widths,'roof_revisions':roofstats,'bridge_lengths':[{'route':rr['route'],'length':(Vector(rr['to'])-Vector(rr['from'])).length} for rr in newroutes if 'BRIDGE' in rr['route']]},ensure_ascii=False,indent=2));print('V043_SAVED',changed,widened,len(moved),len(newroutes),flush=True)
