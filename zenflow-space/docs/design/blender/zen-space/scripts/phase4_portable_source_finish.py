import bpy,json,sys
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v020.blend'
if s.get('version')!='p3-v019' or out.exists():raise RuntimeError('Requires new v020 from v019')
font=bpy.data.fonts.load(str(R/'assets/fonts/NotoSansCJKsc-Regular.otf'));font.pack();labels=[]
for o in s.objects:
 if o.type=='FONT':o.data.font=font;labels.append(o.name)
for f in list(bpy.data.fonts):
 if f!=font and f.name!='Bfont' and f.users==0:bpy.data.fonts.remove(f)
# Bind genuine lighting change to the actual foreground source lamp; environment light independent.
light=min([o for o in s.objects if o.type=='LIGHT' and o.name.startswith('P2_COPY_LAMP')],key=lambda o:(o.location-Vector((9.85,-3,.9))).length);data=light.data;data.energy=4;reg=json.loads((R/'manifest/object-animation-clips.json').read_text())
for name,keys,loop in [('Z05_LAMP_LIGHT_STABLE',[(1,4),(240,4)],True),('Z05_LAMP_LIGHT_FLICKER',[(1,4),(32,3.72),(66,4.12),(104,3.93),(168,4)],True),('Z05_LAMP_LIGHT_EXTINGUISH',[(1,4),(12,1.8),(24,0)],False),('Z05_LAMP_LIGHT_RELIGHT',[(1,0),(10,2.2),(24,4)],False)]:
 data.animation_data_create();data.animation_data.action=None
 for fr,value in keys:data.energy=value;data.keyframe_insert(data_path='energy',frame=fr)
 a=data.animation_data.action;a.name=name;a.use_fake_user=True;slot=data.animation_data.action_slot;t=data.animation_data.nla_tracks.new();t.name='CLIP_LIBRARY_'+name;st=t.strips.new(name,1,a);st.action_slot=slot;t.mute=True;data.animation_data.action=None;data.energy=4
 reg['clips'].append({'name':name,'object':light.name,'target_kind':'LIGHT_DATA','channel':'energy','frames':[keys[0][0],keys[-1][0]],'seconds':(keys[-1][0]-keys[0][0])/24,'loop':loop,'default_playing':False,'export_policy':'source lighting action; runtime reconstructs intensity'})
reg['source_version']='p3-v020';reg['version']='p3-v020';(R/'manifest/object-animation-clips.json').write_text(json.dumps(reg,ensure_ascii=False,indent=2));s.frame_set(1);s['version']='p3-v020';s['phase_status']='P4_FINAL_MODEL_SELF_CHECK';t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v019','p3-v020')+'\nPortable editable Chinese labels use packed Noto Sans CJK SC, SIL OFL 1.1. License in assets/fonts/OFL.txt.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v020';m['source_version']='p3-v020';m['source_snapshot']=out.name;(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(R/'evidence/portable-source-finish.json').write_text(json.dumps({'version':'p3-v020','font':'Noto Sans CJK SC, OFL 1.1, packed','label_objects':labels,'lighting_action_object':light.name,'source_geometry_since_v019':'unchanged except text glyph contours; non-text views and 20s veranda motion use v019 renders with explicit provenance'},ensure_ascii=False,indent=2));print('PORTABLE_SOURCE_FINISHED',len(labels),light.name,flush=True)
