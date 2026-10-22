import subprocess,json,sys,time
from pathlib import Path
R=Path(__file__).resolve().parents[1];B='/Applications/Blender.app/Contents/MacOS/Blender';source='zen-space-p3-v019.blend';m=json.loads((R/'manifest/scene-manifest.json').read_text());names=[c['name'] for c in m['cameras']];core=['CAM_ENTRY_SEATED','CAM_LIBRARY_SEATED','CAM_COPY_VIEW','CAM_ISLAND_SEATED'];jobs=[('day',['day',*names,'--gpu'])]+[(state,[state,*core,'--gpu']) for state in ['dusk','night','rain']]+[('off',['off','CAM_COPY_VIEW','--gpu']),('dawn',['dawn','CAM_LOOKOUT_E','--gpu']),('sunset',['sunset','CAM_LOOKOUT_W','--gpu'])]
for tag,args in jobs:
 print('QUEUE_BEGIN',tag,flush=True)
 with (R/'evidence'/f'render-p3-v019-{tag}-final.log').open('w') as f:r=subprocess.run([B,'-b',source,'-t','6','-P','scripts/render_model.py','--',*args],cwd=R,stdout=f,stderr=subprocess.STDOUT)
 if r.returncode:raise RuntimeError('Render failed '+tag)
 print('QUEUE_DONE',tag,flush=True)
for seconds in [20,60]:
 print('QUEUE_MOTION_BEGIN',seconds,flush=True)
 with (R/'evidence'/f'motion-p3-v019-{seconds}s.log').open('w') as f:r=subprocess.run([B,'-b',source,'-t','6','-P','scripts/render_motion_preview.py','--',str(seconds)],cwd=R,stdout=f,stderr=subprocess.STDOUT)
 if r.returncode:raise RuntimeError('Motion render failed '+str(seconds))
 frames=R/'previews/p3-v019/motion'/f'{seconds}s-frames';video=frames.parent/f'zen-space-{seconds}s.mp4'
 r=subprocess.run(['/opt/homebrew/bin/ffmpeg','-y','-hide_banner','-loglevel','error','-framerate','12','-i',str(frames/'%05d.png'),'-c:v','libx264','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)])
 if r.returncode:raise RuntimeError('Video encode failed')
 print('QUEUE_MOTION_DONE',seconds,str(video),flush=True)
print('FINAL_RENDER_QUEUE_DONE',flush=True)
