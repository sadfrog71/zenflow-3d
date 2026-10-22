"""Area-preserving leaf-card LOD from original CC0 leaf surfaces.
Collapse decimation produced nearly zero-area triangles: deliberately replaced, not accepted.
"""
import bpy,bmesh,numpy as np,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/polyhaven/tree_small_02/tree-leaf-cards.blend'
if OUT.exists():raise RuntimeError('Output exists')
o=bpy.data.objects['CC0_TREE_leaves_FULL'];bm=bmesh.new();bm.from_mesh(o.data);bm.faces.ensure_lookup_table();uvlayer=bm.loops.layers.uv.active;seen=set();records=[];centers=set()
for face in bm.faces:
 if face in seen:continue
 stack=[face];seen.add(face);faces=[]
 while stack:
  f=stack.pop();faces.append(f)
  for e in f.edges:
   for q in e.link_faces:
    if q not in seen:seen.add(q);stack.append(q)
 verts={v for f in faces for v in f.verts}
 if len(verts)<3:continue
 vs=list(verts);pos=np.array([tuple(v.co) for v in vs],dtype=float);center=pos.mean(0);key=tuple(np.round(center/0.002).astype(int))
 if key in centers:continue
 centers.add(key);p=pos-center;cov=p.T@p;vals,axes=np.linalg.eigh(cov);u,v=axes[:,-1],axes[:,-2];qx=p@u;qy=p@v
 if qx.max()-qx.min()<.001 or qy.max()-qy.min()<.001:continue
 vertuv={}
 for f in faces:
  for l in f.loops:
   if l.vert not in vertuv:vertuv[l.vert]=tuple(l[uvlayer].uv)
 uv=np.array([vertuv.get(v,(0,0)) for v in vs]);A=np.stack([qx,qy,np.ones(len(vs))],axis=1);coeff=np.linalg.lstsq(A,uv,rcond=None)[0];corners=[(qx.min(),qy.min()),(qx.max(),qy.min()),(qx.max(),qy.max()),(qx.min(),qy.max())];xyz=[center+u*x+v*y for x,y in corners];tex=[np.array([x,y,1])@coeff for x,y in corners]
 records.append((xyz,tex))
 if len(records)%4000==0:print('LEAF_CARDS_ANALYZED',len(records),flush=True)
bm.free();print('AREA_PRESERVED_CARDS',len(records),flush=True)
mat=bpy.data.materials['tree_small_02_leaves'];mat=mat.copy();mat.name='CC0_TREE_LEAF_CARDS';mat.use_nodes=True;nt=mat.node_tree;bs=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');tx=next(n for n in nt.nodes if n.type=='TEX_IMAGE' and 'leaves_diff' in n.image.name);im=bpy.data.images.load(str(ROOT/'assets/polyhaven/tree_small_02/tree_small_02_leaves_diff_1k.png'),check_existing=True);im.pack();tx.image=im;nt.links.new(tx.outputs['Alpha'],bs.inputs['Alpha']);mat.surface_render_method='DITHERED';mat.use_backface_culling=False
bs.inputs['Roughness'].default_value=.8
rows=[]
for lod,step in [('NEAR',1),('MID',2),('FAR',4)]:
 vv=[];ff=[];tt=[]
 for i,(xyz,uv) in enumerate(records):
  if i%step:continue
  n=len(vv);vv.extend(xyz);tt.extend(uv);ff.append((n,n+1,n+2,n+3))
 me=bpy.data.meshes.new('CC0_LEAF_CARDS_'+lod);me.from_pydata(vv,[],ff);me.update();me.materials.append(mat);uv=me.uv_layers.new()
 for f in me.polygons:
  for li in f.loop_indices:uv.data[li].uv=tt[me.loops[li].vertex_index]
 ob=bpy.data.objects.new('CC0_LEAF_CARDS_'+lod,me);bpy.context.scene.collection.objects.link(ob);ob['source']='https://polyhaven.com/a/tree_small_02';ob['license']='CC0';ob.hide_render=lod!='NEAR';rows.append({'lod':lod,'cards':len(ff),'triangles':len(ff)*2,'vertices':len(vv),'area_m2':sum(f.area for f in me.polygons)})
for ob in bpy.context.scene.objects:
 if not ob.name.startswith('CC0_LEAF_CARDS_'):ob.hide_render=True;ob.hide_viewport=True
bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True);(ROOT/'evidence/tree-leaf-card-derivation.json').write_text(json.dumps({'original_source':'tree_small_02 leaves FULL','method':'connected original leaf surfaces -> PCA plane, projected extent corners, least-squares original UV mapping; preserve area and orientation; complete leaf sampling for distance','license':'CC0','author':'Rico Cilliers','parts':rows},indent=2));print('TREE_LEAF_CARD_LODS_SAVED',OUT,flush=True)
