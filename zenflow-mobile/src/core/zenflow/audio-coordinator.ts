export type AudioActivity = 'SPEECH' | 'RECORDING' | 'PLAYBACK';
export type AudioLease = { readonly activity:AudioActivity; assertActive:()=>void; release:()=>void };
/** Exclusive foreground audio ownership. Releasing an old lease cannot release its successor. */
export class AudioCoordinator {
  private current:AudioLease|null=null;
  acquire(activity:AudioActivity):AudioLease {
    if(this.current)throw Error('AUDIO_BUSY');
    let released=false;
    const lease:AudioLease={activity,assertActive:()=>{if(released||this.current!==lease)throw Error('AUDIO_LEASE_EXPIRED');},release:()=>{
      if(released)return;released=true;if(this.current===lease)this.current=null;
    }};
    this.current=lease;return lease;
  }
}
export const audioCoordinator = new AudioCoordinator();
