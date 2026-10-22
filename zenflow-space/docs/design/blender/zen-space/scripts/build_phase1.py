"""Zenflow Space phase 1. Fresh-file builder; never run against hand-edited master.
Creates a new versioned file and refuses to overwrite it. Metres, +Y north.
"""
import bpy, math, random, json, csv, sys, hashlib
from pathlib import Path
from mathutils import Vector, Matrix
R=random.Random(20261020)
ROOT=Path(__file__).resolve().parents[1]
VERSION='p1-v003'
OUT=ROOT/f'zen-space-{VERSION}.blend'
if OUT.exists(): raise RuntimeError('Version exists. Use next version or edit master in Blender.')
# This builder is used only with --factory-startup. Protect opened projects.
if bpy.data.filepath: raise RuntimeError('Builder requires an unsaved factory startup file')
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
scene=bpy.context.scene
scene.unit_settings.system='METRIC'; scene.unit_settings.scale_length=1
scene.render.engine='BLENDER_WORKBENCH'
scene.render.resolution_x=1600; scene.render.resolution_y=1000; scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.render.film_transparent=False
scene.display.shading.light='STUDIO'; scene.display.shading.color_type='MATERIAL'
scene.display.shading.show_shadows=True; scene.display.shading.show_cavity=True
scene.display.shading.cavity_type='BOTH'; scene.display.shading.curvature_ridge_factor=1.3
scene.display.shading.curvature_valley_factor=1.0
scene.display.shading.show_specular_highlight=True
scene.display.shading.background_type='WORLD'; scene.world.color=(0.15,0.18,0.20)
scene.view_settings.view_transform='Standard'; scene.view_settings.look='Medium High Contrast' if 'Medium High Contrast' in [] else 'None'
scene.view_settings.exposure=0; scene.view_settings.gamma=1
COL={}
def col(name,parent=None):
 c=bpy.data.collections.new(name); (parent or scene.collection).children.link(c); COL[name]=c; return c
for n in ['00_REFERENCE','05_TERRAIN_WATER','10_ARCHITECTURE','20_PROPS_STATIC','30_PROPS_VARIANTS','35_VEGETATION','40_LIVING','50_INTERACTION','60_LIGHTING','70_CAMERAS','80_ANIMATION','90_EXPORT']: col(n)
REF=COL['00_REFERENCE']; TERR=COL['05_TERRAIN_WATER']; ARCH=COL['10_ARCHITECTURE']; PROP=COL['20_PROPS_STATIC']; VEG=COL['35_VEGETATION']; INT=COL['50_INTERACTION']; CAM=COL['70_CAMERAS']
LABEL=col('REGION_LABELS',REF); SCALE=col('HUMAN_SCALE',REF); REFS=col('IMAGE_REFERENCES',REF)
ROOFS=[]; ZONES={}; ASSETS=[]; CAMS=[]; ROUTES=[]
def mat(name,color):
 m=bpy.data.materials.new(name); m.diffuse_color=(*color,1); m.use_nodes=True
 m.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(*color,1)
 m.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=.85
 return m
M={k:mat(k,c) for k,c in {'Clay_Plaster':(.73,.72,.68),'Clay_Structure':(.52,.49,.43),'Clay_Roof':(.34,.38,.40),'Clay_Stone':(.58,.60,.59),'Clay_Ground':(.64,.65,.62),'Clay_Path':(.77,.77,.72),'Proxy_Water':(.38,.55,.59),'Proxy_Leaves':(.50,.57,.48),'Proxy_Blossom':(.61,.29,.27),'Clay_Trunk':(.43,.44,.41),'Clay_Paper':(.87,.85,.77),'Clay_Dark':(.20,.24,.25),'Clay_Seat':(.68,.65,.57),'Label':(.12,.19,.22)}.items()}
def link(obj,c,m=None):
 for cc in list(obj.users_collection): cc.objects.unlink(obj)
 c.objects.link(obj)
 if m:obj.data.materials.append(M[m] if isinstance(m,str) else m)
 obj['phase']='P1_GREYBOX'; obj['version']=VERSION
 return obj
def cube(n,loc,size,c=PROP,m='Clay_Stone',bevel=0):
 bpy.ops.mesh.primitive_cube_add(size=1,location=loc); o=bpy.context.object; o.name=n; o.dimensions=size
 bpy.ops.object.transform_apply(location=False,rotation=False,scale=True); link(o,c,m)
 if bevel:
  mod=o.modifiers.new('Editable_edge_radius','BEVEL'); mod.width=bevel; mod.segments=2
 return o
def cyl(n,loc,r,depth,c=PROP,m='Clay_Structure',verts=16):
 bpy.ops.mesh.primitive_cylinder_add(vertices=verts,radius=r,depth=depth,location=loc); o=bpy.context.object; o.name=n; return link(o,c,m)
def mesh(n,verts,faces,c,m):
 me=bpy.data.meshes.new(n); me.from_pydata(verts,[],faces); me.update(); o=bpy.data.objects.new(n,me); c.objects.link(o); o.data.materials.append(M[m]); o['phase']='P1_GREYBOX'; return o
def beam(n,a,b,r=.08,c=PROP,m='Clay_Structure'):
 a=Vector(a); b=Vector(b); o=cyl(n,(a+b)/2,r,(b-a).length,c,m); o.rotation_euler=(b-a).to_track_quat('Z','Y').to_euler(); return o
def line(n,pts,r=.025,c=PROP,m='Clay_Structure'):
 cv=bpy.data.curves.new(n,'CURVE');cv.dimensions='3D';cv.bevel_depth=r;cv.bevel_resolution=1
 s=cv.splines.new('POLY');s.points.add(len(pts)-1)
 for p,v in zip(s.points,pts):p.co=(*v,1)
 o=bpy.data.objects.new(n,cv);c.objects.link(o);cv.materials.append(M[m]);return o
