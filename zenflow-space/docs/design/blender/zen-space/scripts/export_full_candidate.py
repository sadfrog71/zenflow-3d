"""Exchange copy only. Adapt unsupported shaders explicitly; never save adaptations into the master."""
import bpy,json,sys,re,time,struct,math
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [];sample='--sample' in args;left='--hand-lh' in args;surface='--surface-kit' in args;start=time.monotonic();s.frame_set(1)
filename='states-kit-v001.glb' if sample else 'writing-left-layout-v001.glb' if left else 'surface-kit-v001.glb' if surface else 'zen-space-'+s.get('version','unversioned')+'.glb';out=R/'exports'/filename
for vl in s.view_layers:vl.use=vl.name=='WORLD_FULL'
bpy.context.window.view_layer=s.view_layers['WORLD_FULL'];vl=bpy.context.view_layer;adapted=[]
for m in bpy.data.materials:
 if not m.use_nodes:continue
 nt=m.node_tree;p=next((n for n in nt.nodes if n.type=='BSDF_PRINCIPLED'),None)
 if not p:continue
 if m.get('exchange_use_principled_fallback'):
  output=next(n for n in nt.nodes if n.type=='OUTPUT_MATERIAL');nt.links.new(p.outputs['BSDF'],output.inputs['Surface']);adapted.append({'material':m.name,'change':'diffuse thin-paper transmission becomes source Principled fallback; recreate scattering/light transport in runtime'})
 inp=p.inputs['Base Color'];image=m.get('exchange_base_image')
 if image:
  node=next(n for n in nt.nodes if n.type=='TEX_IMAGE' and n.image and n.image.name==image);nt.links.new(node.outputs['Color'],inp);adapted.append({'material':m.name,'change':'original rock photograph exported; procedural moss requires runtime recreation'})
 elif inp.links:
  src=inp.links[0].from_node
  if src.type=='HUE_SAT' and src.inputs['Color'].links:
   nt.links.new(src.inputs['Color'].links[0].from_socket,inp);adapted.append({'material':m.name,'change':'photograph and constant warm tint exported; source HSV value boost requires runtime colour correction'})
  elif src.type not in ['TEX_IMAGE','MIX_RGB','MATH','GROUP']:
   for link in list(inp.links):nt.links.remove(link)
   inp.default_value=m.diffuse_color;adapted.append({'material':m.name,'change':'procedural colour becomes neutral PBR factor; source effect requires runtime recreation'})
 alpha=p.inputs['Alpha']
 if alpha.links and any(k in m.name.lower() for k in ['leaf','leaves','fern','sprite']):
  src=alpha.links[0].from_socket;clip=nt.nodes.new('ShaderNodeMath');clip.operation='GREATER_THAN';clip.inputs[1].default_value=.35;nt.links.new(src,clip.inputs[0]);nt.links.new(clip.outputs[0],alpha);adapted.append({'material':m.name,'change':'alpha MASK at .35 for stable tree/fern/sprite cutouts in glTF'})
for cname in ['STATE_Z05_RH_PROPS','STATE_Z05_LH_PROPS']:
 c=bpy.data.collections.get(cname)
 if c:
  for ob in c.objects:ob['handedness']='RH' if '_RH_' in cname else 'LH'
if left:
 for ob in bpy.data.collections['STATE_Z05_LH_PROPS'].objects:ob.hide_render=False;ob.hide_viewport=False
 visible=list(bpy.data.collections['STATE_Z05_LH_PROPS'].objects)
elif surface:
 sys.path.insert(0,str(R/'scripts'));from phase2_materials import box,beam,camera,light,assign
 c=bpy.data.collections.new('TEMP_EXCHANGE_SURFACE_KIT');s.collection.children.link(c)
 box('SCALE_1M',(-1.5,0,.5),(.015,.015,1),c,bpy.data.materials['P2_Darkwood'])
 box('KIT_PHOTO_GROUND',(0,0,0),(2.4,2.4,.04),c,bpy.data.materials['P4_PHOTOGRAPHED_LEAFY_GROUND'])
 box('KIT_STONE_BASIN',(0,0,.045),(1.05,1.05,.05),c,bpy.data.materials['P4_MOSSY_BANK_ROCK'])
 box('KIT_TRANSMISSIVE_WATER',(0,0,.085),(.95,.95,.006),c,bpy.data.materials['P2_Water'])
 bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2,radius=.14,location=(.1,0,.08));o=bpy.context.object;o.name='KIT_SUBMERGED_STONE'
 for cc in list(o.users_collection):cc.objects.unlink(o)
 c.objects.link(o);assign(o,bpy.data.materials['P4_MOSSY_BANK_ROCK'])
 box('KIT_PAPER_LAMP',(.82,.22,.32),(.20,.20,.30),c,bpy.data.materials['P2_Glow'],.005);light('KIT_LAMP_LIGHT',(.82,.22,.50),4,(1,.64,.3),c,.05,'POINT')
 me=bpy.data.meshes.new('KIT_TREE_ALPHA');me.from_pydata([(-.95,.35,.02),(-.15,.35,.02),(-.15,.35,.82),(-.95,.35,.82)],[],[(0,1,2,3)]);me.materials.append(bpy.data.materials['P4_FAR_FOREST_SPRITE_0']);uv=me.uv_layers.new()
 for li,co in zip(me.polygons[0].loop_indices,[(0,0),(1,0),(1,1),(0,1)]):uv.data[li].uv=co
 o=bpy.data.objects.new('KIT_SOURCE_TREE_ALPHA',me);c.objects.link(o);camera('CAM_EXCHANGE_SURFACE_KIT',(3,-4,2.7),(0,0,.3),c,40);visible=list(c.objects)
