import { AudioCoordinator } from '../audio-coordinator';
test('recognition, recording and playback cannot overlap',()=>{
 const audio=new AudioCoordinator(),speech=audio.acquire('SPEECH');
 expect(()=>audio.acquire('RECORDING')).toThrow('AUDIO_BUSY');
 expect(()=>audio.acquire('PLAYBACK')).toThrow('AUDIO_BUSY');
 speech.release();const record=audio.acquire('RECORDING');
 expect(()=>audio.acquire('SPEECH')).toThrow('AUDIO_BUSY');record.release();
 const playback=audio.acquire('PLAYBACK');playback.assertActive();playback.release();
});
test('late release and callbacks from an old owner cannot affect a new audio session',()=>{
 const audio=new AudioCoordinator(),old=audio.acquire('RECORDING');old.release();
 const next=audio.acquire('PLAYBACK');old.release();
 expect(()=>old.assertActive()).toThrow('AUDIO_LEASE_EXPIRED');next.assertActive();
 expect(()=>audio.acquire('SPEECH')).toThrow('AUDIO_BUSY');next.release();
});
