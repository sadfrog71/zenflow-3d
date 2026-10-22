"""Finish physical furniture, readable contents and small architectural details for all regions."""
import bpy,sys,math,random,json
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import box,beam,assign,material,light,camera
from phase3_pbr import palette
s=bpy.context.scene;R=random.Random(202610204);OUT=ROOT/'zen-space-p3-v002.blend'
if s.get('version')!='p3-v001' or OUT.exists():raise RuntimeError('Requires p3-v001/new output')
P=palette();M={k:bpy.data.materials['P2_'+k] for k in ['Cedar','Darkwood','Stone','Paper','Leaf','Roof','Plaster','Soil','Water','Ink','Linen','Blossom','Glow']}
c=bpy.data.collections.new('P3_PROPS_AND_ARCH_DETAILS');s.collection.children.link(c);INT=bpy.data.collections['50_INTERACTION'];L=bpy.data.collections['60_LIGHTING']
def hide(o):o.hide_render=True;o.hide_viewport=True;o['retired_proxy']=True

def lathe(n,center,profile,mat,segments=48):
 vs=[];fs=[]
 for rr,zz in profile:
  for i in range(segments):a=i*math.tau/segments;vs.append((rr*math.cos(a),rr*math.sin(a),zz))
 for j in range(len(profile)-1):
  for i in range(segments):a=j*segments+i;b=j*segments+(i+1)%segments;fs.append((a,b,b+segments,a+segments))
 me=bpy.data.meshes.new(n);me.from_pydata(vs,[],fs);me.update();o=bpy.data.objects.new(n,me);c.objects.link(o);o.location=center;assign(o,mat)
 uv=me.uv_layers.active
 for f in me.polygons:
  for li in f.loop_indices:
   v=me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(math.atan2(v.y,v.x)/math.tau+.5,v.z*.6)
  f.use_smooth=True
 return o

def anchor(n,p,role):
 o=bpy.data.objects.get(n)
 if not o:o=bpy.data.objects.new(n,None);INT.objects.link(o)
 o.location=p;o.empty_display_type='SPHERE';o.empty_display_size=.08;o['role']=role;return o

def text(n,body,p,size,mat,rotation=None):
 d=bpy.data.curves.new(n,'FONT');d.body=body;d.size=size;d.align_x='CENTER';d.align_y='CENTER';d.extrude=.0003;o=bpy.data.objects.new(n,d);c.objects.link(o);o.location=p;d.materials.append(mat)
 if rotation:o.rotation_euler=rotation
 return o
# Warm wood undersides of ALL solidified roofs. No blue tile sheets facing rooms.
for o in s.objects:
 if o.type=='MESH' and ('FULL_ROOF' in o.name or 'VERANDA_ROOF' in o.name or 'SIDE_ROOF' in o.name or 'GATE_CAP' in o.name):
  o.data=o.data.copy();o.data.materials.clear();o.data.materials.append(M['Roof']);o.data.materials.append(M['Cedar'])
  for mod in o.modifiers:
   if mod.type=='SOLIDIFY':mod.material_offset=1
# A continuous central copy-hall aisle: remove the two redundant central interior posts,
# retaining flanking posts and adding a real cross tie at roof level.
retired=[]
for o in s.objects:
 if o.name.startswith('Z05_INTERNAL_COLUMN') and abs(o.location.x-12)<.1:hide(o);retired.append(o.name)