def sphere(n,loc,scale,c=VEG,m='Proxy_Leaves',sub=1):
 bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=sub,radius=1,location=loc);o=bpy.context.object;o.name=n;o.scale=scale;return link(o,c,m)
def empty(n,loc,c=INT):
 o=bpy.data.objects.new(n,None); c.objects.link(o);o.location=loc;o.empty_display_type='SPHERE';o.empty_display_size=.14; return o
fontpath=next(iter(Path('/System/Library/Fonts').rglob('PingFang.ttc')),None)
FONT=bpy.data.fonts.load(str(fontpath)) if fontpath else None
def text(n,body,loc,size=.3,c=PROP,m='Label',rot=None):
 cv=bpy.data.curves.new(n,'FONT');cv.body=body;cv.size=size;cv.align_x='CENTER';cv.align_y='CENTER';cv.extrude=.001
 if FONT:cv.font=FONT
 o=bpy.data.objects.new(n,cv);c.objects.link(o);o.location=loc;cv.materials.append(M[m])
 if rot:o.rotation_euler=rot
 return o
# Terrain: pond basin is lower than water; continuous upland slope.
pond=[(-42,-11),(-34,-15),(-23,-13),(-17,-8),(-16,6),(-18,17),(-23,24),(-34,27),(-41,20),(-44,5)]
def inside(x,y,poly):
 hit=False;j=len(poly)-1
 for i,(xi,yi) in enumerate(poly):
  xj,yj=poly[j]
  if (yi>y)!=(yj>y) and x < (xj-xi)*(y-yi)/(yj-yi)+xi:hit=not hit
  j=i
 return hit
def grade(x,y):
 h=max(0,min(1,(y-25)/13))*7.5
 if inside(x,y,pond):h=-2.1
 return h
verts=[];N=101
for j in range(N):
 y=-35+j*1.5
 for i in range(N):
  x=-65+i*1.5;z=grade(x,y)
  if y>42:z=7.38-min(1,(y-42)/23)*19.0+.3*math.sin(x*.2)
  verts.append((x,y,z-.12))
faces=[]
for j in range(N-1):
 for i in range(N-1):
  a=j*N+i;faces.append((a,a+1,a+1+N,a+N))
mesh('TERRAIN_CONTINUOUS_BASE',verts,faces,TERR,'Clay_Ground')
mesh('WATER_LOWER_LEVEL_-1.20',[(x,y,-1.2) for x,y in pond],[tuple(range(len(pond)))],TERR,'Proxy_Water')
# Shore edge and rocks remain sparse, so structure cannot hide behind vegetation.
for k in range(len(pond)):
 a=Vector((*pond[k],-.1));b=Vector((*pond[(k+1)%len(pond)],-.1))
 for j in range(max(2,int((b-a).length/2))):
  p=a.lerp(b,j/max(2,int((b-a).length/2)));sphere(f'BANK_ROCK_{k}_{j}',p,(1.1,.8,.6),TERR,'Clay_Stone')
# Thin platforms; island is terrain, not a meditation pedestal.
iv=[]
for radius,height in [(3.1,-1.7),(2.8,-1.16),(2.0,-.3)]:
 for i in range(32):
  a=i*math.tau/32;rr=radius*(1+.065*math.sin(i*2.3));iv.append((-28+rr*math.cos(a),3+rr*.85*math.sin(a),height))
iff=[]
for ring in range(2):
 for i in range(32):iff.append((ring*32+i,ring*32+(i+1)%32,(ring+1)*32+(i+1)%32,(ring+1)*32+i))
iff.append(tuple(range(64,96)))
mesh('Z03_LOW_ISLAND_GROUND',iv,iff,TERR,'Clay_Ground')
# top -0.30, one cushion only, no bench/back/table
cushion=cyl('Z03_ONLY_ROUND_CUSHION',(-28,3,-.24),.36,.12,PROP,'Clay_Seat',48);cushion['outdoor_cushion']=True
# Public paths: physical walking surfaces and explicit graph.
def path(n,points,width=2.1):
 for si,(a,b) in enumerate(zip(points,points[1:])):
  a=Vector(a);b=Vector(b);v=b-a;dist=Vector((v.x,v.y,0)).length
  count=max(1,math.ceil(abs(v.z)/.15)) if abs(v.z)>.18 else 1
  for j in range(count):
   t0=j/count;t1=(j+1)/count;p=a.lerp(b,(t0+t1)/2)
   top=a.z+(b.z-a.z)*t1
   depth=max(.14,top-grade(p.x,p.y)+.24)
   o=cube(f'{n}_S{si}_STEP{j}',(p.x,p.y,top-depth/2),(width,dist/count+.03,depth),TERR,'Clay_Path',.025)
   o.rotation_euler.z=-math.atan2(v.x,v.y)
  ROUTES.append({'route':n,'from':list(a),'to':list(b),'width':width,'rise':abs(v.z),'steps':count,'max_riser':abs(v.z)/count})
path('ISLAND_SHORE_LINK',[(-28,3,-.3),(-30,0,-.3),(-33,-4,-.15),(-38,-9,0)],1.6)
# Arch bridges with actual thickness and open span.
def bridge(n,x1,x2,y,z1,z2,rise=.65):
 length=x2-x1;w=2.2;v=[]
 for i in range(25):
  t=i/24;x=x1+t*length;z=z1+(z2-z1)*t+rise*math.sin(math.pi*t)
  v.extend([(x,y-w/2,z),(x,y+w/2,z),(x,y-w/2,z-.28),(x,y+w/2,z-.28)])
 f=[]
 for i in range(24):
  a=i*4;b=a+4;f.extend([(a,b,b+1,a+1),(a+2,a+3,b+3,b+2),(a,a+2,b+2,b),(a+1,b+1,b+3,a+3)])
 f.extend([(0,1,3,2),(96,98,99,97)]);mesh(n+'_DECK',v,f,TERR,'Clay_Path')
 for s in [-1,1]:
  pts=[]
  for i in range(25):
   t=i/24;pts.append((x1+t*length,y+s*w/2,z1+(z2-z1)*t+rise*math.sin(math.pi*t)+.28))
  line(n+'_LOW_EDGE_'+str(s),pts,.10,TERR,'Clay_Stone')
 ROUTES.append({'route':n,'from':[x1,y,z1],'to':[x2,y,z2],'width':w,'rise':abs(z2-z1),'steps':0,'max_riser':0,'min_water_clearance':min(z1,z2)-.28+1.2})
