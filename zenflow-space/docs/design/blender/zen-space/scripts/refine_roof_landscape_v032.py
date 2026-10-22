"""Rebuild concave hip roofs, naturalise terrace/pond transitions, and author warm, readable sunlight."""
import bpy,math,random,json,sys
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import box,beam
s=bpy.context.scene;out=R/'zen-space-p3-v032.blend'
if s.get('version')!='p3-v031' or out.exists():raise RuntimeError('Requires v031 and new output')
rng=random.Random(211032);C=bpy.data.collections.new('P6_GARDEN_INTEGRATION');s.collection.children.link(C);roof_report=[];stats={}
def material(n,col,rough=.7):
 m=bpy.data.materials.new(n);m.use_nodes=True;m.diffuse_color=(*col,1);p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(*col,1);p.inputs['Roughness'].default_value=rough;return m
def mesh(n,vs,fs,collection,mats):
 me=bpy.data.meshes.new(n+'_mesh');me.from_pydata(vs,[],fs);me.update();o=bpy.data.objects.new(n,me);collection.objects.link(o)
 for m in mats:me.materials.append(m)
 return o
wood=bpy.data.materials['P2_Cedar'];rock=bpy.data.materials['P4_MOSSY_BANK_ROCK'];stone=bpy.data.materials['P5_FINE_DRESSED_LIMESTONE']
tiles=[material('P6_FIRED_CLAY_'+str(i),col,.61+i*.025) for i,col in enumerate([(.105,.125,.132),(.12,.139,.145),(.09,.109,.116),(.13,.143,.145)])]
for m in tiles:
 p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Specular IOR Level'].default_value=.32
