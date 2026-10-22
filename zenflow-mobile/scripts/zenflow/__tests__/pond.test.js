/** @jest-environment node */
const { createPondClock } = require('../pond-state');
const { PondAudio } = require('../pond-audio');
function advance(clock, start, seconds) {
  const events=[]; clock.step(start,true);
  for(let i=1;i<=seconds*20;i++){const event=clock.step(start+i*50,true);if(event)events.push(event);}
  return events;
}
test('first splash only after 60 active seconds; all three surprises occur once',()=>{
  const clock=createPondClock();expect(advance(clock,0,59)).toEqual([]);
  expect(advance(clock,59000,2)).toEqual(['splash']);
  expect(advance(clock,61000,260)).toEqual(['together','leaf']);
  expect(advance(clock,321000,60)).toEqual([]);
});
test('pause/background does not count time or queue a burst on return',()=>{
  const clock=createPondClock();advance(clock,0,30);clock.step(30000,false);clock.step(900000,false);
  expect(clock.step(900001,true)).toBeNull();expect(clock.elapsed).toBeCloseTo(30);
  expect(advance(clock,900001,31)).toEqual(['splash']);
});
test('one-minute surprise remains one minute on a low-frame-rate device',()=>{
  const clock=createPondClock();clock.step(0,true);
  for(let second=1;second<60;second++)expect(clock.step(second*1000,true)).toBeNull();
  expect(clock.step(60000,true)).toBe('splash');expect(clock.elapsed).toBe(60);
});
test('a long foreground stall never queues a burst of missed surprises',()=>{
  const clock=createPondClock();clock.step(0,true);expect(clock.step(900000,true)).toBe('leaf');expect(clock.step(900050,true)).toBeNull();
});
function fakeContext(deferred=false){
  const sources=[];let resolve;
  const param=()=>({value:0,setValueAtTime:jest.fn(),setTargetAtTime:jest.fn(),linearRampToValueAtTime:jest.fn(),exponentialRampToValueAtTime:jest.fn()});
  const node=()=>({connect:jest.fn(),disconnect:jest.fn(),start:jest.fn(),stop:jest.fn(),gain:param(),playbackRate:param(),frequency:param(),Q:param()});
  const ctx={state:'suspended',currentTime:0,sampleRate:100,destination:{},
    resume:jest.fn(()=>deferred?new Promise(r=>{resolve=()=>{ctx.state='running';r();};}):(ctx.state='running',Promise.resolve())),
    suspend:jest.fn(()=>{ctx.state='suspended';return Promise.resolve();}),close:jest.fn(()=>Promise.resolve()),
    decodeAudioData:jest.fn(async()=>({duration:2})),createGain:jest.fn(node),createBiquadFilter:jest.fn(node),createOscillator:jest.fn(()=>{const n=node();sources.push(n);return n;}),
    createBuffer:jest.fn((channels,length)=>({getChannelData:()=>new Float32Array(length)})),createBufferSource:jest.fn(()=>{const n=node();sources.push(n);return n;}),
  };
  return {ctx,sources,resolve:()=>resolve()};
}
const samples=async()=>({drop:new ArrayBuffer(8),lap:new ArrayBuffer(8)});
const schedule=()=>({add:jest.fn(()=>1),cancel:jest.fn()});
test('no audio before consent, volume capped, stopping terminates every source',async()=>{
  const f=fakeContext();const timer=schedule();const a=new PondAudio(function(){return f.ctx;},samples,timer.add,timer.cancel);a.drop();expect(f.sources).toHaveLength(0);
  await a.enable();a.setVolume(2);expect(a.volume).toBe(.3);a.drop(true);expect(f.sources).toHaveLength(2);
  a.stop();for(const source of f.sources)expect(source.stop).toHaveBeenCalled();expect(a.enabled).toBe(false);expect(f.ctx.suspend).toHaveBeenCalled();expect(timer.cancel).toHaveBeenCalledWith(1);
  const count=f.sources.length;a.drop(true);expect(f.sources).toHaveLength(count);
});
test('background during asynchronous audio activation cannot start a late source',async()=>{
  const f=fakeContext(true),a=new PondAudio(function(){return f.ctx;},samples);const pending=a.enable();a.stop();f.resolve();
  expect(await pending).toBe(false);expect(f.sources).toHaveLength(0);expect(a.enabled).toBe(false);expect(f.ctx.state).toBe('suspended');
});
test('unsupported audio is explicit and leaves the scene silent',async()=>{
  const a=new PondAudio(undefined);await expect(a.enable()).rejects.toThrow('AUDIO_UNAVAILABLE');expect(a.enabled).toBe(false);
});