for y in [-8.6,-4.4]:box('SM_Z05_CENTRAL_TIE_BEAM',(12,y,3.45),(6.8,.22,.25),c,M['Cedar'],.025)
cam=bpy.data.objects['CAM_COPY_VIEW'];cam.location=(9.2,-1.65,.95);cam.rotation_euler=(Vector((11.9,-10,1.0))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=27
# Complete remaining room floors with individual modules.
for code,cx,cy,w,d,z in [('Z06',20,16,7.2,5.4,.3),('Z11',29,24,5.4,4.5,.3)]:
 count=int(w/.27);bw=w/count
 for i in range(count):
  yy=cy-d/2
  while yy<cy+d/2-.01:
   length=min(R.uniform(1.4,2.4),cy+d/2-yy);o=box('SM_'+code+'_FLOOR_PLANK',(cx-w/2+(i+.5)*bw,yy+length/2,z+.007),(bw-.006,length-.008,.014),c,P['Wood'],.003);uv=o.data.uv_layers.active;row=R.randrange(11)
   for f in o.data.polygons:
    for li in f.loop_indices:
     v=o.data.vertices[o.data.loops[li].vertex_index].co;uv.data[li].uv=((v.y+yy)/3,(row+.1+(v.x/bw+.5)*.75)/11)
   yy+=length
# Consistent small bracket modules on the two halls and small houses.
for code,cx,cy,w,d in [('Z04',-11,-6.5,18,10.8),('Z05',12,-6.5,19.8,12.6),('Z06',20,16,7.2,5.4),('Z11',29,24,5.4,4.5)]:
 bays=round(w/3.6);step=w/bays
 for i in range(bays+1):
  x=cx-w/2+i*step
  for y in [cy-d/2,cy+d/2]:
   for sign in [-1,1]:
    box('SM_'+code+'_EAVE_BRACKET',(x+sign*.24,y,3.43),(.48,.21,.15),c,M['Cedar'],.02)
    beam('SM_'+code+'_BRACKET_ARM',(x,y,3.04),(x+sign*.46,y,3.49),.05,c,M['Cedar'])
# Perimeter wall cap and stone plinth; retain existing opening.
for x,w in [(-12.75,22.5),(13.75,22.5)]:
 box('SM_Z12_WALL_STONE_PLINTH',(x,-22.5,.16),(w,.44,.32),c,P['Rock'],.03)
 box('SM_Z12_WALL_COPING',(x,-22.5,2.53),(w+.15,.64,.12),c,M['Roof'],.025)
# Front court and bell court paving, separate from walking surfaces by a few mm.
for zone,cx,cy,w,d in [('Z12',1,-17,47,9),('Z07',1,22,18,7)]:
 for i in range(int(w/1.2)):
  for j in range(int(d/.9)):
   box('SM_'+zone+'_STONE_PAVER',(cx-w/2+(i+.5)*1.2,cy-d/2+(j+.5)*.9,.006),(1.19,.89,.012),c,P['Rock'],.005)
# Stone platform edge coping; upper surfaces do not become raised meditation seats.
for code,cx,cy,z,rr in [('Z08',-26,31,4.5,5),('Z09',0,36,7.5,4.4),('Z10',25,35,7.5,5)]:
 profile=[(rr-.25,z+.012),(rr+.01,z+.012),(rr+.01,z-.14),(rr-.25,z-.14)]
 lathe('SM_'+code+'_STONE_EDGE',(cx,cy,0),profile,P['Rock'],64)
# Four independent cloth-covered books: order follows reference when seen from shelf camera.
bookcolors=[('heart-sutra',(.13,.20,.12)),('diamond-sutra',(.38,.28,.16)),('platform-sutra',(.13,.20,.27)),('middle-way',(.24,.15,.10))]
for bid,color in bookcolors:
 for o in s.objects:
  if o.name.endswith(bid) and o.name.startswith(('BOOK_','BOOK_LABEL_','TITLE_','SELECT_BOOK_','CONTENT_FACE_')):o.location.y=-13.06-o.location.y
 mat=material('P3_CLOTH_COVER_'+bid,color,None,.85);book=bpy.data.objects['BOOK_'+bid];assign(book,mat);p=book.location
 for zz in [p.z-.19,p.z+.19]:
  beam('SM_Z04_BOOK_BINDING_'+bid,(p.x-.088,p.y+.13,zz),(p.x-.088,p.y+.16,zz),.0025,c,M['Paper'],8)
 # A thin rim identifies the independent content face, without real private text.
 anchor('ANCHOR_Z04_RETURN_SEAT',(-18.5,-8.3,1.15),'return_to_reading_seat')
# Reading table: open blank sample and ceramic cup, kettle near wall.
lathe('SM_Z04_CERAMIC_CUP',(-18.8,-7.4,.80),[(.035,-.055),(.05,.055),(.044,.058),(.03,-.04)],M['Paper'],32)
lathe('SM_Z04_KETTLE',(-19.4,-3.8,.77),[(.10,0),(.15,.1),(.11,.24),(.06,.27)],M['Darkwood'],32)
beam('SM_Z04_KETTLE_SPOUT',(-19.27,-3.8,.92),(-19.13,-3.8,1.02),.035,c,M['Darkwood'],16)
# Paper states are blank or clearly authored samples, never private content.
variants=bpy.data.collections['30_PROPS_VARIANTS']
for state,body,dy in [('BLANK','',0),('TWO_CHARACTERS','坐下',.002),('CORRECTED','',.004),('USER_SAMPLE','示意',.006)]:
 o=box('SM_Z05_PAPER_STATE_'+state,(9.1,-3,.69+dy),(.55,.36,.002),c,M['Paper'],.003);o['sample_only']=True;o['state']=state
 # Variants stored out of the default render, independent source objects retained.
 if state!='BLANK':hide(o)
 if body:
  t=text('SAMPLE_Z05_'+state,body,(9.08,-3.02,.695+dy),.06,M['Ink']);t['sample_only']=True;hide(t)
# Curl at near-paper corner gives actual thickness and edge silhouette.
mesh=bpy.data.meshes.new('P3_PAPER_CORNER');mesh.from_pydata([(9.31,-2.84,.694),(9.36,-2.84,.694),(9.36,-2.82,.709),(9.32,-2.82,.702)],[],[(0,1,2,3)]);ob=bpy.data.objects.new('SM_Z05_PAPER_CURLED_CORNER',mesh);c.objects.link(ob);assign(ob,M['Paper'])
for n,p,role in [('ANCHOR_Z05_PAPER',(9.1,-3,.695),'paper_surface'),('ANCHOR_Z05_INK',(9.75,-3,.70),'ink'),('ANCHOR_Z05_LAMP',(9.85,-3,.84),'lamp_relight'),('ANCHOR_Z05_BRUSH',(9.55,-2.95,.70),'brush'),('ANCHOR_Z05_PAPER_NORMAL',(9.1,-3,.75),'paper_normal_positive_Z')]:anchor(n,p,role)
# Real hollow stone trough and partial thin curtain in the independent room.
for o in s.objects:
 if o.name in ['Z06_WATER_TROUGH','Z06_SHALLOW_WATER']:hide(o)
box('SM_Z06_TROUGH_BASE',(23,16,.38),(.48,2.13,.16),c,P['Rock'],.035)
for x in [22.80,23.20]:box('SM_Z06_TROUGH_SIDE',(x,16,.60),(.09,2.13,.38),c,P['Rock'],.025)
for y in [14.98,17.02]:box('SM_Z06_TROUGH_END',(23,y,.60),(.48,.09,.38),c,P['Rock'],.025)
box('SM_Z06_TROUGH_WATER',(23,16,.702),(.30,1.93,.012),c,M['Water'])
box('SM_Z06_FLOATING_BLANK_PAPER',(23,16.3,.712),(.18,.25,.0015),c,M['Paper'],.003)
box('SM_Z06_WOVEN_SEAT',(19.65,15.15,.36),(1.0,.85,.12),c,M['Linen'],.06)
curtainmat=material('P3_THIN_CURTAIN',(.78,.76,.65),'paper.png',.95);vs=[];fs=[]
for j in range(13):
 for i in range(19):
  y=16.6+i*.052;z=.85+j*.16;x=23.47+.035*math.sin(i*.8+j*.1);vs.append((x,y,z))
for j in range(12):
 for i in range(18):a=j*19+i;fs.append((a,a+1,a+20,a+19))
me=bpy.data.meshes.new('P3_CURTAIN');me.from_pydata(vs,[],fs);o=bpy.data.objects.new('SM_Z06_LIGHT_CURTAIN',me);c.objects.link(o);assign(o,curtainmat)
for f in me.polygons:f.use_smooth=True
anchor('ANCHOR_Z06_TEMPORARY_PAPER',(20.4,16,.69),'temporary_display_no_storage');anchor('ANCHOR_Z06_RELEASE',(23,16,.71),'release_temporary_paper')
# Bell: curved bronze shell with open interior, rim bands and a distinct worn patch.
bronze=material('P3_BRONZE',(.22,.15,.065),None,.46,.78);worn=material('P3_WORN_BRONZE',(.34,.24,.11),None,.32,.78)
for n in ['Z07_BELL','Z07_BELL_RIM']:hide(bpy.data.objects[n])
bell=lathe('SM_Z07_BRONZE_BELL',(-3,22,0),[(.56,1.45),(.58,1.52),(.51,1.57),(.47,2.13),(.36,2.43),(.22,2.52),(.19,2.50),(.31,2.38),(.42,2.10),(.46,1.57),(.52,1.50)],bronze,64)
for z in [1.54,1.63,2.25]:lathe('SM_Z07_BELL_BAND',(-3,22,0),[(.485 if z>2 else .54,z-.025),(.50 if z>2 else .555,z),(.485 if z>2 else .54,z+.025)],bronze,64)
box('SM_Z07_STRIKE_WEAR',(-3,21.515,1.84),(.18,.012,.18),c,worn,.065)
beam('SM_Z07_BELL_HANGER',(-3,22,2.50),(-3,22,3.02),.035,c,bronze,16)
# Mallet suspension at a plausible resting point, no automatic interaction cue.
for x in [-3.55,-3.05]:beam('SM_Z07_MALLET_ROPE',(x,21.5,1.2),(x,21.5,2.9),.012,c,M['Darkwood'],10)
# Open stone bowl, using same footprint and separate water.
for n in ['Z07_STONE_BOWL','Z07_BOWL_WATER']:hide(bpy.data.objects[n])
lathe('SM_Z07_HOLLOW_STONE_BOWL',(2,23,0),[(.60,.05),(.78,.18),(.85,.70),(.82,.83),(.69,.83),(.62,.25),(.20,.19),(.20,.08)],P['Rock'],64)
lathe('SM_Z07_BOWL_WATER',(2,23,0),[(.01,.63),(.70,.63)],M['Water'],64)
anchor('ANCHOR_Z07_STONE_DROP',(2,23,.7),'stone_drop');anchor('ANCHOR_Z07_BELL_PIVOT',(-3,22,2.9),'bell_pivot')
# Low reachable wish branches and durable blank wood labels.
tagwood=material('P3_WEATHERED_TAG_WOOD',(.48,.35,.19),'cedar-soft.png',.82);rope=material('P3_DARK_RED_CORD',(.25,.045,.026),None,.87)
for o in list(s.objects):
 if o.name.startswith('Z08_WISH_TREE_BLANK_TAG'):
  assign(o,tagwood);x,y,z=o.location;beam('SM_Z08_REACHABLE_BRANCH',(-26,31,6.75),(x,y,z+.45),.033,c,M['Darkwood'],10)
  face=box('CONTENT_FACE_Z08_TAG_'+str(round(x,2)),(x,y-.0185,z),(.105,.001,.25),c,tagwood);face['content_role']='blank placeholder; no real public or private messages'
  anchor('ANCHOR_Z08_TAG_'+str(round(x,2)),(x,y-.06,z),'public_tag_content_surface')
 if o.name.startswith('Z08_WISH_TREE_SIGN_STRING'):assign(o,rope)
# Existing tag box roof retained. Replace solid body proxy with open compartments.
body=bpy.data.objects.get('Z08_TAG_BOX')
if body:hide(body)
for x in [-28.35,-27.65]:box('SM_Z08_TAG_BOX_SIDE',(x,33,5.25),(.055,.50,.80),c,M['Cedar'],.012)
box('SM_Z08_TAG_BOX_BACK',(-28,33.23,5.25),(.70,.055,.80),c,M['Cedar'],.012)
for z in [4.87,5.23,5.63]:box('SM_Z08_TAG_BOX_SHELF',(-28,33,z),(.70,.50,.055),c,M['Cedar'],.008)
for x in [-28.1,-27.9]:box('SM_Z08_TAG_BOX_DIVIDER',(x,33,5.42),(.025,.45,.35),c,M['Cedar'],.006)
for i in range(5):box('SM_Z08_STACKED_BLANK_TAG',(-28.2+i*.08,32.98,5.31+i*.012),(.13,.28,.025),c,tagwood,.006)
anchor('ANCHOR_Z08_TAG_TAKE',(-28,32.65,5.3),'take_blank_tag')
# Comfortable upholstered seat in the dialogue cottage, with actual timber frame.
for o in list(s.objects):
 if o.name.startswith('Z11_CHAIR_') or o.name in ['Z11_SIDE_TABLE','Z11_LAMP_PROXY']:hide(o)
for y in [23.56,24.44]:
 for x in [27.9,28.55]:beam('SM_Z11_CHAIR_LEG',(x,y,.3),(x,y,.72),.045,c,M['Cedar'],12)
 beam('SM_Z11_CHAIR_ARM',(27.9,y,1.02),(28.65,y,1.02),.055,c,M['Cedar'],16)
box('SM_Z11_CHAIR_FRAME',(28.2,24,.66),(1.0,.88,.10),c,M['Cedar'],.035)
seat=box('SM_Z11_SEAT_CUSHION',(28.2,24,.79),(.91,.81,.20),c,M['Linen'],.09)
back=box('SM_Z11_BACK_CUSHION',(27.82,24,1.11),(.20,.82,.72),c,M['Linen'],.09);back.rotation_euler.y=-.12
for o in [seat,back]:
 for mod in o.modifiers:
  if mod.type=='BEVEL':mod.segments=4
 for f in o.data.polygons:f.use_smooth=True
lathe('SM_Z11_SIDE_TABLE_TOP',(28.8,24.9,0),[(.01,.90),(.37,.90),(.37,.95),(.01,.95)],M['Cedar'],48)
for x,y in [(28.55,24.65),(29.05,24.65),(28.8,25.15)]:beam('SM_Z11_TABLE_LEG',(x,y,.3),(x,y,.91),.035,c,M['Cedar'],12)
lathe('SM_Z11_PAPER_LAMP',(28.8,24.9,0),[(.07,.98),(.13,1.05),(.16,1.18),(.13,1.31),(.07,1.36)],M['Glow'],48)
for i in range(8):
 a=i*math.tau/8;x=28.8+math.cos(a)*.145;y=24.9+math.sin(a)*.145;beam('SM_Z11_LAMP_RIB',(x,y,1.07),(x,y,1.29),.004,c,M['Darkwood'],6)
light('P3_COTTAGE_LAMP',(28.8,24.9,1.18),12,(1,.64,.32),L,.12,'POINT')
lathe('SM_Z11_CUP',(28.65,24.63,.99),[(.03,0),(.045,.07),(.039,.073),(.025,.015)],M['Paper'],32)
anchor('ANCHOR_Z11_CHAT_SEAT',(27.95,24,1.4),'user_initiated_chat_seat')
# Small secondary objects: a rice dish and broom on veranda, no duplicated outdoor cushion.
lathe('SM_Z01_RICE_DISH',(-15.0,9.12,.48),[(.035,-.02),(.10,.005),(.095,.025),(.06,.007)],M['Paper'],32)
for i in range(18):
 x=-15+R.uniform(-.045,.045);y=9.12+R.uniform(-.045,.045);beam('SM_Z01_RICE_GRAIN',(x,y,.50),(x+.006,y+.002,.50),.0018,c,M['Paper'],6)
beam('SM_Z01_BROOM_HANDLE',(-12.95,10.25,.15),(-12.82,10.9,1.45),.017,c,M['Cedar'],12)
for i in range(14):beam('SM_Z01_BROOM_FIBRE',(-13.03+i*.012,10.25,.13),(-13.06+i*.014,10.18,.02),.004,c,M['Linen'],6)
anchor('ANCHOR_Z01_RICE',(-15,9.12,.50),'rice_dish');anchor('ANCHOR_Z01_SWEEP',(-14.8,9,0),'sweep_stone')
# Soft light has a physical window/table source in each remaining room.
light('P3_PRIVATE_WINDOW',(23.55,16.8,2),160,(.80,.90,1),L,2,target=(20,16,.6))
light('P3_COTTAGE_WINDOW',(31.75,24.5,2),170,(1,.90,.72),L,2,target=(28.2,24,1))
s['version']='p3-v002';s['phase_status']='P3_ALL_REGION_DETAIL_REVIEW_PENDING';s.camera=bpy.data.objects['CAM_WORLD_FULL']
bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True)
(ROOT/'evidence/phase3-props-build.json').write_text(json.dumps({'version':'p3-v002','base':'p3-v001','detail_objects':len(c.objects),'retired_redundant_copy_posts':retired,'scope':['all remaining room floors and eave brackets','book cover colors, reference-facing order and content anchors','blank/sample paper variants and curled edge','private room hollow trough and curtain','curved open bronze bell and stone bowl','reachable wish branches and blank content faces','tag box open compartments','dialogue chair, table and lamp','front/bell court paving and veranda rice dish/broom'],'pending':['all-region render comparison','path/room clearance recheck','animations, weather, previews and full GLB']},ensure_ascii=False,indent=2))
print('P3_PROPS_SAVED',OUT,flush=True)
