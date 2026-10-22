"""Repair measured 24m core/outer seam gap and make baked far-tree shading behave as an impostor."""
import bpy,json,math
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v026.blend'
if s.get('version')!='p3-v025' or out.exists():raise RuntimeError('Requires new v026 from v025')
core=s.objects['TERRAIN_CONTINUOUS_BASE'];outer=s.objects['SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE'];N=201;pts=[core.matrix_world@v.co for v in core.data.vertices];edge=[pts[i] for i in range(N)]+[pts[j*N+N-1] for j in range(1,N)]+[pts[(N-1)*N+i] for i in range(N-2,-1,-1)]+[pts[j*N] for j in range(N-2,0,-1)];L=len(edge);deltas=[edge[i].z-outer.data.vertices[i].co.z for i in range(L)];before=max(abs(x) for x in deltas)
def smooth(t):t=max(0,min(1,t));return t*t*(3-2*t)
for i,v in enumerate(outer.data.vertices):
 ring=i//L;dist=680*(ring/48)**1.6;v.co.z+=deltas[i%L]*(1-smooth(dist/100))
outer.data.update();after=max((outer.matrix_world@outer.data.vertices[i].co-edge[i]).length for i in range(L))
if after>.00001:raise RuntimeError('Outer seam not continuous')
# Baked distant sprites already contain illumination. Relighting their planes produced dark rectangular artifacts.
for m in bpy.data.materials:
 if not m.name.startswith('P4_FAR_FOREST_SPRITE_'):continue
 nt=m.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');tex=next(n for n in nt.nodes if n.type=='TEX_IMAGE');output=next(n for n in nt.nodes if n.type=='OUTPUT_MATERIAL');clip=nt.nodes.new('ShaderNodeMath');clip.operation='GREATER_THAN';clip.inputs[1].default_value=.35;nt.links.new(tex.outputs['Alpha'],clip.inputs[0]);nt.links.new(clip.outputs[0],p.inputs['Alpha']);em=nt.nodes.new('ShaderNodeEmission');em.name='FAR_FOREST_EMISSION';em.inputs['Strength'].default_value=.85;nt.links.new(tex.outputs['Color'],em.inputs['Color']);transparent=nt.nodes.new('ShaderNodeBsdfTransparent');mix=nt.nodes.new('ShaderNodeMixShader');nt.links.new(clip.outputs[0],mix.inputs[0]);nt.links.new(transparent.outputs[0],mix.inputs[1]);nt.links.new(em.outputs[0],mix.inputs[2]);nt.links.new(mix.outputs[0],output.inputs['Surface']);nt.links.new(tex.outputs['Color'],p.inputs['Emission Color']);p.inputs['Emission Strength'].default_value=.7;m['exchange_use_principled_fallback']=True;m['exchange_far_impostor']=True
for ob in s.objects:
 if ob.name.startswith('SM_FAR_FOREST_IMPOSTORS_'):ob.visible_shadow=False;ob['shadow_policy']='far-only baked sprite; no incorrect planar shadow cast into functional site'
s.cycles.transparent_max_bounces=32;s['version']='p3-v026';s['phase_status']='P4_COMPLETE_MODEL_REFERENCE_CHECK';s.frame_set(1);t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v025','p3-v026')+'\nCore/outer terrain boundary welded in position; baked far forest is weather-tinted unlit/alpha-masked context with no near-field shadow.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v026';m['source_version']='p3-v026';m['source_snapshot']=out.name;(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(R/'evidence/terrain-seam-and-impostor-repair.json').write_text(json.dumps({'version':'p3-v026','boundary_vertices':L,'maximum_boundary_gap_before_m':before,'maximum_boundary_gap_after_m':after,'continuation_blend_distance_m':100,'far_impostor':'source-tree bake used unlit with alpha mask .35; weather adjusts emission; no planar shadows into near field','transparent_ray_limit':32,'functional_roads_buildings_moved':False},indent=2));print('TERRAIN_SEAM_FINISHED',before,after,flush=True)
