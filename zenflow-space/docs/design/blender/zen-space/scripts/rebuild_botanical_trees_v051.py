"""Branch-led volumetric garden trees, derived from the reviewed Image Gen study; no camera-specific vegetation."""
import bpy,json,math,random,re
from pathlib import Path
from mathutils import Vector,Matrix
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v051.blend';assert s.get('version')=='p3-v050' and not out.exists();C=bpy.data.collections.new('P12_BOTANICAL_TREE_INSTANCES');s.collection.children.link(C);L=bpy.data.collections.new('P12_BOTANICAL_SHARED_LIBRARY');s.collection.children.link(L)
# Reuse existing licensed bark photograph; foliage uses actual folded geometry, not alpha planes.
bark=s.objects['SM_Z09_LANDSCAPE_TREE_ALIGNED_TRUNK'].data.materials[0];greens=[]
for i,col in enumerate([(.045,.087,.022),(.067,.125,.030),(.090,.158,.040),(.117,.191,.055),(.140,.215,.070),(.079,.146,.041)]):
 m=bpy.data.materials.new('P12_LIVING_LEAF_'+str(i));m.use_nodes=True;m.diffuse_color=(*col,1);p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(*col,1);p.inputs['Roughness'].default_value=.58;p.inputs['Specular IOR Level'].default_value=.32;p.inputs['Subsurface Weight'].default_value=.075;greens.append(m)
