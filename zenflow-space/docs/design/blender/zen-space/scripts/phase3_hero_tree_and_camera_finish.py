import bpy,math,json,random
from pathlib import Path
from mathutils import Vector,Matrix
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v018.blend'
if s.get('version')!='p3-v017' or out.exists():raise RuntimeError('Requires new v018 from v017')
C=bpy.data.collections.new('P3_ALIGNED_PHOTO_HERO_TREES');s.collection.children.link(C);parts={role:bpy.data.objects['CC0_TREE_'+role+'_NEAR'].data for role in ['trunk','branches']};parts['leaves']=bpy.data.objects['CC0_LEAF_CARDS_NEAR'].data
pts=[v.co for v in parts['leaves'].vertices];mn=Vector(tuple(min(v[k] for v in pts) for k in range(3)));mx=Vector(tuple(max(v[k] for v in pts) for k in range(3)));size=mx-mn;tr=parts['trunk'];zmin=min(v.co.z for v in tr.vertices);roots=[v.co for v in tr.vertices if v.co.z<zmin+.08];root=Vector((sum(v.x for v in roots)/len(roots),sum(v.y for v in roots)/len(roots),zmin));h=mx.z-zmin;rows=[]
heroes=[('Z01_COURTYARD_TREE',0,9,0,7.8,7.4,6.3),('Z08_WISH_TREE',-26,31,4.5,8.0,7.2,6.5),('Z09_LANDSCAPE_TREE',0,36,7.5,11.0,10.5,9.0),('Z11_GARDEN_TREE',35,25,0,4.2,4.0,4.0)]
for i,(name,x,y,z,w,d,height) in enumerate(heroes):
 for o in list(s.objects):
  if o.name.startswith('SM_'+name) and any(k in o.name for k in ['TRUNK','BRANCH','ROOT','_CC0_']):o.hide_render=True;o.hide_viewport=True
 scale=Vector((w/size.x,d/size.y,height/h));mat=Matrix.Translation(Vector((x,y,z)))@Matrix.Rotation(i*.83,4,'Z')@Matrix.Diagonal((*scale,1))@Matrix.Translation(-root)
 for role,me in parts.items():
  ob=bpy.data.objects.new('SM_'+name+'_ALIGNED_'+role.upper(),me);C.objects.link(ob);ob.matrix_world=mat;ob['source']='https://polyhaven.com/a/tree_small_02';ob['license']='CC0';ob['shape']='whole source tree, shared transform for trunk/branches/leaves'
 rows.append({'tree':name,'root':[x,y,z],'height_m':height,'width_m':w})
# Keep the original main-court red flowers sparse; no wish tags on the other two landmark trees.
cam=s.objects['CAM_CHAT_SEATED'];cam.rotation_euler=(Vector((35.5,23.5,1.6))-cam.location).to_track_quat('-Z','Y').to_euler()
# Cloud-sea volume is a weather variant in the far valley; no geometry or path is hidden by it.
bpy.ops.mesh.primitive_cube_add(size=1,location=(160,245,-5));cloud=bpy.context.object;cloud.name='ENV_FAR_VALLEY_CLOUD_SEA';cloud.scale=(470,350,16);cloud.hide_render=True;cloud.hide_viewport=True;cloud['weather_variant']='dawn/sunset only; source volume, runtime reconstruction required'
mat=bpy.data.materials.new('P3_DISTANT_CLOUD_VOLUME');mat.use_nodes=True;nt=mat.node_tree;nt.nodes.clear();o=nt.nodes.new('ShaderNodeOutputMaterial');vol=nt.nodes.new('ShaderNodeVolumePrincipled');vol.inputs['Color'].default_value=(.84,.87,.90,1);vol.inputs['Anisotropy'].default_value=.15;tex=nt.nodes.new('ShaderNodeTexNoise');tex.inputs['Scale'].default_value=6;tex.inputs['Detail'].default_value=4;coord=nt.nodes.new('ShaderNodeTexCoord');nt.links.new(coord.outputs['Generated'],tex.inputs['Vector']);ramp=nt.nodes.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.48;ramp.color_ramp.elements[0].color=(0,0,0,1);ramp.color_ramp.elements[1].position=.69;ramp.color_ramp.elements[1].color=(.10,.10,.10,1);nt.links.new(tex.outputs[0],ramp.inputs[0]);nt.links.new(ramp.outputs[0],vol.inputs['Density']);nt.links.new(vol.outputs[0],o.inputs['Volume']);cloud.data.materials.append(mat)
def find(l,n):
 if l.name==n:return l
 for c in l.children:
  rr=find(c,n)
  if rr:return rr
lc=find(s.view_layers['LAYOUT_CUTAWAY'].layer_collection,C.name)
if lc:lc.exclude=True
s['version']='p3-v018';s['phase_status']='P4_REFERENCE_DIFFERENCE_RECHECK';s.camera=s.objects['CAM_ENTRY_SEATED'];s.frame_set(1);t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v017','p3-v018'))
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v018';m['source_snapshot']=out.name
for c in m['cameras']:
 if c['name']=='CAM_CHAT_SEATED':c['rotation_euler']=list(cam.rotation_euler);c['target']=[35.5,23.5,1.6]
(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(R/'evidence/hero-tree-alignment-repair.json').write_text(json.dumps({'version':'p3-v018','trees':rows,'method':'one root-aligned shared transform for original CC0 trunk, branches and leaf cards; procedural forks preserved hidden','camera':'chat view re-aimed to retain full side lamp and raised garden view','cloud_sea':'far-valley weather variant, not a structural concealment'},indent=2));print('HERO_ALIGNMENT_FINISHED',rows,flush=True)