else:
 visible=[o for o in s.objects if not o.hide_render and not o.hide_viewport and o.visible_get(view_layer=vl) and o.type in ['MESH','EMPTY','FONT','CURVE','CAMERA','LIGHT']]
 if sample:
  allowed=('ANCHOR_Z01_SPARROW','PIVOT_Z01_SPARROW','SM_Z01_SPARROW','SM_Z01_WING_FEATHER','SM_Z05_OIL_FLAME','PIVOT_Z07_BELL','SM_Z07_BRONZE_BELL','SM_Z07_BELL_BAND','SM_Z07_OVAL_WORN_STRIKE');visible=[o for o in visible if o.name.startswith(allowed)]
merged=[]
if not sample and not left and not surface:
 keep=('CUP','LAMP','DESK','PAPER','BOOK','BELL','TAG','CUSHION','BOWL','BIRD','SPARROW','FISH','RIPPLE','SHADOW','WATER','CONTEXT','FERN','LEAF','TREE','FLOWER');groups={}
 for ob in visible:
  if ob.type!='MESH' or ob.parent or ob.animation_data or ob.data.users>1 or ob.name.startswith(('CONTENT','SELECT')) or any(k in ob.name for k in keep) or not ob.data.materials or len(ob.data.materials)!=1 or any(c.name.startswith('STATE_Z05_') for c in ob.users_collection):continue
  z=re.search(r'Z\d\d',ob.name)
  if not z:continue
  key=(z.group(),any('ROOF' in c.name for c in ob.users_collection),ob.data.materials[0].name);groups.setdefault(key,[]).append(ob)
 for key,obs in groups.items():
  if len(obs)<3:continue
  bpy.ops.object.select_all(action='DESELECT')
  for ob in obs:ob.select_set(True)
  bpy.context.view_layer.objects.active=obs[0];bpy.ops.object.join();ob=bpy.context.object;ob.name='SM_EXPORT_'+key[0]+('_ROOF_' if key[1] else '_STATIC_')+key[2];merged.append({'node':ob.name,'source_objects':len(obs)})
 visible=[o for o in s.objects if not o.hide_render and not o.hide_viewport and o.visible_get(view_layer=vl) and o.type in ['MESH','EMPTY','FONT','CURVE','CAMERA','LIGHT']]
bpy.ops.object.select_all(action='DESELECT')
for ob in visible:ob.select_set(True)
if visible:bpy.context.view_layer.objects.active=visible[0]
out.parent.mkdir(exist_ok=True)
bpy.ops.export_scene.gltf(filepath=str(out),export_format='GLB',use_selection=True,use_visible=True,use_renderable=True,export_apply=True,export_extras=True,export_cameras=True,export_lights=True,export_yup=True,export_animations=sample,export_animation_mode='ACTIONS',export_merge_animation='NONE',export_bake_animation=False,export_force_sampling=False,export_frame_range=False,export_anim_slide_to_zero=True,export_image_format='AUTO')
b=out.read_bytes();length,kind=struct.unpack_from('<II',b,12);g=json.loads(b[20:20+length]);report={'source_version':s.get('version'),'source':bpy.data.filepath,'file':str(out.relative_to(R)),'bytes':len(b),'seconds':time.monotonic()-start,'nodes':len(g.get('nodes',[])),'meshes':len(g.get('meshes',[])),'materials':len(g.get('materials',[])),'images':len(g.get('images',[])),'animations':[a.get('name') for a in g.get('animations',[])],'extensions':g.get('extensionsUsed',[]),'alpha_mask_materials':[m.get('name') for m in g.get('materials',[]) if m.get('alphaMode')=='MASK'],'adapted_materials':adapted,'master_saved_after_export_adaptation':False,'merged_static_groups':merged,'scope':'desktop exchange validation; static full model plus optional LH props; animation library remains in Blender source / independent animation sample; no mobile claim'}
name='states-kit-export-check.json' if sample else 'writing-left-layout-export-check.json' if left else 'surface-kit-export-check.json' if surface else 'full-glb-export-check.json';(R/'evidence'/name).write_text(json.dumps(report,ensure_ascii=False,indent=2));print('EXPORT_COMPLETE',report['file'],len(b),report['animations'],report['extensions'],flush=True)