bridge('Z02_LOWER_BRIDGE',-25.8,-15.8,3,-.3,0)
bridge('Z02_UPPER_BRIDGE',-36.5,-19,18,0,0,.85)
path('WEST_UPPER_BANK',[(-38,-9,0),(-43,5,0),(-40,16,0),(-36.5,18,0)],2)
path('EAST_BANK_TO_UPPER',[(-15.8,3,0),(-15.5,12,0),(-19,18,0)],2.2)
# Waterfall in three descending pools. Downstream level remains -1.2.
for i,(cx,cy,w,top,bottom) in enumerate([(-34,33,5,6,3.6),(-32,29,5.5,3.6,1.2),(-30,25,6,1.2,-1.2)]):
 cube(f'Z02_CASCADE_{i}_FACE',(cx,cy-1.4,(top+bottom)/2),(w,.2,top-bottom),TERR,'Proxy_Water')
 cube(f'Z02_CASCADE_{i}_POOL',(cx,cy+.1,top-.05),(w,3,.10),TERR,'Proxy_Water')
 for side in [-1,1]:sphere(f'CASCADE_BANK_{i}_{side}',(cx+side*(w/2+.4),cy,top-.5),(1.5,2,1.3),TERR,'Clay_Stone')
# Main courtyard, front court, and perimeter.
cube('Z01_MAIN_COURTYARD',(1,9,-.12),(28,22,.24),TERR,'Clay_Path',.08)
cube('Z12_COMMON_FORECOURT',(1,-17,-.12),(47,9,.24),TERR,'Clay_Path',.08)
path('CENTRAL_ENTRY',[ (0,-23,0),(0,-14,0),(0,1,0),(0,6,0)],3.2)
path('COURT_TO_BELL',[(6,9,0),(6,19,0),(3,23,0)],2.4)
path('PUBLIC_EAST_ROUTE',[(14,9,0),(25,10,0),(25,22,0),(25,28,1.8),(25,35,7.5)],2.4)
path('PRIVATE_BRANCH',[(25,11,0),(21.8,11,0),(21.8,13.3,.3)],1.8)
path('CHAT_BRANCH',[(25,22,0),(26.3,22,0),(26.3,23,.3)],1.8)
path('WEST_HILL_STAIRS',[(-19,18,0),(-21,23,0),(-22,29,4.5),(-25,30,4.5)],2.0)
path('HILL_WEST_TO_TREE',[(-25,30,4.5),(-15,36,6.3),(0,38,7.5)],2)
path('HILL_TREE_TO_LOOKOUT',[(0,38,7.5),(14,39,7.5),(25,35,7.5)],2)
path('BELL_TO_HILL',[(3,25,0),(8,28,1.8),(8,38,7.5),(0,38,7.5)],2)
# Major architecture modules and openable roofs.
def zone(code,name,center,dims,z=0,neighbors=''):
 c=col(code+'_'+name,ARCH);shell=col(code+'_SHELL',c);roof=col(code+'_ROOF',c)
 ZONES[code]={'code':code,'name':name,'center':[*center,z],'dimensions':list(dims),'neighbors':neighbors,'collection':c.name}
 return shell,roof

def roof(n,cx,cy,w,d,z,rise,c):
 rings=[0,.2,.45,.7,.98];v=[]
 for t in rings:
  hx=w/2-1.4*t;hy=d/2*(1-t);zz=z+rise*(1-(1-t)**1.5)+.24*(1-t)**6
  v.extend([(cx-hx,cy-hy,zz),(cx+hx,cy-hy,zz),(cx+hx,cy+hy,zz),(cx-hx,cy+hy,zz)])
 faces=[]
 for i in range(len(rings)-1):
  for j in range(4):a=i*4+j;b=i*4+(j+1)%4;faces.append((a,b,b+4,a+4))
 faces.append((16,17,18,19));o=mesh(n,v,faces,c,'Clay_Roof')
 so=o.modifiers.new('Roof_thickness','SOLIDIFY');so.thickness=.18
 beam(n+'_RIDGE',(cx-w/2+1.4,cy,z+rise+.12),(cx+w/2-1.4,cy,z+rise+.12),.14,c,'Clay_Roof')
 # roof-band seams clarify curved slope without a texture dependency
 for x in [cx-w/2+1+i*.72 for i in range(max(1,int((w-2)/.72)))]:
  for s in [-1,1]:
   pts=[]
   for t in rings:
    hx=w/2-1.4*t
    xx=max(cx-hx+.03,min(cx+hx-.03,x));yy=cy+s*d/2*(1-t);zz=z+rise*(1-(1-t)**1.5)+.24*(1-t)**6+.025
    pts.append((xx,yy,zz))
   line(n+'_SEAM',pts,.018,c,'Clay_Roof')
 ROOFS.append(c)

def window(n,center,span,axis,c,zbase,paper=False,door=False):
 x,y=center;wallh=3.25;bottom=0 if door else .75;top=2.75
 if bottom:cube(n+'_SILL',(x,y,zbase+bottom/2),(span,.14,bottom) if axis=='X' else (.14,span,bottom),c,'Clay_Plaster')
 cube(n+'_HEAD',(x,y,zbase+(top+wallh)/2),(span,.14,wallh-top) if axis=='X' else (.14,span,wallh-top),c,'Clay_Plaster')
 if not door:
  for j in range(1,max(2,int(span/.45))):
   off=-span/2+j*span/max(2,int(span/.45));loc=(x+off,y,zbase+(bottom+top)/2) if axis=='X' else (x,y+off,zbase+(bottom+top)/2)
   cube(n+'_LATTICE_V',loc,(.035,.10,top-bottom) if axis=='X' else (.10,.035,top-bottom),c,'Clay_Structure')
  for h in [bottom,.9,1.4,2,top]:cube(n+'_LATTICE_H',(x,y,zbase+h),(span,.09,.04) if axis=='X' else (.09,span,.04),c,'Clay_Structure')

