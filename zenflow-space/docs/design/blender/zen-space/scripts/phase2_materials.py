"""Shared export-safe materials and primitives; original textures, no external assets."""
import bpy, math
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
def material(name,color,texture=None,rough=.65,metal=0,alpha=False,emission=0):
 m=bpy.data.materials.get(name) or bpy.data.materials.new(name);m.use_nodes=True
 m.diffuse_color=(*color,1);nt=m.node_tree;nt.nodes.clear();p=nt.nodes.new('ShaderNodeBsdfPrincipled');out=nt.nodes.new('ShaderNodeOutputMaterial');nt.links.new(p.outputs['BSDF'],out.inputs['Surface'])
 p.inputs['Base Color'].default_value=(*color,1);p.inputs['Roughness'].default_value=rough;p.inputs['Metallic'].default_value=metal
 if texture:
  tx=nt.nodes.new('ShaderNodeTexImage');tx.image=bpy.data.images.load(str(ROOT/'assets/textures'/texture),check_existing=True);tx.image.pack();nt.links.new(tx.outputs['Color'],p.inputs['Base Color'])
  if alpha:
   nt.links.new(tx.outputs['Alpha'],p.inputs['Alpha']);m.surface_render_method='DITHERED';m.use_transparent_shadow=True;m.use_backface_culling=False
 if emission:
  p.inputs['Emission Color'].default_value=(*color,1);p.inputs['Emission Strength'].default_value=emission
 return m
def palette():
 return {k:material('P2_'+k,*v) for k,v in {
 'Cedar':((.42,.23,.10),'cedar.png',.47),'Darkwood':((.14,.075,.035),'darkwood.png',.55),
 'Stone':((.42,.45,.40),'stone.png',.82),'Paper':((.78,.72,.59),'paper.png',.9),
 'Leaf':((.18,.30,.08),'leaf-alpha.png',.75,0,True),'Roof':((.10,.135,.15),None,.76),
 'Plaster':((.78,.75,.66),None,.88),'Soil':((.20,.24,.14),None,.95),
 'Water':((.055,.16,.14),None,.12,.20),'Ink':((.015,.022,.019),None,.3),
 'Linen':((.42,.36,.22),'paper.png',.98),'Blossom':((.43,.065,.05),None,.7),
 'Glow':((1,.52,.18),None,.5,0,False,2.0)}.items()}
def assign(o,m):
 if o.type=='MESH':
  o.data=o.data.copy() if o.data.users>1 else o.data
  o.data.materials.clear();o.data.materials.append(m)
  if not o.data.uv_layers:o.data.uv_layers.new(name='UVMap')
  uv=o.data.uv_layers.active.data
  for f in o.data.polygons:
   axis=max(range(3),key=lambda i:abs(f.normal[i]));dims=[i for i in range(3) if i!=axis]
   for li in f.loop_indices:
    v=o.data.vertices[o.data.loops[li].vertex_index].co;uv[li].uv=(v[dims[0]]*.5,v[dims[1]]*.5)
 elif hasattr(o.data,'materials'):
  o.data.materials.clear();o.data.materials.append(m)
 return o
def link(o,c):
 for cc in list(o.users_collection):cc.objects.unlink(o)
 c.objects.link(o);return o
def box(n,p,d,c,m,bevel=.0):
 x,y,z=[v/2 for v in d];verts=[(-x,-y,-z),(x,-y,-z),(x,y,-z),(-x,y,-z),(-x,-y,z),(x,-y,z),(x,y,z),(-x,y,z)]
 me=bpy.data.meshes.new(n+'_mesh');me.from_pydata(verts,[],[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]);me.update();o=bpy.data.objects.new(n,me);c.objects.link(o);o.location=p;assign(o,m)
 if bevel:b=o.modifiers.new('Soft_edges','BEVEL');b.width=bevel;b.segments=2
 return o
def beam(n,a,b,r,c,m,vertices=12):
 a,b=Vector(a),Vector(b);h=(b-a).length/2;vs=[]
 for z in [-h,h]:
  for i in range(vertices):vs.append((r*math.cos(i*math.tau/vertices),r*math.sin(i*math.tau/vertices),z))
 fs=[tuple(reversed(range(vertices))),tuple(range(vertices,2*vertices))]
 for i in range(vertices):j=(i+1)%vertices;fs.append((i,j,j+vertices,i+vertices))
 me=bpy.data.meshes.new(n+'_mesh');me.from_pydata(vs,[],fs);me.update();o=bpy.data.objects.new(n,me);c.objects.link(o);o.location=(a+b)/2;o.rotation_euler=(b-a).to_track_quat('Z','Y').to_euler();assign(o,m)
 for f in o.data.polygons:f.use_smooth=len(f.vertices)==4
 return o
def camera(n,p,t,c,lens=36):
 d=bpy.data.cameras.new(n);o=bpy.data.objects.new(n,d);c.objects.link(o);o.location=p;o.rotation_euler=(Vector(t)-o.location).to_track_quat('-Z','Y').to_euler();d.lens=lens;d.clip_start=.025;d.clip_end=600;return o
def light(n,p,energy,color,c,size=1,kind='AREA',target=None):
 d=bpy.data.lights.new(n,kind);o=bpy.data.objects.new(n,d);c.objects.link(o);o.location=p;d.energy=energy;d.color=color
 if kind=='AREA':d.shape='DISK';d.size=size
 elif kind=='POINT':d.shadow_soft_size=size
 if target:o.rotation_euler=(Vector(target)-o.location).to_track_quat('-Z','Y').to_euler()
 return o
def setup(s):
 s.render.engine='CYCLES';s.cycles.samples=32;s.cycles.use_denoising=True;s.cycles.max_bounces=5;s.cycles.transparent_max_bounces=6
 s.render.resolution_x=1600;s.render.resolution_y=900;s.render.resolution_percentage=100
 s.view_settings.view_transform='AgX';s.view_settings.look='AgX - Medium High Contrast';s.view_settings.exposure=.6
 s.world.use_nodes=True;s.world.node_tree.nodes.get('Background').inputs['Color'].default_value=(.58,.72,.84,1);s.world.node_tree.nodes.get('Background').inputs['Strength'].default_value=.45
 s.render.image_settings.file_format='PNG'