# Smooth, truly concave roof profiles replace the previous steep-eave, flattened-crown profile.
roofs=[o for o in s.objects if o.type=='MESH' and len(o.data.vertices)==20 and any(k in o.name for k in ['FULL_ROOF','VERANDA_ROOF','SIDE_ROOF','WALL_CAP','GATE_CAP']) and not o.hide_render]
for roof in roofs:
 collection=roof.users_collection[0];p0=[roof.matrix_world@v.co for v in roof.data.vertices];a,b,c,d=p0[:4];U=(b-a).normalized();V=(d-a).normalized();w=(b-a).length;dep=(d-a).length;center=(a+b+c+d)/4;zbase=center.z-.24;center.z=0;oldrise=max(p.z for p in p0)-zbase;rise=oldrise*(1.11 if min(w,dep)>6 else 1.02);hip=min(dep*.45,w*.23);flare=.16 if min(w,dep)>6 else .085
 def corner(j,t):
  x=[-1,1,1,-1][j]*(w/2-hip*t);y=[-1,-1,1,1][j]*dep/2*(1-t);z=zbase+rise*t**1.5+flare*(1-t)**5+.10*(1-t)**7
  return center+U*x+V*y+Vector((0,0,z))
 def surface(side,u,t):return corner(side,t).lerp(corner((side+1)%4,t),u)
 # All roof layers share the same new surface, including the wooden underside.
 vs=[];fs=[];rings=19
 for row in range(rings):
  t=.997*row/(rings-1)
  for j in range(4):vs.append(tuple(corner(j,t)))
 for row in range(rings-1):
  for j in range(4):aa=row*4+j;bb=row*4+(j+1)%4;fs.append((aa,bb,bb+4,aa+4))
 fs.append(tuple((rings-1)*4+j for j in range(4)));me=bpy.data.meshes.new(roof.name+'_P6_CONCAVE_SHELL');me.from_pydata(vs,[],fs);me.update();me.materials.append(tiles[0]);me.materials.append(wood);inv=roof.matrix_world.inverted()
 for v in me.vertices:v.co=inv@v.co
 roof.data=me
 for f in me.polygons:f.use_smooth=True
 for mod in roof.modifiers:
  if mod.type=='SOLIDIFY':mod.thickness=.115;mod.material_offset=1
 # Retain history geometry hidden; retire only this roof's generated clay tile derivative and ridge.
 retired=[]
 for ob in list(s.objects):
  if (ob.name.startswith('SM_'+roof.name+'_CURVED_CLAY_TILES') or ob.name==roof.name+'_RIDGE') and not ob.hide_render:ob.hide_render=True;ob.hide_viewport=True;retired.append(ob.name)
 vv=[];ff=[];mi=[];tilecount=0
 for side in range(4):
  eavewidth=(corner((side+1)%4,0)-corner(side,0)).length;run=(surface(side,.5,.997)-surface(side,.5,0)).length;ny=max(2,math.ceil(run/.30))
  for row in range(ny):
   t0=.997*row/ny;t1=min(.997,(row+1.13)*.997/ny);tm=(t0+t1)/2;width=(corner((side+1)%4,tm)-corner(side,tm)).length;nx=max(1,round(width/.235))
   for j in range(nx):
    u=(j+.5)/nx
    for cap in [False,True]:
     uc=u+(0.5/nx if cap else 0)
     if uc>=.998:continue
     half=min(.065 if cap else .10,width/(nx*2)*.92);base=len(vv);col=rng.choices([0,1,2,3],[.55,.20,.20,.05])[0]
     for t in [t0,t1]:
      cen=surface(side,uc,t);tangent=(surface(side,min(.999,uc+.0001),t)-surface(side,max(.001,uc-.0001),t)).normalized();slope=(surface(side,uc,min(.997,t+.0001))-surface(side,uc,max(0,t-.0001))).normalized();normal=tangent.cross(slope).normalized()
      if normal.z<0:normal=-normal
      for arc in range(7):
       q=-1+arc/3;h=math.sqrt(max(0,1-q*q))*.038 if cap else (1-math.sqrt(max(0,1-q*q)))*.017;vv.append(tuple(cen+tangent*q*half+normal*(h+.022)))
     for k in range(6):ff.append((base+k,base+k+1,base+k+8,base+k+7));mi.append(col)
     tilecount+=1
 o=mesh('SM_P6_'+roof.name+'_CONCAVE_PAN_CAP_TILES',vv,ff,collection,tiles)
 for p,slot in zip(o.data.polygons,mi):p.material_index=slot;p.use_smooth=True
 sol=o.modifiers.new('Fired clay thickness','SOLIDIFY');sol.thickness=.009;sol.offset=-1
 # Thin, dark eave fascia reads as a finished edge, instead of a floating sheet.
 for j in range(4):
  start=corner(j,0);end=corner((j+1)%4,0);mid=(start+end)/2;ob=beam('SM_P6_'+roof.name+'_EAVE_FASCIA',start-Vector((0,0,.045)),end-Vector((0,0,.045)),.055,collection,wood,12)
 # Segmented half-round ridge caps, with restrained turned-up terminals.
 length=w-2*hip;segments=max(2,round(length/.34));ridgez=zbase+rise+.055
 for k in range(segments):
  xx=-length/2+(k+.5)*length/segments;aa=center+U*(xx-length/segments/2-.012)+Vector((0,0,ridgez));bb=center+U*(xx+length/segments/2+.012)+Vector((0,0,ridgez));ob=beam('SM_P6_'+roof.name+'_RIDGE_CAP',aa,bb,.115 if dep>6 else .085,collection,tiles[1],14)
 for sign in [-1,1]:
  prev=center+U*(sign*(length/2-.08))+Vector((0,0,ridgez))
  for j in range(1,7):
   t=j/6;cur=center+U*(sign*(length/2+.45*t))+Vector((0,0,ridgez+.16*t*t));beam('SM_P6_'+roof.name+'_TURNED_RIDGE_END',prev,cur,.092 if dep>6 else .07,collection,tiles[0],12);prev=cur
 # Hip ridges follow the curvature and meet the main ridge, rather than ending in bald corners.
 for side in range(4):
  prev=corner(side,0)+Vector((0,0,.075))
  for j in range(1,22):
   cur=corner(side,.997*j/21)+Vector((0,0,.075));beam('SM_P6_'+roof.name+'_HIP_RIDGE',prev,cur,.069,collection,tiles[1],10);prev=cur
 roof['profile']='concave slope, thin timber eave, individually overlapping fired clay pan/cap tiles'
 roof_report.append({'roof':roof.name,'footprint_unchanged_m':[w,dep],'rise_m':rise,'hip_setback_m':hip,'tiles':tilecount,'retired':retired})
# Terrace skirts meet the actual hill rather than reading as freestanding concrete drums.
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];bvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons]);routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes']
def ground(x,y):
 hit,n,i,d=bvh.ray_cast(Vector((x,y,100)),Vector((0,0,-1)),250);return hit.z if hit else 0