test('late decoding cannot play after leaving and the next activation reuses decoded samples',async()=>{
  const f=fakeContext(),timer=schedule();let release;
  const delayed=jest.fn(()=>new Promise(resolve=>{release=()=>resolve({drop:new ArrayBuffer(8),lap:new ArrayBuffer(8)});}));
  const a=new PondAudio(function(){return f.ctx;},delayed,timer.add,timer.cancel);
  const pending=a.enable();await Promise.resolve();a.stop();release();
  expect(await pending).toBe(false);expect(f.sources).toHaveLength(0);
  await a.enable('lap');expect(delayed).toHaveBeenCalledTimes(1);expect(f.sources).toHaveLength(1);
  expect(f.sources[0].loop).toBe(true);expect(timer.add).not.toHaveBeenCalled();a.dispose();
});
test('a canceled recurring drop callback cannot restart sound',async()=>{
  const f=fakeContext(),timer=schedule(),a=new PondAudio(function(){return f.ctx;},samples,timer.add,timer.cancel);
  await a.enable();const callback=timer.add.mock.calls[0][0];a.stop();callback();
  expect(f.sources).toHaveLength(1);expect(timer.add).toHaveBeenCalledTimes(1);
});

const {matchTrail}=require('../pond-follow');
test('following accepts a nearby forward gesture but not reverse or distant movement',()=>{
  const trail=[{x:0,z:0},{x:1,z:0},{x:2,z:0}];
  expect(matchTrail({x:1.1,z:.12},{x:.9,z:.12},trail)).toBeGreaterThan(.7);
  expect(matchTrail({x:.9,z:0},{x:1.1,z:0},trail)).toBe(0);
  expect(matchTrail({x:1.1,z:1},{x:.9,z:1},trail)).toBe(0);
  expect(matchTrail({x:1,z:0},{x:1,z:0},trail)).toBe(0);
});
test('curved and repeated trail samples stay finite and accept the local direction',()=>{
  const trail=[{x:0,z:0},{x:0,z:0},{x:1,z:0},{x:1,z:1}];
  expect(matchTrail({x:1,z:.6},{x:1,z:.4},trail)).toBeCloseTo(1);
  expect(matchTrail({x:0,z:0},null,trail)).toBe(0);
  expect(matchTrail({x:0,z:0},{x:-1,z:0},[])).toBe(0);
});

