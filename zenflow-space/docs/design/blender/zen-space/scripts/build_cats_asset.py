"""Original adult cats: anatomical sculpted masses, facial details and quiet poses, no external model."""
import bpy,math,json,random
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];O=R/'assets/living';O.mkdir(exist_ok=True);out=O/'cats-original-v001.blend'
if out.exists():raise RuntimeError('Asset version exists')
bpy.ops.wm.read_factory_settings(use_empty=True);s=bpy.context.scene;s.unit_settings.system='METRIC';C=bpy.data.collections.new('P6_ORIGINAL_CATS');s.collection.children.link(C)
def move(o):
 for c in list(o.users_collection):c.objects.unlink(o)
 C.objects.link(o);return o
def mat(name,col,rough=.8):
 m=bpy.data.materials.new(name);m.use_nodes=True;m.diffuse_color=(*col,1);p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(*col,1);p.inputs['Roughness'].default_value=rough;return m
cream=mat('P6_CAT_IVORY_FUR',(.64,.61,.52),.95);nose=mat('P6_CAT_NOSE',(.32,.16,.13),.53);inner=mat('P6_CAT_INNER_EAR',(.33,.23,.20),.9);black=mat('P6_CAT_EYE_AND_LID',(.009,.012,.010),.3);gold=mat('P6_CAT_EYE_AMBER',(.32,.39,.12),.25);whisker=mat('P6_CAT_WHISKER',(.57,.57,.50),.9)
def fur(name,base,tabby=False):
 m=mat(name,base,.91);nt=m.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Sheen Weight'].default_value=.28;p.inputs['Sheen Roughness'].default_value=.8;coord=nt.nodes.new('ShaderNodeTexCoord');noise=nt.nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=180;noise.inputs['Detail'].default_value=2;nt.links.new(coord.outputs['Generated'],noise.inputs['Vector']);b=nt.nodes.new('ShaderNodeBump');b.inputs['Strength'].default_value=.18;b.inputs['Distance'].default_value=.00045;nt.links.new(noise.outputs['Fac'],b.inputs['Height']);nt.links.new(b.outputs[0],p.inputs['Normal'])
 if tabby:
  wave=nt.nodes.new('ShaderNodeTexWave');wave.wave_type='BANDS';wave.bands_direction='X';wave.inputs['Scale'].default_value=8;wave.inputs['Distortion'].default_value=4;wave.inputs['Detail Scale'].default_value=2;nt.links.new(coord.outputs['Generated'],wave.inputs['Vector']);r=nt.nodes.new('ShaderNodeValToRGB');r.color_ramp.elements[0].position=.32;r.color_ramp.elements[0].color=(.23,.105,.035,1);r.color_ramp.elements[1].position=.61;r.color_ramp.elements[1].color=(.57,.34,.14,1);nt.links.new(wave.outputs['Color'],r.inputs[0]);nt.links.new(r.outputs[0],p.inputs['Base Color'])
 return m
charcoal=fur('P6_CAT_CHARCOAL_FUR',(.055,.065,.058));amber=fur('P6_CAT_GINGER_TABBY_FUR',(.45,.25,.10),True)
def ell(name,pos,scale,material=None,parent=None):
 bpy.ops.mesh.primitive_uv_sphere_add(segments=32,ring_count=20,radius=1,location=pos);o=move(bpy.context.object);o.name=name;o.scale=scale;bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
 if material:o.data.materials.append(material)
 for f in o.data.polygons:f.use_smooth=True
 if parent:o.parent=parent
 return o
def empty(name):o=bpy.data.objects.new(name,None);C.objects.link(o);return o

