export interface DraftSink {
  saveDraft(id:string,kind:'NOTE',body:string,generation:number):Promise<unknown>;
}
/** Bounded debounce. A saved notification always corresponds to the exact committed body. */
export class DraftWriter {
  private body:string|null=null;
  private firstAt:number|null=null;
  private timer:ReturnType<typeof setTimeout>|null=null;
  private queue:Promise<void>=Promise.resolve();
  private stopped=false;
  private version=0;
  constructor(private sink:DraftSink,private id:string,private generation:number,
    private notify:(body:string,error?:unknown)=>void){}
  schedule(body:string){
    if(this.stopped)return;
    this.body=body;this.version++;const now=Date.now();if(this.firstAt===null)this.firstAt=now;
    if(this.timer)clearTimeout(this.timer);
    this.timer=setTimeout(()=>{void this.flush().catch(()=>{});},Math.min(500,Math.max(0,2000-(now-this.firstAt))));
  }
  flush():Promise<void>{
    if(this.timer)clearTimeout(this.timer);this.timer=null;this.firstAt=null;
    const body=this.body,version=this.version;this.body=null;
    if(body===null||this.stopped)return this.queue;
    const work=this.queue.catch(()=>{}).then(async()=>{
      if(this.stopped)return;
      try{await this.sink.saveDraft(this.id,'NOTE',body,this.generation);if(!this.stopped)this.notify(body);}
      catch(error){if(!this.stopped){if(version===this.version&&this.body===null)this.body=body;this.notify(body,error);}throw error;}
    });
    this.queue=work;return work;
  }
  acknowledge(body:string){if(this.body===body){this.body=null;if(this.timer)clearTimeout(this.timer);this.timer=null;this.firstAt=null;}}
  cancel(){this.stopped=true;if(this.timer)clearTimeout(this.timer);this.timer=null;this.body=null;}
}
