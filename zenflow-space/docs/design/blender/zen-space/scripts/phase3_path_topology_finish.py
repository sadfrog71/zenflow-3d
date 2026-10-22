import bpy,bmesh,json
from pathlib import Path
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v017.blend'
if s.get('version')!='p3-v016' or out.exists():raise RuntimeError('Requires new v017 from v016')
o=bpy.data.objects['PUBLIC_EAST_ROUTE_RESOLVED_NETWORK'];bm=bmesh.new();bm.from_mesh(o.data)
def report():return {'vertices':len(bm.verts),'faces':len(bm.faces),'degenerate_faces_below_1e10_m2':sum(f.calc_area()<1e-10 for f in bm.faces),'non_manifold_edges':sum(not e.is_manifold for e in bm.edges),'volume_m3':bm.calc_volume(signed=False)}
a=report();bmesh.ops.dissolve_degenerate(bm,dist=1e-5,edges=list(bm.edges));b=report()
if b['non_manifold_edges'] or b['degenerate_faces_below_1e10_m2'] or abs(a['volume_m3']-b['volume_m3'])>.0001:raise RuntimeError('Topology repair exceeds bounds')
bm.to_mesh(o.data);bm.free();o.data.update();s['version']='p3-v017';s['phase_status']='P4_FINAL_SELF_CHECK';t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v016','p3-v017'))
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
for fn in ['scene-manifest.json','object-animation-clips.json','weather-and-layout-states.json']:
 p=R/'manifest'/fn;m=json.loads(p.read_text());m['source_version']='p3-v017'
 if fn=='scene-manifest.json':m['version']='p3-v017';m['source_snapshot']=out.name
 p.write_text(json.dumps(m,ensure_ascii=False,indent=2))
(R/'evidence/path-topology-finish.json').write_text(json.dumps({'version':'p3-v017','before':a,'after':b,'dissolve_distance_m':1e-5,'volume_change_m3':b['volume_m3']-a['volume_m3'],'visual_geometry_change':'microscopic coincident edge cleanup only; route extents unchanged'},indent=2));print('PATH_TOPOLOGY_FINISHED',a,b,flush=True)
