"""Bake original CC0 tree geometry into transparent far-distance sprites, not reference images."""
import bpy,math,json
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1]
if bpy.data.filepath:raise RuntimeError('Fresh unsaved bake process required')
s=bpy.context.scene
for o in list(s.objects):bpy.data.objects.remove(o,do_unlink=True)
need=['CC0_TREE_trunk_NEAR','CC0_TREE_branches_NEAR','CC0_LEAF_CARDS_NEAR']
with bpy.data.libraries.load(str(R/'zen-space-p3-v022.blend'),link=False) as (a,b):b.objects=[n for n in a.objects if n in need]
for o in b.objects:
 s.collection.objects.link(o);o.hide_render=False;o.hide_viewport=False
 if 'LEAF_CARDS' in o.name:
  o.data=o.data.copy()
  for f in o.data.polygons:
   c=sum((o.data.vertices[i].co for i in f.vertices),Vector())/len(f.vertices)
   for i in f.vertices:o.data.vertices[i].co=c+(o.data.vertices[i].co-c)*1.25
bpy.context.view_layer.update();points=[o.matrix_world@v.co for o in b.objects for v in o.data.vertices];mn=Vector(tuple(min(v[k] for v in points) for k in range(3)));mx=Vector(tuple(max(v[k] for v in points) for k in range(3)));center=(mn+mx)/2;size=mx-mn
world=bpy.data.worlds.new('Bake daylight');s.world=world;world.use_nodes=True;bg=next(n for n in world.node_tree.nodes if n.type=='BACKGROUND');bg.inputs['Color'].default_value=(.63,.73,.82,1);bg.inputs['Strength'].default_value=.8
ld=bpy.data.lights.new('Bake sun','SUN');ld.energy=2;ld.angle=.18;lo=bpy.data.objects.new('Bake sun',ld);s.collection.objects.link(lo);lo.rotation_euler=(.5,-.3,-.8)
cd=bpy.data.cameras.new('Bake camera');cam=bpy.data.objects.new('Bake camera',cd);s.collection.objects.link(cam);s.camera=cam;cd.type='ORTHO';cd.ortho_scale=max(size.z,size.x,size.y)*1.16
s.render.engine='CYCLES';s.cycles.samples=24;s.cycles.use_denoising=True;s.cycles.transparent_max_bounces=16;prefs=bpy.context.preferences.addons['cycles'].preferences;prefs.compute_device_type='METAL';prefs.get_devices()
for d in prefs.devices:d.use=d.type=='METAL'
s.cycles.device='GPU';s.render.resolution_x=s.render.resolution_y=512;s.render.resolution_percentage=100;s.render.film_transparent=True;s.render.image_settings.file_format='PNG';s.render.image_settings.color_mode='RGBA';s.view_settings.view_transform='AgX';s.view_settings.look='AgX - Medium High Contrast';s.view_settings.exposure=.3
out=R/'assets/derived/forest-impostors';out.mkdir(parents=True,exist_ok=True);rows=[]
for i,angle in enumerate([.55,2.1,4.3]):
 cam.location=center+Vector((math.cos(angle)*20,math.sin(angle)*20,1));cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler();s.render.filepath=str(out/f'tree-{i+1}.png');bpy.ops.render.render(write_still=True);rows.append({'file':str(Path(s.render.filepath).relative_to(R)),'angle_rad':angle})
(R/'manifest/far-forest-impostor-source.json').write_text(json.dumps({'source':'https://polyhaven.com/a/tree_small_02','license':'CC0','author':'Rico Cilliers','derivation':'Cycles transparent orthographic render of source tree trunk/branches/leaf cards, enlarged whole-leaf coverage 1.25x; no reference-image planes','use':'far context only, outside functional garden','sprite_world_aspect':1,'source_tree_height':size.z,'files':rows},indent=2));print('FOREST_SPRITES_BAKED',rows,flush=True)
