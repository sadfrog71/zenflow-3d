import bpy,math,json
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v015.blend'
if s.get('version')!='p3-v014' or out.exists():raise RuntimeError('Requires new v015 from v014')
oldmat=bpy.data.materials['P2_Leaf'];mat=oldmat.copy();mat.name='P3_CURVED_SHRUB_LEAF';nt=mat.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED')
for link in list(p.inputs['Alpha'].links):nt.links.remove(link)
p.inputs['Alpha'].default_value=1;p.inputs['Roughness'].default_value=.8
meshes={};changed=[]
for ob in list(s.objects):
 if ob.type!='MESH' or ob.hide_render or len(ob.data.materials)!=1 or ob.data.materials[0]!=oldmat:continue
 old=ob.data
 if any(len(f.vertices)!=4 for f in old.polygons):continue
 if old.name not in meshes:
  vs=[];fs=[];uvs=[]
  for face in old.polygons:
   a,b,c,d=[old.vertices[i].co for i in face.vertices];center=(a+b+c+d)/4;u=(b-a)/2;v=(d-a)/2;normal=u.cross(v).normalized();start=len(vs)
   for j in range(7):
    t=j/6;width=max(.025,math.sin(math.pi*t))
    for k in [-1,0,1]:
     pp=center+v*(2*t-1)+u*(width*k)+normal*(.006*math.sin(math.pi*t)*(1-abs(k)))
     vs.append(pp);uvs.append((.25+.40*t,.32+.30*(k+1)/2))
   for j in range(6):
    for k in range(2):a=start+j*3+k;fs.append((a,a+1,a+4,a+3))
  me=bpy.data.meshes.new('CURVED_'+old.name);me.from_pydata(vs,[],fs);me.update();me.materials.append(mat);uv=me.uv_layers.new()
  for face in me.polygons:
   face.use_smooth=True
   for li in face.loop_indices:uv.data[li].uv=uvs[me.loops[li].vertex_index]
  meshes[old.name]=me
 ob.data=meshes[old.name];changed.append(ob.name)
# State definitions are model-source values, separate from application scheduling.
weather={'day':{'sun_energy':1.8,'world_strength':.5,'exposure':.55},'dusk':{'sun_energy':.8,'world_strength':.22,'exposure':.7},'night':{'sun_energy':.025,'world_strength':.1,'exposure':1.1},'rain':{'sun_energy':.2,'world_strength':.45,'exposure':.8,'stone_roughness':.28,'outdoor_cushion_darkening':.82},'reduced_motion':{'enabled_clips':[],'waterfall_flow_speed':0,'paper_and_tags_stay_readable':True},'lamp_off':{'lamp_emission':0,'ambient_window_and_sky_kept':True}}
for name in ['P2_Stone','P3_Weathered_Gray_Rock']:
 bpy.data.materials[name]['rain_roughness']=.28
s.objects['Z03_ONLY_ROUND_CUSHION']['rain_darkening']=.82
s['version']='p3-v015';s['phase_status']='P3_FINAL_CANDIDATE_FULL_AUDIT';s['default_motion']='independent clips inactive; runtime chooses optional ambient states';s['weather_states']=json.dumps(weather)
readme=bpy.data.texts.get('READ_ME_FIRST') or bpy.data.texts.new('READ_ME_FIRST');readme.clear();readme.write('Zenflow Space — editable full-world candidate p3-v015\n\nOne shared geometry for full roofs, cutaway and eye-level views.\nDefault: blank paper, no private content, inactive independent action library.\nCollections preserve retired procedural sources as hidden objects.\nUse WORLD_FULL / LAYOUT_CUTAWAY / EYE_LEVEL view layers.\nCurrent structural and reference comparisons are in evidence/ and previews/.\nModel production and self-check are separate from user aesthetic acceptance.\nSource Cycles materials include effects which need runtime reconstruction (water, wetness, lighting).\nNo mobile app implementation or device performance claim.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'manifest/weather-and-layout-states.json').write_text(json.dumps({'version':'p3-v015','weather':weather,'layout_states':{'Z01':['CUP','CUP_REMOVED','HOLLOW_BOWL'],'Z05_PAPER':['BLANK','TWO_CHARACTERS','CORRECTED','USER_SAMPLE','WET_INK','DRY_INK']},'private_content':'none; authored samples explicitly marked','default_motion':'none'},ensure_ascii=False,indent=2));(R/'evidence/curved-shrub-leaves.json').write_text(json.dumps({'version':'p3-v015','shared_meshes':len(meshes),'objects':len(changed),'construction':'7 cross sections, curved midrib and tapered blade; opaque leaf geometry replaces rectangular alpha cards in close shrubs'},indent=2));print('LEAF_SURFACES_FINISHED',len(changed),flush=True)
