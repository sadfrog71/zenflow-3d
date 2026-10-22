import bpy,sys,math,json
from pathlib import Path
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import beam
s=bpy.context.scene;out=R/'zen-space-p3-v019.blend'
if s.get('version')!='p3-v018' or out.exists():raise RuntimeError('Requires new v019 from v018')
for ob in s.objects:
 if ob.name.startswith('SM_Z11_LAMP_RIB'):ob.hide_render=True;ob.hide_viewport=True
body=s.objects['SM_Z11_PAPER_LAMP'];C=bpy.data.collections['P3_PROPS_AND_ARCH_DETAILS'];x,y=body.location.x,body.location.y;mat=bpy.data.materials['P2_Cedar']
for k in range(12):
 a=k*math.tau/12;pts=[]
 for rr,z in [(.131,1.05),(.151,1.115),(.163,1.18),(.151,1.245),(.131,1.31)]:pts.append((x+math.cos(a)*rr,y+math.sin(a)*rr,z))
 for p,q in zip(pts,pts[1:]):beam('SM_Z11_LAMP_CURVED_BAMBOO_RIB',p,q,.002,C,mat,6)
for f in body.data.polygons:f.use_smooth=True
s['version']='p3-v019';s['phase_status']='P4_RENDERED_REFERENCE_REVIEW';s.frame_set(1);t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v018','p3-v019'))
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v019';m['source_version']='p3-v019';m['source_snapshot']=out.name;(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(R/'evidence/lamp-rib-repair.json').write_text(json.dumps({'version':'p3-v019','diagnosis':'straight ribs intersected the convex paper shade, leaving visible dark stubs','repair':'12 continuous ribs follow the actual curved shade profile, radius +3mm and 2mm rib thickness'},indent=2));print('LAMP_RIB_FINISHED',flush=True)