def dist(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-8,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
def clear(x,y,r=.4):return all(dist(x,y,row['from'],row['to'])>row['width']/2+r for row in routes)
rocksource=s.objects['SM_Z02_NATURAL_BANK_STONE'];rockmeshes=[]
for ob in s.objects:
 if ob.name.startswith('SM_Z02_NATURAL_BANK_STONE') and ob.data not in rockmeshes:rockmeshes.append(ob.data)
rockmeshes=rockmeshes[:7]
def boulder(name,x,y,z,scale):
 ob=bpy.data.objects.new(name,rockmeshes[rng.randrange(len(rockmeshes))]);C.objects.link(ob);ob.location=(x,y,z);ob.scale=scale;ob.rotation_euler=(rng.uniform(-.1,.1),rng.uniform(-.1,.1),rng.random()*math.tau);return ob
terraces=[]
for name in ['Z08_TERRACE','Z09_TERRACE','Z10_TERRACE']:
 ob=s.objects[name];vv=[ob.matrix_world@v.co for v in ob.data.vertices];cx=sum(v.x for v in vv)/len(vv);cy=sum(v.y for v in vv)/len(vv);top=max(v.z for v in vv);radius=max(math.hypot(v.x-cx,v.y-cy) for v in vv);vs=[];fs=[];n=96;layers=6
 for j in range(layers):
  t=j/(layers-1)
  for i in range(n):
   a=i*math.tau/n;rim=radius*(1+.025*(1+math.sin(a*7)));rr=rim+t*(1.3+.3*math.sin(a*3));x=cx+math.cos(a)*rr;y=cy+math.sin(a)*rr;base=min(top-.14,ground(x,y)-.04);z=top*(1-t)+base*t+.10*math.sin(a*9+j)*math.sin(math.pi*t);vs.append((x,y,z))
 for j in range(layers-1):
  for i in range(n):a=j*n+i;b=j*n+(i+1)%n;fs.append((a,b,b+n,a+n))
 fs.append(tuple(range(n)));me=bpy.data.meshes.new(name+'_P6_GROUND_JOINED_OUTCROP');me.from_pydata(vs,[],fs);me.update();me.materials.append(rock);me.materials.append(stone)
 for p in me.polygons:p.material_index=1 if len(p.vertices)>4 else 0;p.use_smooth=len(p.vertices)==4
 inv=ob.matrix_world.inverted()
 for v in me.vertices:v.co=inv@v.co
 ob.data=me;uv=me.uv_layers.new()
 for p in me.polygons:
  for li in p.loop_indices:
   v=ob.matrix_world@me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(v.x*.65,v.y*.65)
 count=0
 for i in range(45):
  a=i*math.tau/45;rr=radius+rng.uniform(.1,1.4);x=cx+math.cos(a)*rr;y=cy+math.sin(a)*rr;r=rng.uniform(.5,1)
  if not clear(x,y,r*.8):continue
  z=ground(x,y);boulder('SM_P6_'+name+'_HILL_FOOT_ROCK',x,y,z+r*.22,(r,rng.uniform(.5,1),rng.uniform(.5,1)));count+=1
 terraces.append({'name':name,'top_unchanged_m':top,'natural_foot_rocks':count})
# Inward bank revetment conceals the long straight walkway foundations without narrowing paths.
controls=[(-42,-11),(-34,-15),(-23,-13),(-17,-8),(-16,6),(-18,17),(-23,24),(-34,27),(-41,20),(-44,5)];placed=0;plants=0;leafmeshes=[bpy.data.meshes['P5_SHARED_SHORE_LEAF_CLUSTER_'+str(i)] for i in range(3)]
for a,b in zip(controls,controls[1:]+controls[:1]):
 length=math.dist(a,b);steps=max(2,int(length/.82))
 for j in range(steps):
  t=(j+.5)/steps;x=a[0]*(1-t)+b[0]*t;y=a[1]*(1-t)+b[1]*t;to=Vector((-29-x,7-y));to.normalize();x+=to.x*rng.uniform(.5,1.25);y+=to.y*rng.uniform(.5,1.25);r=rng.uniform(.55,1.05)
  if not clear(x,y,r*.78+.15):continue
  z=ground(x,y);sz=max(.6,min(1.45,(-z+.25)/1.5));boulder('SM_P6_Z02_INNER_BANK_REVETMENT',x,y,z+sz*.52,(r,rng.uniform(.5,.95),sz));placed+=1
  if j%2==0:
   xx=x-to.x*.5;yy=y-to.y*.5;zz=ground(xx,yy)
   if zz>-.8 and clear(xx,yy,.25):
    ob=bpy.data.objects.new('SM_P6_Z02_BANK_FINE_PLANT',leafmeshes[j%3]);C.objects.link(ob);ob.location=(xx,yy,zz+.005);ob.rotation_euler.z=rng.random()*math.tau;ob.scale=(rng.uniform(1.7,2.8),)*3;plants+=1
# Planted pockets at the front and terrace transitions break the empty green slab while leaving the gate clear.
for cx,cy,rx,ry in [(-22,-24,5,2),(20,-24,4,2),(-34,29,3,3),(6,32,3,3),(30,31,3,3),(-18,3,1.4,4)]:
 for j in range(36):
  x=cx+rng.uniform(-rx,rx);y=cy+rng.uniform(-ry,ry)
  if not clear(x,y,.6):continue
  z=ground(x,y)
  if z<-1:continue
  ob=bpy.data.objects.new('SM_P6_GARDEN_TRANSITION_UNDERSTORY',leafmeshes[j%3]);C.objects.link(ob);ob.location=(x,y,z+.004);ob.rotation_euler.z=rng.random()*math.tau;ob.scale=(rng.uniform(1.8,3),)*3;plants+=1
  if j%12==0:boulder('SM_P6_GARDEN_TRANSITION_ROCK',x,y,z+.15,(.55,.45,.4))
# Satin timber separates dry stone, glazed clay and water highlights; retain original colour photographs.
floor=bpy.data.materials['P2_Cedar'].copy();floor.name='P6_HAND_FINISHED_SATIN_FLOOR';p=next(n for n in floor.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Roughness'].default_value=.32;p.inputs['Coat Weight'].default_value=.12;p.inputs['Coat Roughness'].default_value=.33
woodcount=0
for ob in s.objects:
 if ob.type=='MESH' and not ob.hide_render and ('FLOOR_PLANK' in ob.name or ob.name in ['Z01_VERANDA_FLOOR','Z04_FLOOR','Z05_FLOOR']):
  if ob.data.users>1:ob.data=ob.data.copy()
  ob.data.materials.clear();ob.data.materials.append(floor);woodcount+=1
p=next(n for n in bpy.data.materials['P2_Water'].node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Roughness'].default_value=.045;p.inputs['Transmission Weight'].default_value=.78;p.inputs['Base Color'].default_value=(.23,.39,.34,1);p.inputs['IOR'].default_value=1.333;p.inputs['Specular IOR Level'].default_value=.5
# Morning key: smaller sun disk gives legible, softly edged foliage and tile shadows.
sun=s.objects['DAY_SUN'];sun.rotation_euler=Vector((.46,.38,-.78)).to_track_quat('-Z','Y').to_euler();sun.data.energy=1.65;sun.data.color=(1,.87,.67);sun.data.angle=.035;s['day_sun_energy']=1.65;s['day_world_strength']=.42;s['day_exposure']=.32
cam=s.objects['CAM_WORLD_FULL'];cam.location=(48,-140,112);target=Vector((-5,8,2));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=116
s['version']='p3-v032';s['phase_status']='P6_ROOF_LANDSCAPE_LIGHT_REVIEW';s['roof_revision']='shared concave hip shells and continuous overlapping tiles, no per-camera roofs';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/roof-landscape-light-v032.json').write_text(json.dumps({'version':'p3-v032','base':'p3-v031','roofs':roof_report,'terraces':terraces,'inner_bank_rocks':placed,'new_transition_plants':plants,'satin_floor_objects':woodcount,'lighting':{'sun_energy':1.65,'sun_angle':.035,'sun_direction':[.46,.38,-.78],'world_strength':.42,'exposure':.32},'layout':'building locations, paths and all first-person cameras retained; overview camera adjusted only'},indent=2));print('ROOF_LANDSCAPE_LIGHT_SAVED',len(roof_report),placed,plants,woodcount,flush=True)
