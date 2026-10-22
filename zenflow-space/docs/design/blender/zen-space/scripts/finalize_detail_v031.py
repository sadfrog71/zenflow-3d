"""Final detail pass: lamp paper transmits real light, new casting rings follow bell motion, diagnostic view clears terrain."""
import bpy,json
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v031.blend'
if s.get('version')!='p3-v030' or out.exists():raise RuntimeError('Requires v030 and unused v031 output')
m=bpy.data.materials['P5_WASHI_FIBRE_LIGHT'];nt=m.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');output=next(n for n in nt.nodes if n.type=='OUTPUT_MATERIAL');img=next(n for n in nt.nodes if n.type=='TEX_IMAGE' and n.image and n.image.name.endswith('washi-base.png'));trans=nt.nodes.new('ShaderNodeBsdfTranslucent');nt.links.new(img.outputs['Color'],trans.inputs['Color']);mix=nt.nodes.new('ShaderNodeMixShader');mix.inputs[0].default_value=.60;nt.links.new(p.outputs['BSDF'],mix.inputs[1]);nt.links.new(trans.outputs[0],mix.inputs[2]);nt.links.new(mix.outputs[0],output.inputs['Surface']);m['exchange_use_principled_fallback']=True
parent=s.objects['PIVOT_Z07_BELL'];names=[]
for o in s.objects:
 if o.name.startswith('SM_P5_BELL_FINE_CASTING_RING'):
  mw=o.matrix_world.copy();o.parent=parent;o.matrix_world=mw;names.append(o.name)
cam=s.objects['CAM_ISLAND_SECTION'];old={'position':list(cam.location),'rotation':list(cam.rotation_euler),'ortho_scale':cam.data.ortho_scale};cam.location=(-36,-12,12);target=Vector((-27,5,-.7));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=18
s['version']='p3-v031';s['phase_status']='P5_DETAIL_DELIVERY_CANDIDATE';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/detail-refinement-v031.json').write_text(json.dumps({'version':'p3-v031','base':'p3-v030','lamp':'fibre texture plus physical thin-paper transmission; runtime uses documented Principled fallback','new_bell_rings_parented':names,'diagnostic_camera_only':{'name':cam.name,'before':old,'after':{'position':list(cam.location),'target':list(target),'ortho_scale':18},'reason':'old low orthographic camera originated below terrain at image corners, producing black interior surfaces; lift camera without changing world'},'all_first_person_and_comparison_cameras':'unchanged'},indent=2));print('DETAIL_CANDIDATE_V031_SAVED',flush=True)