blossom=bpy.data.materials['P2_Blossom'];prototypes={}
def make_proto(name,seed,kind='broad',hero=False):
 rng=random.Random(seed);woodv=[];woodf=[];wooduv=[];leafv=[];leaff=[];leafmi=[];flower_v=[];flower_f=[];clouds=[]
 def tube(points,radii,n=8):
  start=len(woodv);running=0
  for i,p in enumerate(points):
   t=(points[min(i+1,len(points)-1)]-points[max(0,i-1)]).normalized();u=t.cross(Vector((0,1,0)))
   if u.length<.01:u=t.cross(Vector((1,0,0)))
   u.normalize();v=t.cross(u).normalized()
   if i:running+=(p-points[i-1]).length
   for j in range(n):a=math.tau*j/n;woodv.append(tuple(p+radii[i]*(u*math.cos(a)+v*math.sin(a))));wooduv.append((j/n,running*.45))
  for i in range(len(points)-1):
   for j in range(n):a=start+i*n+j;b=start+i*n+(j+1)%n;woodf.append((a,b,b+n,a+n))
  woodf.append(tuple(start+j for j in reversed(range(n))));woodf.append(tuple(start+(len(points)-1)*n+j for j in range(n)))
 def branch(a,b,r0,r1,bend=.18):
  side=Vector((rng.uniform(-1,1),rng.uniform(-1,1),0));pts=[]
  for k in range(6):t=k/5;pts.append(a.lerp(b,t)+side*(math.sin(math.pi*t)*bend))
  tube(pts,[r0*(1-k/5)+r1*(k/5) for k in range(6)],8 if r0>.06 else 6)
 # Prototype nominal height eight metres; trunk forks rather than ending in a floating leaf ball.
 lean=Vector((rng.uniform(-.36,.36),rng.uniform(-.28,.28),0));trunk=[Vector((0,0,0)),Vector((.03,-.02,.45)),Vector((lean.x*.4,lean.y*.4,1.8)),Vector((lean.x,lean.y,3.2)),Vector((lean.x+.13,lean.y-.1,4.7))];tube(trunk,[.27,.225,.175,.135,.075],11)
 for j in range(5):
  a=math.tau*j/5+rng.uniform(-.2,.2);p=Vector((math.cos(a)*.60,math.sin(a)*.60,.03));branch(p,Vector((0,0,.8)),.09,.12,.04)
 nmain=7 if hero else 6
 if kind=='pine':nmain=9
 for i in range(nmain):
  a=i*2.399963+rng.uniform(-.30,.30);z=2.85+(i/(nmain-1))*1.45;start=Vector((lean.x*.8,lean.y*.8,z));reach=rng.uniform(2.0,3.25);end=Vector((math.cos(a)*reach,math.sin(a)*reach,rng.uniform(5.1,6.9)))
  if kind=='pine':end.z=4.6+(i%3)*.88;reach=2.5-(i%3)*.48;end.x=math.cos(a)*reach;end.y=math.sin(a)*reach
  branch(start,end,.12 if hero else .10,.032,.24)
  ns=5 if hero else 3
  for j in range(ns):
   t=.48+j*(.48/max(1,ns-1));base=start.lerp(end,t);aa=a+rng.uniform(-1.2,1.2);rr=rng.uniform(.65,1.15);tip=base+Vector((math.cos(aa)*rr,math.sin(aa)*rr,rng.uniform(.25,.80)));branch(base,tip,.033,.008,.12)
   # Each foliage lobe grows around fine twig tips; overlap some lobes, preserve crown windows.
   for k in range(2):
    aa2=aa+(-.7 if k==0 else .7);e=tip+Vector((math.cos(aa2)*.45,math.sin(aa2)*.45,rng.uniform(.1,.45)));branch(tip,e,.009,.002,.035);rad=(rng.uniform(.62,.94),rng.uniform(.58,.90),rng.uniform(.48,.73))
    if kind=='pine':rad=(rad[0]*1.12,rad[1]*1.12,rad[2]*.48)
    clouds.append((e,rad))
 leaves_per_cloud=340 if hero else 220
 if kind=='pine':leaves_per_cloud=330
 for ci,(center,radii) in enumerate(clouds):
  tint=rng.randrange(len(greens))
  for k in range(leaves_per_cloud):
   # Bounded Gaussian masses avoid a mechanically uniform spherical shell.
   q=Vector((rng.gauss(0,.44),rng.gauss(0,.44),rng.gauss(0,.42)))
   if q.length>1.2:q*=1.2/q.length
   p=center+Vector((q.x*radii[0],q.y*radii[1],q.z*radii[2]));length=rng.uniform(.13,.23);width=length*rng.uniform(.33,.48)
   if kind=='pine':length*=.75;width*=.40
   yaw=rng.random()*math.tau;u=Vector((math.cos(yaw),math.sin(yaw),rng.uniform(-.45,.45))).normalized();v=u.cross(Vector((rng.uniform(-.25,.25),rng.uniform(-.25,.25),1))).normalized();n=u.cross(v).normalized();base=len(leafv)
   # Folded six-vertex blade, opaque on both sides; no broad-card silhouettes or alpha fringes.
   leafv += [tuple(p-u*length*.5),tuple(p+v*width*.5-u*length*.06),tuple(p+u*length*.5),tuple(p-v*width*.5-u*length*.06),tuple(p+n*length*.10-u*length*.04),tuple(p-n*length*.018)]
   leaff += [(base,base+1,base+4),(base+1,base+2,base+4),(base+2,base+3,base+4),(base+3,base,base+4),(base+3,base+2,base+5),(base+2,base+1,base+5),(base+1,base,base+5),(base,base+3,base+5)]
   slot=tint if rng.random()<.70 else rng.randrange(len(greens));leafmi.extend([slot]*8)
  if kind=='flower' and ci%2==0:
   for k in range(8):
    a=rng.random()*math.tau;p=center+Vector((radii[0]*.65*math.cos(a),radii[1]*.65*math.sin(a),radii[2]*rng.uniform(.05,.55)));b=len(flower_v);flower_v.append(tuple(p))
    for j in range(5):aa=math.tau*j/5;flower_v.append(tuple(p+Vector((math.cos(aa)*.075,math.sin(aa)*.075,.025))))
    for j in range(5):flower_f.append((b,b+1+j,b+1+(j+1)%5))
 def mesh(n,vs,fs,mats,indices=None,uvco=None):
  me=bpy.data.meshes.new(n);me.from_pydata(vs,[],fs);me.update()
  for ma in mats:me.materials.append(ma)
  for i,p in enumerate(me.polygons):p.use_smooth=True;p.material_index=indices[i] if indices else 0
  if uvco:
   uv=me.uv_layers.new()
   for p in me.polygons:
    for li in p.loop_indices:uv.data[li].uv=uvco[me.loops[li].vertex_index]
  return me
 parts={'WOOD':mesh('P12_'+name+'_BRANCH_SKELETON',woodv,woodf,[bark],uvco=wooduv),'LEAVES':mesh('P12_'+name+'_FOLDED_LEAVES',leafv,leaff,greens,leafmi)}
 if flower_v:parts['FLOWERS']=mesh('P12_'+name+'_TERMINAL_BLOSSOMS',flower_v,flower_f,[blossom])
 maxz=max(v[2] for v in leafv);xs=[v[0] for v in leafv];ys=[v[1] for v in leafv];info={'height':maxz,'width':max(max(xs)-min(xs),max(ys)-min(ys)),'parts':parts,'leaf_count':len(leafv)//6,'branch_tubes':nmain,'kind':kind}
 prototypes[name]=info;return info
for i in range(3):make_proto('BROAD_'+str(i),5100+i)
make_proto('PINE_0',5140,'pine');make_proto('COURT_FLOWER',5150,'flower',True);make_proto('OLD_WIDE',5160,'broad',True)
# Determine actual root from original trunk's lowest ring, not its object origin.
def root_of(o):
 z=min(v.co.z for v in o.data.vertices);pts=[o.matrix_world@v.co for v in o.data.vertices if v.co.z<z+.06];return sum(pts,Vector())/len(pts)
def world_bounds(obs):
 p=[o.matrix_world@Vector(v) for o in obs for v in o.bound_box];return [min(v[k] for v in p) for k in range(3)],[max(v[k] for v in p) for k in range(3)]
retired=[];placed=[]
def hide(obs):
 for o in obs:o.hide_render=True;o.hide_viewport=True;retired.append(o.name)
def place(prefix,root,height,width,proto,angle,flower=False):
 info=prototypes[proto];sz=height/info['height'];sx=width/info['width'];mat=Matrix.Translation(root)@Matrix.Rotation(angle,4,'Z')@Matrix.Diagonal((sx,sx,sz,1))
 for role,me in info['parts'].items():
  n=('SM_Z01_BOTANICAL_'+role) if flower else prefix+'_'+role;o=bpy.data.objects.new(n,me);C.objects.link(o);o.matrix_world=mat;o['source']='Original procedural branch-led botanical tree; retained CC0 bark photograph';o['prototype']=proto
 placed.append({'name':prefix,'root':list(root),'height_m':height,'width_m':width,'prototype':proto})
heroes=[]
for name,h,w,proto in [('Z01_COURTYARD_TREE',5.6,6.5,'COURT_FLOWER'),('Z08_WISH_TREE',7.4,10.4,'OLD_WIDE'),('Z09_LANDSCAPE_TREE',10.1,12.0,'OLD_WIDE'),('Z11_GARDEN_TREE',4.8,4.8,'BROAD_1')]:
 old=[s.objects['SM_'+name+'_ALIGNED_'+k] for k in ['TRUNK','BRANCHES','LEAVES']];root=root_of(old[0]);heroes.append(root);hide(old);place('SM_P12_'+name,root,h,w,proto,len(heroes)*.83,flower=name.startswith('Z01'))
# Retire only the old main-tree flowers, whose branches have been replaced.
hide([o for o in s.objects if not o.hide_render and o.name.startswith('SM_Z01_SPARSE_RED_FLOWER')])
groups={}
for o in list(s.objects):
 if o.type=='MESH' and not o.hide_render and o.name.startswith('SM_CONTEXT_TREE_'):
  key=re.match(r'SM_CONTEXT_TREE_(\d+)_',o.name).group(1);groups.setdefault(key,[]).append(o)
duplicates=[]
for key,obs in sorted(groups.items(),key=lambda p:int(p[0])):
 tr=next((o for o in obs if o.name.endswith('_TRUNK')),None)
 if not tr:continue
 root=root_of(tr);lo,hi=world_bounds(obs);H=hi[2]-root.z;W=max(hi[0]-lo[0],hi[1]-lo[1]);hide(obs)
 if any((root-r).length<1.0 for r in heroes):duplicates.append(key);continue
 rng=random.Random(5200+int(key));height=max(4.1,min(8.0,H*rng.uniform(.97,1.13)));width=max(3.7,min(6.4,W*rng.uniform(1.04,1.18)));place('SM_P12_CONTEXT_'+key,root,height,width,'BROAD_'+str(int(key)%3),rng.random()*math.tau)
# The three existing framing saplings also use the same botanical prototypes, with independent whole-tree transforms.
for i in range(3):
 obs=[o for o in s.objects if not o.hide_render and o.name.startswith('SM_P9_FRAMING_SAPLING_') and (i==0 and '.' not in o.name or i>0 and o.name.endswith('.'+str(i).zfill(3)))]
 tr=next((o for o in obs if '_TRUNK' in o.name),None)
 if tr:
  root=root_of(tr);lo,hi=world_bounds(obs);hide(obs);place('SM_P12_FRAMING_'+str(i),root,max(4,hi[2]-root.z),max(3.5,hi[0]-lo[0]),'BROAD_'+str(i%3),i*1.9)
# Existing pine locations remain; replace the old separate trunk/tuft construction as a whole species group.
pines=[o for o in list(s.objects) if o.type=='MESH' and not o.hide_render and o.name.startswith('SM_BACKGROUND_PINE_TRUNK')];roots=[]
for i,o in enumerate(pines):
 root=root_of(o);lo,hi=world_bounds([o]);roots.append(root);hide([o]);place('SM_P12_PINE_'+str(i),root,max(4.6,min(7,hi[2]-root.z+.7)),3.6+(i%3)*.35,'PINE_0',i*2.4)
hide([o for o in s.objects if not o.hide_render and o.name.startswith('SM_BACKGROUND_PINE_TUFT')])
# Leave original sources and action library intact; only the positively identified tree groups are retired.
s['version']='p3-v051';s['phase_status']='P12_BOTANICAL_TREE_REFERENCE_COMPARISON';p=R/'manifest/scene-manifest.json';m=json.loads(p.read_text());m.update(version='p3-v051',source_version='p3-v051',source_snapshot=out.name);m['botanical_tree_revision']={'reference_imagegen':'evidence/tree-reference-study-20261022/tree-canopy-proposal-imagegen-v001.png','instance_count':len(placed),'prototype_count':len(prototypes),'duplicate_context_groups_retired':duplicates,'geometry':'branch-led opaque folded leaf meshes; no alpha-card canopy','layout_unchanged':True};p.write_text(json.dumps(m,ensure_ascii=False,indent=2));s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
report={'version':'p3-v051','retired_objects':retired,'trees':placed,'duplicate_context_groups':duplicates,'prototypes':{n:{k:v for k,v in d.items() if k!='parts'} for n,d in prototypes.items()},'preserved':'building/terrain/path/bridge/camera transforms, fish/cat animation; old tree meshes retained hidden'};(R/'evidence/botanical-trees-v051.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('BOTANICAL_TREES',len(placed),len(retired),len(duplicates),flush=True)
