import bpy,json,hashlib,numpy as np
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def snapshot(file):
 bpy.ops.wm.open_mainfile(filepath=str(R/file));s=bpy.context.scene;bpy.context.view_layer.update();rows={};cache={};fonts={}
 for o in s.objects:
  if o.hide_render or o.hide_viewport:continue
  if o.type=='MESH':
   me=o.data
   if me.name not in cache:
    v=np.empty(len(me.vertices)*3,dtype=np.float32);me.vertices.foreach_get('co',v);l=np.empty(len(me.loops),dtype=np.int32);me.loops.foreach_get('vertex_index',l);cache[me.name]=hashlib.sha256(v.tobytes()+l.tobytes()).hexdigest()
   rows[o.name]={'geometry':cache[me.name],'matrix':[round(x,7) for row in o.matrix_world for x in row],'materials':[m.name for m in me.materials if m]}
  elif o.type=='FONT':fonts[o.name]={'body':o.data.body,'font':o.data.font.name,'filepath':o.data.font.filepath}
 cams={o.name:{'matrix':[round(x,7) for row in o.matrix_world for x in row],'lens':o.data.lens} for o in s.objects if o.type=='CAMERA'}
 return {'meshes':rows,'fonts':fonts,'cameras':cams}
a=snapshot('zen-space-p3-v019.blend');b=snapshot('zen-space-p3-v021.blend');report={}
for key in ['meshes','fonts','cameras']:
 aa,bb=a[key],b[key];report[key]={'baseline_count':len(aa),'final_count':len(bb),'added':sorted(set(bb)-set(aa)),'removed':sorted(set(aa)-set(bb)),'changed':[n for n in aa.keys()&bb.keys() if aa[n]!=bb[n]]}
report['baseline']='p3-v019';report['final']='p3-v021';report['scope']='visible mesh coordinates/topology, transforms/material assignments, editable fonts and camera transforms/lenses; day/dusk/night rendering presets applied by same render_model.py';report['all_default_visible_geometry_and_cameras_equal']=all(not x['added'] and not x['removed'] and not x['changed'] for x in [report['meshes'],report['fonts'],report['cameras']]);(R/'evidence/visible-revision-equivalence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('REVISION_EQUIVALENCE',json.dumps(report),flush=True)
