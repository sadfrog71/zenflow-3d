import subprocess,json,time
from pathlib import Path
R=Path(__file__).resolve().parents[1];B='/Applications/Blender.app/Contents/MacOS/Blender';source='zen-space-p3-v028.blend';version='p3-v028'
for seconds in [20,60]:
 print('FINAL_MOTION_BEGIN',seconds,flush=True)
 with (R/'evidence'/f'motion-{version}-{seconds}s.log').open('w') as f:r=subprocess.run([B,'-b',source,'-t','6','-P','scripts/render_motion_preview.py','--',str(seconds),'--light'],cwd=R,stdout=f,stderr=subprocess.STDOUT)
 if r.returncode:raise RuntimeError('Source animation render failed')
 frames=R/'previews'/version/'motion'/f'{seconds}s-frames';video=frames.parent/f'zen-space-{seconds}s.mp4'
 r=subprocess.run(['/opt/homebrew/bin/ffmpeg','-y','-hide_banner','-loglevel','error','-framerate','12','-start_number','1','-i',str(frames/'%05d.png'),'-c:v','libx264','-crf','19','-pix_fmt','yuv420p','-movflags','+faststart',str(video)])
 if r.returncode:raise RuntimeError('Encoding failed')
 print('FINAL_MOTION_DONE',seconds,str(video),flush=True)
print('FINAL_MOTION_QUEUE_DONE',flush=True)