def tube(name,points,radius,material,parent=None):
 # Interpolated Catmull-Rom with a tapered circular cross section.
 pp=[Vector(p) for p in points];samples=[]
 for j in range(len(pp)-1):
  a=pp[max(0,j-1)];b=pp[j];c=pp[j+1];d=pp[min(len(pp)-1,j+2)]
  for k in range(8):
   t=k/8;samples.append(.5*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t))
 samples.append(pp[-1]);vs=[];fs=[];n=10
 for i,p in enumerate(samples):
  tangent=(samples[min(i+1,len(samples)-1)]-samples[max(0,i-1)]).normalized();u=tangent.cross(Vector((0,0,1)))
  if u.length<.001:u=tangent.cross(Vector((1,0,0)))
  u.normalize();v=tangent.cross(u).normalized();r=radius*(1-.65*i/(len(samples)-1))
  for j in range(n):a=j*math.tau/n;vs.append(tuple(p+u*math.cos(a)*r+v*math.sin(a)*r))
 for i in range(len(samples)-1):
  for j in range(n):a=i*n+j;b=i*n+(j+1)%n;fs.append((a,b,b+n,a+n))
 fs.extend([tuple(reversed(range(n))),tuple((len(samples)-1)*n+j for j in range(n))]);me=bpy.data.meshes.new(name);me.from_pydata(vs,[],fs);me.update();o=bpy.data.objects.new(name,me);C.objects.link(o);me.materials.append(material)
 for f in me.polygons:f.use_smooth=True
 if parent:o.parent=parent
 return o

def ear(name,center,side,material,parent):
 x,y,z=center;verts=[(x-side*.028,y-.025,z),(x+side*.028,y-.015,z-.006),(x+side*.033,y+.022,z),(x+side*.023,y-.005,z+.066)];faces=[(0,1,3),(1,2,3),(2,0,3),(0,2,1)];me=bpy.data.meshes.new(name);me.from_pydata(verts,[],faces);me.update();o=bpy.data.objects.new(name,me);C.objects.link(o);me.materials.append(material);be=o.modifiers.new('Soft ear rim','BEVEL');be.width=.004;be.segments=3;o.parent=parent
 # A smaller inner fold sits on the forward face, rather than a painted triangle.
 v=[(x-side*.015,y-.0255,z+.010),(x+side*.016,y-.016,z+.006),(x+side*.020,y-.008,z+.051)];me=bpy.data.meshes.new(name+'_INNER');me.from_pydata(v,[],[(0,1,2)]);me.materials.append(inner);oo=bpy.data.objects.new(name+'_INNER',me);C.objects.link(oo);oo.parent=parent
 return o

def sculpt(parts,name,material,parent):
 bpy.ops.object.select_all(action='DESELECT')
 for o in parts:o.select_set(True)
 bpy.context.view_layer.objects.active=parts[0];bpy.ops.object.join();o=bpy.context.object;o.name=name;mod=o.modifiers.new('Unified anatomical surface','REMESH');mod.mode='VOXEL';mod.voxel_size=.0042;mod.use_smooth_shade=True;bpy.ops.object.modifier_apply(modifier=mod.name);sm=o.modifiers.new('Gentle muscle transitions','SMOOTH');sm.factor=.65;sm.iterations=4;bpy.ops.object.modifier_apply(modifier=sm.name);o.data.materials.clear();o.data.materials.append(material);o.parent=parent
 for p in o.data.polygons:p.use_smooth=True
 return o

def face(prefix,headpos,parent,sleep=False):
 x,y,z=headpos
 for side in [-1,1]:
  ell(prefix+'_MUZZLE',(x+side*.016,y-.065,z-.021),(.022,.019,.017),cream,parent)
  ex=x+side*.036;ey=y-.063;ez=z+.014
  if sleep:tube(prefix+'_CLOSED_LID',[(ex-side*.012,ey,ez),(ex,ey-.004,ez-.004),(ex+side*.012,ey,ez+.001)],.0017,black,parent)
  else:
   eye=ell(prefix+'_EYE',(ex,ey,ez),(.013,.005,.010),gold,parent);ell(prefix+'_VERTICAL_PUPIL',(ex,ey-.0043,ez),(.0028,.0015,.0081),black,parent);ell(prefix+'_EYE_CATCHLIGHT',(ex-.003,ey-.0057,ez+.004),(.0017,.0008,.0017),cream,parent)
   tube(prefix+'_UPPER_LID',[(ex-.014,ey,ez),(ex,ey-.004,ez+.010),(ex+.014,ey,ez)],.0015,charcoal,parent)
 ell(prefix+'_NOSE',(x,y-.083,z-.010),(.009,.005,.0055),nose,parent)
 tube(prefix+'_MOUTH',[(x,y-.080,z-.015),(x,y-.080,z-.025),(x-.009,y-.075,z-.029)],.0009,black,parent)
 for side in [-1,1]:
  for k in range(3):
   tube(prefix+'_WHISKER',[(x+side*.022,y-.077,z-.02+k*.005),(x+side*.061,y-.075,z-.013+k*.01),(x+side*(.094+k*.006),y-.050,z-.027+k*.018)],.00035,whisker,parent)
 ear(prefix+'_EAR_L',(x-.048,y+.002,z+.045),-1,charcoal if not sleep else amber,parent);ear(prefix+'_EAR_R',(x+.048,y+.002,z+.045),1,charcoal if not sleep else amber,parent)
