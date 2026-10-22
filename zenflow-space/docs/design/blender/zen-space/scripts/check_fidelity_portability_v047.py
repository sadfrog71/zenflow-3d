import bpy,json
from pathlib import Path
s=bpy.context.scene
images=[{'name':i.name,'packed':bool(i.packed_file),'used':i.users} for i in bpy.data.images if i.source=='FILE' and i.users]
fonts=[{'name':f.name,'packed':bool(f.packed_file),'builtin':f.filepath=='<builtin>'} for f in bpy.data.fonts if f.users]
r={'source':bpy.data.filepath,'images':images,'fonts':fonts,'linked_libraries':len(bpy.data.libraries),'all_used_images_packed':all(i['packed'] for i in images),'all_used_fonts_packed_or_builtin':all(i['packed'] or i['builtin'] for i in fonts),'action_count':len(bpy.data.actions)}
# Remove external fallback paths in memory only. Render a small source camera image.
for i in bpy.data.images:
 if i.source=='FILE':i.filepath='/nonexistent-portability-probe/'+i.name
for f in bpy.data.fonts:
 if f.filepath!='<builtin>':f.filepath='/nonexistent-portability-probe/'+f.name
s.camera=bpy.data.objects['CAM_LIBRARY_SHELF'];s.render.engine='CYCLES';s.cycles.samples=4;s.render.resolution_x=640;s.render.resolution_y=360;s.render.resolution_percentage=100;s.render.filepath='/tmp/zen-space-fidelity-portability/reopened-render.png';bpy.ops.render.render(write_still=True)
r['render_exists']=Path(s.render.filepath).is_file();Path('/Users/mark.ma/Documents/AI coding/zenflow-space/docs/design/blender/zen-space/evidence/portability-check-p3-v047.json').write_text(json.dumps(r,indent=2));print('PORTABILITY',r['all_used_images_packed'],r['all_used_fonts_packed_or_builtin'],r['linked_libraries'],r['render_exists'])
