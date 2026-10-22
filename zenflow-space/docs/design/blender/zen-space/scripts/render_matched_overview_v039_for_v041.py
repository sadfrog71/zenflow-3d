"""Temporary audit camera only: render old geometry from the current overview camera; never save old source."""
import bpy,sys,runpy
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene
if s.get('version')!='p3-v039':raise RuntimeError('Expected old source')
c=s.objects['CAM_WORLD_FULL'];c.location=(8,-148,120);c.rotation_euler=(Vector((-5,9,2))-c.location).to_track_quat('-Z','Y').to_euler();c.data.ortho_scale=108;s['version']='p3-v039-matched-v041';sys.argv=[__file__,'--','day','CAM_WORLD_FULL','--gpu'];runpy.run_path(str(R/'scripts/render_model.py'),run_name='__main__')