def building(code,name,cx,cy,w,d,floor=.3,small=False):
 sh,ro=zone(code,name,(cx,cy),(w,d),floor)
 cube(code+'_FLOOR',(cx,cy,floor-.15),(w+.4,d+.4,.3),sh,'Clay_Structure',.04)
 # regular full-height column rows, internal grid aligned to the same module
 bays=round(w/3.6);step=w/bays
 for i in range(bays+1):
  x=cx-w/2+i*step
  for y in [cy-d/2,cy+d/2]:
   cyl(code+'_COLUMN',(x,y,floor+1.6),.16 if small else .21,3.2,sh)
   cyl(code+'_COLUMN_BASE',(x,y,floor+.10),.24 if small else .30,.20,sh,'Clay_Stone')
  if not small and 0<i<bays:
   for y in [cy-d/6,cy+d/6]:cyl(code+'_INTERNAL_COLUMN',(x,y,floor+1.6),.18,3.2,sh)
 for s in [-1,1]:
  yy=cy+s*d/2
  beam(code+'_BEAM',(cx-w/2-.3,yy,floor+3.15),(cx+w/2+.3,yy,floor+3.15),.14,sh)
  for i in range(bays):
   xx=cx-w/2+(i+.5)*step
   # one centre-bay doorway at each north/south wall
   window(code+'_NS_WALL',(xx,yy),step-.20,'X',sh,floor,door=(i==bays//2))
 for s in [-1,1]:
  xx=cx+s*w/2; db=max(1,round(d/3.6));ds=d/db
  for i in range(db):window(code+'_EW_WALL',(xx,cy-d/2+(i+.5)*ds),ds-.12,'Y',sh,floor,door=(small and s==-1 and i==db//2))
 for i in range(bays+1):
  x=cx-w/2+i*step;beam(code+'_CROSSBEAM',(x,cy-d/2-.7,floor+3.15),(x,cy+d/2+.7,floor+3.15),.12,sh)
 # exposed rafters support the same roof used in every view
 for i in range(int(w/.6)+1):
  x=cx-w/2+i*.6
  for s in [-1,1]:beam(code+'_RAFTER',(x,cy,floor+5.15),(x,cy+s*(d/2+1.1),floor+3.38),.055,ro)
 roof(code+'_FULL_ROOF',cx,cy,w+2.3,d+2.4,floor+3.35,1.8 if small else 2.6,ro)
 # broad shallow front / back stairs; no separate front enclosure
 for s in [-1,1]:
  for j in range(2):cube(code+'_ENTRY_STEP',(cx+.5*step,cy+s*(d/2+.35+.3*j),.225-.15*j),(step-.35,.34,.15),sh,'Clay_Stone',.015)
 return sh,ro
lib,libroof=building('Z04','LIBRARY',-11,-6.5,18,10.8)
copy,copyroof=building('Z05','COPY_HALL',12,-6.5,19.8,12.6)
private,_=building('Z06','PRIVATE_SIDE_ROOM',20,16,7.2,5.4,small=True)
chat,_=building('Z11','DIALOGUE_COTTAGE',29,24,5.4,4.5,small=True)
# West waterside veranda: open to water, full roof and circular columns.
sh,ro=zone('Z01','COURT_VERANDA',(-14,9),(2.8,20),0,'Z02 Z04 Z05 Z07 Z06')
cube('Z01_VERANDA_FLOOR',(-14,9,-.14),(2.8,20,.28),sh,'Clay_Structure')
for y in [0,3.6,7.2,10.8,14.4,18]:
 for x in [-15.2,-12.8]:
  cyl('Z01_VERANDA_COLUMN',(x,y,1.6),.17,3.2,sh)
  cyl('Z01_COLUMN_BASE',(x,y,.1),.25,.2,sh,'Clay_Stone')
 beam('Z01_X_BEAM',(-15.8,y,3.12),(-12.2,y,3.12),.11,sh)
# rotate roof geometry around Z for a north-south run
before=set(bpy.data.objects);roof('Z01_VERANDA_ROOF',0,0,20.8,4.6,3.25,.9,ro)
bpy.context.view_layer.update()
for o in set(bpy.data.objects)-before:
 # meshes/curves made in world coords; transform around origin then translate
 o.matrix_world=Matrix.Translation(Vector((-14,9,0))) @ Matrix.Rotation(math.pi/2,4,'Z') @ o.matrix_world
cube('Z01_VERANDA_STONE_SEAT',(-14.85,8.7,.21),(.48,1,.42),PROP,'Clay_Stone',.05)
cyl('Z01_CUP_PROXY',(-14.85,8.45,.49),.055,.14,PROP,'Clay_Paper',24)
# back colonnade connects bell court with east rooms
shb,rob=zone('Z07','BELL_COURT',(1,22),(18,7),0,'Z01 Z09')
cube('Z07_PAVED_COURT',(1,22,-.12),(18,7,.24),TERR,'Clay_Path')
for x in [-8,8]:
 for y in [19,22.6,26.2]:cyl('Z07_SIDE_COLUMN',(x,y,1.6),.17,3.2,shb)
 before=set(bpy.data.objects);roof('Z07_SIDE_ROOF',0,0,8,3.2,3.25,.8,rob)
 bpy.context.view_layer.update()
 for o in set(bpy.data.objects)-before:o.matrix_world=Matrix.Translation(Vector((x,22,0))) @ Matrix.Rotation(math.pi/2,4,'Z') @ o.matrix_world
cube('Z07_BACKGROUND_WALL',(1,26,1.5),(18,.25,3),shb,'Clay_Plaster')
roof('Z07_WALL_CAP',1,26,18.4,1,3.05,.38,rob)
# Bell frame is a small roofed bay, bell retains independent anchor.
for x in [-4.3,-1.7]:cyl('Z07_BELL_POST',(x,22,1.5),.15,3,shb)
beam('Z07_BELL_LINTEL',(-4.5,22,3),(-1.5,22,3),.15,shb)
bell=cyl('Z07_BELL',(-3,22,2.0),.50,1.05,PROP,'Clay_Dark',32)
cyl('Z07_BELL_RIM',(-3,22,1.5),.59,.12,PROP,'Clay_Dark',32)
beam('Z07_MALLET',(-3.7,21.5,1.2),(-2.9,21.5,1.2),.08,PROP,'Clay_Structure')
empty('ANCHOR_BELL_STRIKE',(-3,21.5,1.8))
cyl('Z07_STONE_BOWL',(2,23,.40),.85,.8,PROP,'Clay_Stone',32)
cyl('Z07_BOWL_WATER',(2,23,.81),.7,.02,PROP,'Proxy_Water',32)
# Perimeter: only southern outside wall; gate opening retained.
for x,w in [(-12.75,22.5),(13.75,22.5)]:cube('Z12_SOUTH_OUTER_WALL',(x,-22.5,1.25),(w,.35,2.5),ARCH,'Clay_Plaster')
for x in [-1.5,1.5]:cyl('Z12_GATE_POST',(x,-22.5,1.7),.23,3.4,ARCH)
gatero=col('Z12_GATE_ROOF',ARCH);roof('Z12_GATE_CAP',0,-22.5,5.2,3.5,3.25,.9,gatero)
# Seatings and room props.
def desk(n,x,y,z=.3,c=PROP,w=1.65,d=.75):
 cube(n+'_TOP',(x,y,z+.335),(w,d,.07),c,'Clay_Structure',.035)
 for dx in [-w*.40,w*.40]:
  for dy in [-d*.32,d*.32]:cube(n+'_LEG',(x+dx,y+dy,z+.15),(.075,.075,.30),c,'Clay_Structure')
 cube(n+'_PAPER',(x-.1,y,z+.38),(.55,.36,.014),c,'Clay_Paper',.006)
 cube(n+'_INKSTONE',(x+w*.34,y,z+.39),(.18,.13,.05),c,'Clay_Dark',.02)
 beam(n+'_BRUSH',(x+.25,y+.05,z+.39),(x+.44,y+.10,z+.39),.012,c,'Clay_Dark')
 empty('ANCHOR_'+n,(x,y,z+.39))
for x in [-17.6,-4.4]:
 for y in [-9,-4]:desk('Z04_READING_DESK',x,y,c=lib)
# copy rows leave 1.2m+ aisles clear from column rows
for x in [5.6,9.2,14.6,18.2]:
 for y in [-10,-6.5,-3]:
  desk(f'Z05_DESK_{x}_{y}',x,y,c=copy)
  cyl('Z05_INDOOR_SEAT',(x,y+.9,.38),.34,.16,copy,'Clay_Seat',32)
  cyl('Z05_LAMP_PROXY',(x+.65,y,.84),.10,.36,copy,'Clay_Paper',16)
# one foreground copy desk matches RH/LH cameras
# Bookshelf east wall, actual readable public book labels.
shelf_x=-2.5;shelf_y=-6.5
cube('Z04_BOOKCASE_BACK',(shelf_x+.15,shelf_y,1.45),(.16,5.2,2.3),lib,'Clay_Structure')
for y in [-9.1,-6.5,-3.9]:cube('Z04_BOOKCASE_UPRIGHT',(shelf_x,y,1.45),(.55,.10,2.3),lib,'Clay_Structure')
for h in [.35,.9,1.55,2.2,2.6]:cube('Z04_BOOKCASE_SHELF',(shelf_x-.08,shelf_y,h),(.72,5.3,.10),lib,'Clay_Structure')
bookrot=Matrix(((0,0,-1),(-1,0,0),(0,1,0))).to_euler()
for i,(title,bid) in enumerate([('心经','heart-sutra'),('金刚经','diamond-sutra'),('六祖坛经','platform-sutra'),('中论','middle-way')]):
 yy=-7.25+i*.48
 book=cube('BOOK_'+bid,(-2.84,yy,1.23),(.16,.37,.53),lib,'Clay_Seat',.015);book['book_id']=bid
 cube('BOOK_LABEL_'+bid,(-2.927,yy,1.24),(.008,.14,.42),lib,'Clay_Paper')
 text('TITLE_'+bid,'\n'.join(title),(-2.936,yy,1.25),.072,lib,'Label',bookrot)
 a=empty('SELECT_BOOK_'+bid,(-3.02,yy,1.23));a['book_id']=bid;a['bounds_m']=[.3,.42,.6];a['read_only_reference']=True
for y in [-8.7,-8.3,-4.8,-4.4]:
 for z in [.63,1.87,2.42]:cube('Z04_FILLER_BOOK',(-2.8,y,z),(.3,.28,.35),lib,'Clay_Plaster')
# Private room contains temporary paper + shallow trough, no public circulation.
desk('Z06_TEMPORARY_PAPER',20.5,16,c=private,w=1.2)
cube('Z06_WATER_TROUGH',(23,16, .53),(.45,2.1,.45),private,'Clay_Stone',.03)
cube('Z06_SHALLOW_WATER',(23,16,.765),(.31,1.95,.02),private,'Proxy_Water')
cube('Z06_SOFT_BACK',(19.5,15,.65),(.7,.25,.7),private,'Clay_Seat',.1)
# Chair facing east garden. Only one chair; side-entry privacy screen.
cube('Z11_CHAIR_SEAT',(28.2,24,.77),(1,.85,.22),chat,'Clay_Seat',.09)
cube('Z11_CHAIR_BACK',(27.77,24,1.11),(.18,.9,.85),chat,'Clay_Seat',.09)
for y in [23.55,24.45]:
 beam('Z11_CHAIR_ARM',(27.9,y,1.02),(28.6,y,1.02),.065,chat)
 for x in [27.9,28.55]:beam('Z11_CHAIR_LEG',(x,y,.3),(x,y,.74),.045,chat)
cyl('Z11_SIDE_TABLE',(28.8,24.9,.61),.33,.62,chat)
cyl('Z11_LAMP_PROXY',(28.8,24.9,1.08),.13,.34,chat,'Clay_Paper')
cube('Z11_ENTRY_PRIVACY_SCREEN',(27.2,23.1,1.5),(.10,1.3,2.4),chat,'Clay_Plaster')
for yy in [23.1,24.8,25.8]:sphere('Z11_EAST_GARDEN_ROCK',(34.2,yy,.3),(.7,.5,.4),TERR,'Clay_Stone')
# Platforms and trees use distinct editable proxy groups.
def platform(code,name,x,y,z,r):
 sh,ro=zone(code,name,(x,y),(r*2,r*2),z)
 bottom=min(grade(x+math.cos(a)*r,y+math.sin(a)*r) for a in [i*math.tau/16 for i in range(16)])-.4
 cyl(code+'_TERRACE',(x,y,(z+bottom)/2),r,z-bottom,TERR,'Clay_Stone',48)
 return sh
wish=platform('Z08','WISH_TREE',-26,31,4.5,5)
old=platform('Z09','OLD_TREE',0,36,7.5,4.4)
look=platform('Z10','LOOKOUT',25,35,7.5,5)
# central tree remains at courtyard, low-density red blossoms only here

def tree(n,x,y,z,height,radius,blossom=False,tags=False):
 c=col(n,VEG);beam(n+'_TRUNK',(x,y,z),(x+.2,y,z+height*.7),.4 if height<7 else .65,c,'Clay_Trunk')
 for i in range(7):
  a=i*math.tau/7;end=(x+radius*.65*math.cos(a),y+radius*.65*math.sin(a),z+height*(.62+.13*R.random()))
  beam(n+'_BRANCH',(x,y,z+height*.4),end,.15,c,'Clay_Trunk')
  sphere(n+'_CANOPY',end,(radius*.62,radius*.56,height*.25),c,'Proxy_Leaves',2)
  if blossom and i in [1,3,6]:sphere(n+'_SPARSE_RED',tuple(Vector(end)+Vector((0,0,.35))),(radius*.13,radius*.13,.25),c,'Proxy_Blossom',1)
 if tags:
  for i in range(6):
   xx=x-2+i*.7; yy=y-1.1; zz=z+1.65+(i%2)*.15
   beam(n+'_LOW_BRANCH',(x,y,z+2.1),(xx,yy,zz+.45),.035,c,'Clay_Trunk')
   beam(n+'_SIGN_STRING',(xx,yy,zz+.5),(xx,yy,zz+.2),.008,c,'Clay_Structure')
   cube(n+'_BLANK_TAG',(xx,yy,zz),(.13,.035,.32),c,'Clay_Seat',.01)
 tree_id=empty(n+'_ROOT',(x,y,z),c);tree_id['role']='wish' if tags else 'courtyard_blossom' if blossom else 'landscape'
 return c
# courtyard canopy clear of principal pedestrian axis at x6
main_tree=tree('Z01_COURTYARD_TREE',0,9,0,6,4,True)
tree('Z08_WISH_TREE',-26,31,4.5,6,4,False,True)
tree('Z09_LANDSCAPE_TREE',0,36,7.5,9,6)
# Tree and rocks outside east cottage are physically on its east side.
tree('Z11_GARDEN_TREE',35,25,0,4,2)
for code,x,y,z in [('Z08',-23,29,4.5),('Z08',-29,29.5,4.5),('Z10',24,33,7.5),('Z10',27.5,36,7.5)]:
 cube(code+'_STONE_SEAT',(x,y,z+.21),(1.7,.55,.42),PROP,'Clay_Stone',.06)
# roofed tag box, only on the wish platform
cube('Z08_TAG_BOX',(-23,32,5.25),(.7,.5,.8),PROP,'Clay_Structure')
boxroof=col('Z08_TAG_BOX_ROOF',PROP);roof('Z08_TAG_BOX_CAP',-23,32,1.1,.9,5.75,.25,boxroof)
# small roof is retained in cutaway because only architecture roofs are toggled
# sparse landscape masses and distant mountains: no fog or hiding layout defects
for i in range(16):
 x=R.uniform(-44,38);y=R.uniform(43,53)
 if 16<x<34:x=42
 z=7.5-min(1,(y-42)/23)*19.0
 tree('DISTANT_TREE_'+str(i),x,y,z,R.uniform(4,6),R.uniform(1.7,2.5))
for i in range(13):
 x=-110+i*18;y=100+R.uniform(-10,30);height=R.uniform(18,38)
 v=[(x-20,y-8,-2),(x+20,y-8,-2),(x+15,y+20,-2),(x-15,y+20,-2),(x,y+5,height)]
 mesh('DISTANT_MOUNTAIN_'+str(i),v,[(0,1,4),(1,2,4),(2,3,4),(3,0,4)],TERR,'Clay_Stone')
for i in range(7):
 x=-105-R.uniform(0,20);y=-10+i*22;hh=R.uniform(20,38)
 mesh('WEST_MOUNTAIN_'+str(i),[(x-14,y-20,-2),(x+14,y-20,-2),(x+14,y+20,-2),(x-14,y+20,-2),(x,y,hh)],[(0,1,4),(1,2,4),(2,3,4),(3,0,4)],TERR,'Clay_Stone')
for i in range(8):
 x=100+R.uniform(0,20);y=-5+i*20;hh=R.uniform(18,34)
 mesh('EAST_MOUNTAIN_'+str(i),[(x-15,y-18,-12),(x+15,y-18,-12),(x+15,y+18,-12),(x-15,y+18,-12),(x,y,hh)],[(0,1,4),(1,2,4),(2,3,4),(3,0,4)],TERR,'Clay_Stone')
# Z02/Z03/Z12 entries for layout register and labels
ZONES['Z02']={'code':'Z02','name':'WATER_GARDEN_DOUBLE_BRIDGE','center':[-29,10,-1.2],'dimensions':[26,40],'neighbors':'Z01 Z03 Z08'}
ZONES['Z03']={'code':'Z03','name':'LOW_ISLAND_MEDITATION','center':[-28,3,-.3],'dimensions':[4.8,4.8],'neighbors':'Z02 WEST_BANK'}
ZONES['Z12']={'code':'Z12','name':'GATE_FORECOURT','center':[0,-19,0],'dimensions':[47,9],'neighbors':'Z04 Z05 Z01'}
ZONES['Z04']['neighbors']='Z12 Z01';ZONES['Z05']['neighbors']='Z12 Z01';ZONES['Z06']['neighbors']='PUBLIC_EAST_ROUTE';ZONES['Z11']['neighbors']='PUBLIC_EAST_ROUTE';ZONES['Z08']['neighbors']='Z02 Z09';ZONES['Z09']['neighbors']='Z08 Z07 Z10';ZONES['Z10']['neighbors']='PUBLIC_EAST_ROUTE Z09'
ZONES['Z01']['center']=[1,9,0];ZONES['Z01']['dimensions']=[28,22];ZONES['Z01']['veranda']={'center':[-14,9,0],'dimensions':[2.8,20]}
for code,z in ZONES.items():
 x,y,h=z['center'];text('LABEL_'+code,code,(x,y,h+3.6),1.15,LABEL,'Label')
# human reference hidden from render, visible on demand
for x,y,z in [(6,8,0),(-11,-6,.3),(-28,3,-.3)]:
 o=cyl('HUMAN_1.70M',(x,y,z+.85),.22,1.7,SCALE,'Clay_Dark');o.hide_render=True
# Pack reference images as hidden image empties, not as world surfaces.
refdir=ROOT.parents[1]/'style-studies-20261020'
for p in [refdir/'v8-first-person/00-overview-courtyard-blossoms.png',refdir/'v3/03-entry-tang.png',refdir/'v7/02-dialogue-cottage-entry.png']:
 if p.exists():
  im=bpy.data.images.load(str(p));im.pack();o=bpy.data.objects.new('REF_'+p.stem,None);REFS.objects.link(o);o.empty_display_type='IMAGE';o.data=im;o.hide_render=True;o.hide_viewport=True
# Cameras are all the same world, no per-camera geometry variants.
def camera(name,pos,target,lens=32,ortho=None,region='',state='EYE_LEVEL'):
 d=bpy.data.cameras.new(name);o=bpy.data.objects.new(name,d);CAM.objects.link(o);o.location=pos;o.rotation_euler=(Vector(target)-o.location).to_track_quat('-Z','Y').to_euler();d.lens=lens;d.clip_start=.06;d.clip_end=500
 if ortho:d.type='ORTHO';d.ortho_scale=ortho
 CAMS.append({'name':name,'region':region,'position':list(pos),'target':list(target),'lens_mm':lens,'ortho_m':ortho,'display_state':state})
 return o
camera('CAM_WORLD_FULL',(74,-114,103),(-5,7,1),ortho=103,state='WORLD_FULL')
camera('CAM_LAYOUT_TOP',(-5,6,115),(-5,6,0),ortho=116,state='LAYOUT_CUTAWAY')
camera('CAM_LAYOUT_AXON',(70,-105,105),(-5,8,1),ortho=100,state='LAYOUT_CUTAWAY')
camera('CAM_ENTRY_SEATED',(-13.4,7.5,1.05),(-28,20,1.05),26,region='Z01')
camera('CAM_WATER_BRIDGE',(-17.7,2.4,1.5),(-32,25,1),28,region='Z02')
camera('CAM_ISLAND_SEATED',(-28,3,.35),(-15,8,1.3),30,region='Z03')
camera('CAM_ISLAND_APPROACH',(-30,-.5,1.3),(-28,3,-.24),35,region='Z03')
camera('CAM_ISLAND_SECTION',(-28,-8,2.2),(-25,3,-.6),ortho=16,region='Z03')
camera('CAM_LIBRARY_SEATED',(-18.5,-8.3,1.15),(-20.2,1,1.3),28,region='Z04')
camera('CAM_LIBRARY_SHELF',(-5.8,-6.5,1.3),(-2.85,-6.5,1.23),45,region='Z04')
camera('CAM_LIBRARY_ENTRY',(-9.2,-13.8,1.5),(-11,-4,1.3),28,region='Z04')
camera('CAM_COPY_VIEW',(5.6,-1.9,.95),(13,-9.5,.9),28,region='Z05')
camera('CAM_COPY_RH',(5.3,-2.05,1.24),(5.55,-3,.68),48,region='Z05')
camera('CAM_COPY_LH',(5.9,-2.05,1.24),(5.55,-3,.68),48,region='Z05')
camera('CAM_COPY_ENTRY',(13.8,-14.8,1.5),(12,-5,1.1),28,region='Z05')
camera('CAM_PRIVATE_SEATED',(19.4,14.8,1.15),(22.5,16.2,1),28,region='Z06')
camera('CAM_PRIVATE_ENTRY',(21.8,11.8,1.5),(21.8,16,1.1),28,region='Z06')
camera('CAM_BELL_COURT',(-3,19,1.4),(-2,24,1.65),30,region='Z07')
camera('CAM_WISH_APPROACH',(-26,27.5,5.95),(-26,31,6.4),26,region='Z08')
camera('CAM_WISH_READ',(-26,28.5,5.95),(-26,29.9,6.3),45,region='Z08')
camera('CAM_OLD_TREE_PATH',(0,31.4,9),(0,36,11),26,region='Z09')
camera('CAM_LOOKOUT_N',(25,35,8.55),(28,80,12),30,region='Z10')
camera('CAM_LOOKOUT_E',(27.5,35,8.55),(80,45,10),30,region='Z10')
camera('CAM_LOOKOUT_W',(23,35,8.55),(-65,43,12),30,region='Z10')
camera('CAM_CHAT_ENTRY',(25,23,1.5),(29,24,1.4),28,region='Z11')
camera('CAM_CHAT_SEATED',(28.2,24,1.4),(34.5,24.5,1.5),30,region='Z11')
camera('CAM_FRONT_GATE',(0,-22.1,1.5),(0,1,2),26,region='Z12')
# Real lighting reference kept editable for phase 2, independent of workbench shading.
ld=bpy.data.lights.new('DAY_SUN','SUN');lo=bpy.data.objects.new('DAY_SUN',ld);COL['60_LIGHTING'].objects.link(lo);lo.rotation_euler=(.5,-.4,-.6);ld.energy=2;ld.angle=.15
# View layers share objects. Hide only explicitly designated roof collections.
scene.view_layers[0].name='WORLD_FULL'
scene.view_layers.new('LAYOUT_CUTAWAY');scene.view_layers.new('EYE_LEVEL')
def findlayer(root,name):
 if root.name==name:return root
 for c in root.children:
  f=findlayer(c,name)
  if f:return f
for vl in scene.view_layers:
 findlayer(vl.layer_collection,LABEL.name).exclude=vl.name!='LAYOUT_CUTAWAY'
 findlayer(vl.layer_collection,SCALE.name).exclude=True
 findlayer(vl.layer_collection,REFS.name).exclude=True
 if vl.name=='LAYOUT_CUTAWAY':
  for c in set(ROOFS):
   lc=findlayer(vl.layer_collection,c.name)
   if lc:lc.exclude=True
  # only canopy proxies hidden for plan clarity; tree roots/roles retained in world
  for c in VEG.children:
   if c.name.startswith('Z01_COURTYARD_TREE') or c.name.startswith('Z08_') or c.name.startswith('Z09_'):findlayer(vl.layer_collection,c.name).exclude=True
scene.camera=bpy.data.objects['CAM_WORLD_FULL']
scene['project']='Zenflow Space';scene['version']=VERSION;scene['phase_status']='P1_REVIEW_PENDING';scene['units']='metres +X east +Y north +Z up';scene['geometry_contract']='one geometry, roofs excluded only in LAYOUT_CUTAWAY'
# Add Blender text block with editing contract.
t=bpy.data.texts.new('READ_ME_FIRST');t.write('Zenflow Space P1 editable greybox\nUse WORLD_FULL / LAYOUT_CUTAWAY / EYE_LEVEL view layers.\nDo not rebuild over hand edits. Master is zen-space.blend.\nAll cameras reference the same objects. Book anchors are empties.\nNo real private content, no app logic. Greybox dimensions await visual review.\n')
# Share repeated mesh data, preserving independently editable objects and anchors.
pool={};linked=0;mesh_objects=0
for o in scene.objects:
 if o.type!='MESH':continue
 mesh_objects+=1
 me=o.data
 payload=([tuple(round(x,6) for x in v.co) for v in me.vertices],[tuple(f.vertices) for f in me.polygons],[m.name for m in me.materials])
 key=hashlib.sha256(repr(payload).encode()).hexdigest()
 if key in pool:o.data=pool[key];linked+=1
 else:pool[key]=me
(ROOT/'evidence/instancing-report.json').write_text(json.dumps({'source_version':VERSION,'mesh_objects':mesh_objects,'unique_mesh_data':len(pool),'linked_instances':linked,'method':'shared mesh datablocks; object transforms, materials and anchors retained','skill':'environment-artist'},indent=2))
# Registers are authoritative measurements for this snapshot.
manifest={'project':'Zenflow Space','version':VERSION,'blender':bpy.app.version_string,'unit':'metre','axes':{'east':'+X','north':'+Y','up':'+Z'},'color':{'view_transform':scene.view_settings.view_transform,'look':scene.view_settings.look,'exposure':0,'gamma':1},'zones':list(ZONES.values()),'cameras':CAMS,'routes':ROUTES,'water_levels':{'lower':-1.2,'cascade_top_to_bottom':[6,3.6,1.2,-1.2],'island_ground':-.3,'island_cushion_diameter':.72,'island_cushion_thickness':.12},'limitations':['greybox materials only','no device verification','no business implementation','dimensions are model proposal pending user visual review']}
(ROOT/'manifest/scene-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
for fn,data,keys in [('layout-register.csv',list(ZONES.values()),['code','name','center','dimensions','neighbors']),('camera-register.csv',CAMS,['name','region','position','target','lens_mm','ortho_m','display_state']),('path-register.csv',ROUTES,['route','from','to','width','rise','steps','max_riser'])]:
 with (ROOT/'manifest'/fn).open('w',newline='') as f:
  wr=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore');wr.writeheader();wr.writerows(data)
with (ROOT/'manifest/asset-register.csv').open('w',newline='') as f:
 wr=csv.writer(f);wr.writerow(['object','type','collection','material','source','license','interaction'])
 for o in scene.objects:
  wr.writerow([o.name,o.type,';'.join(c.name for c in o.users_collection),';'.join(m.name for m in o.data.materials) if hasattr(o.data,'materials') else '', 'procedural original','project-authored','anchor' if o.type=='EMPTY' and o.name.startswith(('ANCHOR','SELECT')) else 'static'])
bpy.ops.wm.save_as_mainfile(filepath=str(OUT))
print('P1_BUILD_SAVED',str(OUT),'objects',len(scene.objects),'cameras',len(CAMS))
