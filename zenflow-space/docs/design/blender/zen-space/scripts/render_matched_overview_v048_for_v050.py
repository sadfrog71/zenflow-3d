"""Open untouched v048 and render the new v050 composition camera without saving the old source."""
import bpy,sys,runpy
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;assert s.get('version')=='p3-v048';src=s.objects['CAM_WORLD_FULL'];cam=src.copy();cam.data=src.data.copy();cam.name='CAM_GARDEN_COMPOSITION';src.users_collection[0].objects.link(cam);cam.location=(8,-148,113);cam.rotation_euler=(Vector((-6,11,2))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=90;s['version']='p3-v048-matched-v050';sys.argv=[str(R/'scripts/render_model.py'),'--','day','CAM_GARDEN_COMPOSITION','--gpu'];runpy.run_path(str(R/'scripts/render_model.py'),run_name='__main__');print('MATCHED_BASELINE_NO_SOURCE_SAVE',flush=True)
