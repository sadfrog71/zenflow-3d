// Recorded water, bundled locally. No synthesized sweep, microphone or runtime network.
class PondAudio {
  constructor(Context, loadSamples, schedule=(fn,ms)=>setTimeout(fn,ms), cancel=id=>clearTimeout(id)) {
    this.Context=Context;this.loadSamples=loadSamples;this.schedule=schedule;this.cancel=cancel;
    this.context=null;this.enabled=false;this.epoch=0;this.volume=.16;this.nodes=new Set();this.timers=new Set();this.lastDrop=-Infinity;this.lastFollow=-Infinity;this.preset='drop';this.buffers=null;
  }
  async enable(preset=this.preset) {
    const epoch=++this.epoch;this.preset=preset;
    if(!this.Context)throw new Error('AUDIO_UNAVAILABLE');
    if(!this.context)this.context=new this.Context();
    // Resume within the user gesture before asynchronous decoding.
    await this.context.resume();
    if(!this.buffers){
      const samples=await this.loadSamples();
      const entries=await Promise.all(Object.entries(samples).map(async([key,bytes])=>[key,await this.context.decodeAudioData(bytes.slice(0))]));
      this.buffers=Object.fromEntries(entries);
    }
    if(epoch!==this.epoch){if(!this.enabled&&this.context.state==='running')await this.context.suspend();return false;}
    if(this.context.state!=='running')throw new Error('AUDIO_SUSPENDED');
    this.enabled=true;this.master=this.context.createGain();this.master.gain.value=0;this.master.connect(this.context.destination);
    this.master.gain.setTargetAtTime(this.volume,this.context.currentTime,.2);
    this.lastDrop=-Infinity;
    this.lastFollow=-Infinity;
    if(preset==='lap')this.play('lap',.75,1,true);else{this.drop();this.nextDrop(epoch);}
    return true;
  }
  nextDrop(epoch) {
    const timer=this.schedule(()=>{this.timers.delete(timer);if(!this.enabled||epoch!==this.epoch)return;this.drop();this.nextDrop(epoch);},9000+Math.random()*6000);
    this.timers.add(timer);
  }
  play(key,volume=1,rate=1,loop=false) {
    if(!this.enabled||!this.buffers?.[key])return;
    const source=this.context.createBufferSource(),gain=this.context.createGain();source.buffer=this.buffers[key];source.playbackRate.value=rate;source.loop=loop;
    gain.gain.value=volume;source.connect(gain);gain.connect(this.master);this.nodes.add(source);
    source.onended=()=>{this.nodes.delete(source);source.disconnect();gain.disconnect();};source.start();
  }
  setVolume(value){this.volume=Math.min(.3,Math.max(0,value));if(this.enabled)this.master.gain.setTargetAtTime(this.volume,this.context.currentTime,.15);}
  drop(splash=false){
    if(!this.enabled)return;const now=this.context.currentTime;
    if(!splash&&now-this.lastDrop<1.8)return;this.lastDrop=now;
    this.play('drop',splash?.75:.65,1);
  }
  follow(amount){
    if(!this.enabled||amount<.25||!this.buffers?.lap)return;
    const now=this.context.currentTime;
    if(now-this.lastFollow<1.4)return;
    this.lastFollow=now;this.lastDrop=now;
    // A brief swell of the existing recorded water, fading at both ends.
    const source=this.context.createBufferSource(),gain=this.context.createGain();
    const duration=Math.min(1.2,this.buffers.lap.duration);
    source.buffer=this.buffers.lap;source.connect(gain);gain.connect(this.master);
    gain.gain.setValueAtTime(0,now);
    gain.gain.linearRampToValueAtTime(.15+Math.min(1,amount)*.2,now+duration*.25);
    gain.gain.linearRampToValueAtTime(0,now+duration);
    this.nodes.add(source);source.onended=()=>{this.nodes.delete(source);source.disconnect();gain.disconnect();};
    source.start(now,0,duration);
  }
  stop(){
    ++this.epoch;this.enabled=false;this.timers.forEach(timer=>this.cancel(timer));this.timers.clear();
    this.nodes.forEach(node=>{try{node.stop();node.disconnect();}catch{}});this.nodes.clear();this.master?.disconnect();this.master=null;
    if(this.context?.state==='running')void this.context.suspend().catch(()=>{});
  }
  dispose(){this.stop();if(this.context)void this.context.close().catch(()=>{});}
}
module.exports={PondAudio};