# Adult proportions: approximately 0.50m long and 0.45m to ear tip, restrained eyes and muzzle.
root=empty('P6_CAT_WATCH_ROOT');parts=[]
for name,pos,sc in [('TORSO',(0,.035,.205),(.103,.132,.18)),('CHEST',(0,-.045,.285),(.083,.095,.115)),('HEAD',(0,-.09,.385),(.079,.072,.070)),('HAUNCH_L',(-.078,.075,.115),(.077,.105,.091)),('HAUNCH_R',(.078,.075,.115),(.077,.105,.091)),('LEG_L',(-.046,-.073,.128),(.026,.029,.123)),('LEG_R',(.046,-.073,.128),(.026,.029,.123)),('PAW_L',(-.048,-.105,.029),(.037,.052,.027)),('PAW_R',(.048,-.105,.029),(.037,.052,.027))]:parts.append(ell('CAT_WATCH_'+name,pos,sc))
body=sculpt(parts,'SM_P6_CAT_WATCH_SCULPT',charcoal,root)
# White chest bib is a shallow conforming ellipsoid, with rounded edge and high fur roughness.
ell('SM_P6_CAT_WATCH_CHEST_BIB',(0,-.120,.255),(.052,.018,.090),cream,root)
for side in [-1,1]:ell('SM_P6_CAT_WATCH_WHITE_TOES',(side*.048,-.125,.025),(.032,.035,.020),cream,root)
face('SM_P6_CAT_WATCH',(0,-.09,.385),root)
tail=tube('SM_P6_CAT_WATCH_CURLED_TAIL',[(-.07,.15,.10),(-.16,.18,.06),(-.21,.10,.035),(-.20,-.03,.026),(-.11,-.13,.025)],.024,charcoal,root)
# A second adult, asleep in a compact curl, with its chin on crossed paws.
root2=empty('P6_CAT_SLEEP_ROOT');parts=[]
for name,pos,sc in [('TORSO',(0,.018,.108),(.171,.108,.083)),('HAUNCH',(.105,.025,.097),(.10,.109,.076)),('SHOULDER',(-.095,-.010,.099),(.084,.09,.073)),('HEAD',(-.120,-.054,.105),(.071,.062,.062)),('PAWS',(-.075,-.091,.027),(.095,.032,.025))]:parts.append(ell('CAT_SLEEP_'+name,pos,sc))
body2=sculpt(parts,'SM_P6_CAT_SLEEP_SCULPT',amber,root2);face('SM_P6_CAT_SLEEP',(-.120,-.054,.105),root2,True)
points=[]
for i in range(12):
 a=math.radians(45+i*24);points.append((.012+math.cos(a)*.192,.015+math.sin(a)*.133,.035+.020*(1+math.cos(a))))
tube('SM_P6_CAT_SLEEP_WRAPPED_TAIL',points,.025,amber,root2)
# Save both poses in local coordinates; placement, breathing and gaze authored in the garden file.
s['asset']='Original anatomical seated and sleeping adult cats';s['scale']='metres';s['source']='original procedural sculpt; no downloaded animal model';s['materials']='procedural fur and tabby patterns; bake exchange copy for glTF';bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(O/'cats-original-v001.json').write_text(json.dumps({'file':out.name,'authoring':'original procedural anatomical sculpt in Blender','poses':['seated watching water','curled asleep'],'body_surface':'voxel-unified smooth anatomical masses','details':['independent ears/inner folds','small adult eyes or closed lids','nose/muzzle/mouth','tapered whiskers','curved 3D tails','rough fur sheen and tabby coat'],'units':'metres','not_claimed':'strand hair or photogrammetry'},indent=2));print('CATS_ASSET_SAVED',len(C.objects),flush=True)