const {createPointerGesture}=require('../pond-follow');
const pointerEvent=(type,pointerId=1,clientX=10,clientY=10)=>({type,pointerId,clientX,clientY,isPrimary:true,button:0});
test('a quick swipe is retained even before the next visual frame; a tap remains a selection',()=>{
  const gesture=createPointerGesture();
  expect(gesture.begin(pointerEvent('pointerdown'))).toBe(true);
  expect(gesture.move(pointerEvent('pointermove',1,30,12))).toBe(true);
  expect(gesture.end(pointerEvent('pointerup'))).toEqual({moved:true,cancelled:false});
  gesture.begin(pointerEvent('pointerdown'));
  gesture.move(pointerEvent('pointermove',1,12,11));
  expect(gesture.end(pointerEvent('pointerup'))).toEqual({moved:false,cancelled:false});
});
test('another finger cannot steal or terminate the active gesture',()=>{
  const gesture=createPointerGesture();gesture.begin(pointerEvent('pointerdown'));
  expect(gesture.begin({...pointerEvent('pointerdown',2),isPrimary:false})).toBe(false);
  expect(gesture.move(pointerEvent('pointermove',2,40,40))).toBe(false);
  expect(gesture.end(pointerEvent('pointerup',2))).toBeNull();expect(gesture.id).toBe(1);
  expect(gesture.end(pointerEvent('pointerup'))).toEqual({moved:false,cancelled:false});
});
test('cancel, lost capture and background invalidate the gesture; late events cannot resurrect it',()=>{
  const gesture=createPointerGesture();
  gesture.begin(pointerEvent('pointerdown'));
  expect(gesture.end(pointerEvent('lostpointercapture'))?.cancelled).toBe(true);
  gesture.begin(pointerEvent('pointerdown'));
  expect(gesture.end(pointerEvent('pointercancel'))?.cancelled).toBe(true);
  gesture.begin(pointerEvent('pointerdown'));expect(gesture.cancel()).toBe(1);
  expect(gesture.move(pointerEvent('pointermove',1,40,40))).toBe(false);
  expect(gesture.end(pointerEvent('pointerup'))).toBeNull();
  expect(gesture.begin({...pointerEvent('pointerdown'),button:2})).toBe(false);
});

const {pickFish}=require('../pond-follow');
test('head and long tail taps select a fish; empty water and a distant fish do not',()=>{
  const candidates=[{head:{x:100,y:80},tail:{x:100,y:220},radius:24},{head:{x:240,y:220},tail:{x:240,y:80},radius:24}];
  expect(pickFish({x:100,y:65},candidates)).toBe(0);
  expect(pickFish({x:110,y:230},candidates)).toBe(0);
  expect(pickFish({x:230,y:80},candidates)).toBe(1);
  expect(pickFish({x:170,y:150},candidates)).toBe(-1);
  expect(pickFish({x:100,y:250},candidates)).toBe(-1);
});
test('overlapping touch margins choose the nearer fish, including a collapsed projection',()=>{
  const candidates=[{head:{x:100,y:100},tail:{x:100,y:100},radius:24},{head:{x:130,y:90},tail:{x:130,y:120},radius:24}];
  expect(pickFish({x:120,y:100},candidates)).toBe(1);
  expect(pickFish({x:105,y:100},candidates)).toBe(0);
});
test('following water is opt-in, directional, throttled and stopped with the scene',async()=>{
  const f=fakeContext(),timer=schedule(),a=new PondAudio(function(){return f.ctx;},samples,timer.add,timer.cancel);
  a.follow(1);expect(f.sources).toHaveLength(0);
  await a.enable('lap');a.follow(0);expect(f.sources).toHaveLength(1);
  a.follow(.8);expect(f.sources).toHaveLength(2);expect(f.sources[1].start).toHaveBeenCalledWith(0,0,1.2);
  f.ctx.currentTime=.5;a.follow(1);expect(f.sources).toHaveLength(2);
  f.ctx.currentTime=1.5;a.follow(.8);expect(f.sources).toHaveLength(3);
  a.stop();for(const source of f.sources)expect(source.stop).toHaveBeenCalled();
  f.ctx.currentTime=10;a.follow(1);expect(f.sources).toHaveLength(3);
});

test('soft water gestures leave space between sounds and keep the recorded pitch',async()=>{
  const f=fakeContext(),timer=schedule(),a=new PondAudio(function(){return f.ctx;},samples,timer.add,timer.cancel);
  const play=jest.spyOn(a,'play');await a.enable('drop');
  expect(timer.add.mock.calls[0][1]).toBeGreaterThanOrEqual(9000);
  expect(timer.add.mock.calls[0][1]).toBeLessThanOrEqual(15000);
  f.ctx.currentTime=.4;a.drop();f.ctx.currentTime=1.79;a.drop();expect(play).toHaveBeenCalledTimes(1);
  f.ctx.currentTime=1.8;a.drop();expect(play).toHaveBeenCalledTimes(2);
  expect(play).toHaveBeenLastCalledWith('drop',.65,1);a.stop();
});
