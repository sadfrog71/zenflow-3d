import { ActivityTracker } from '../activity';
test('foreground start and stop are serialized; retries use same session; no pulses after stop', async () => {
 const send=jest.fn(async()=>({accepted:true}));let counter=0;const tracker=new ActivityTracker(send,()=>`session-${++counter}`);
 tracker.start();tracker.start();tracker.heartbeat();tracker.stop();tracker.heartbeat();await tracker.settled();
 expect(send.mock.calls.map(call=>(call as unknown as [{state:string}])[0].state)).toEqual(['START','HEARTBEAT','STOP']);
 tracker.start();await tracker.settled();expect(send.mock.calls.length).toBe(4);
});
test('failed initial delivery is retried as START before later heartbeat', async () => {
 const send=jest.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue({accepted:true});const tracker=new ActivityTracker(send,()=> 'session');
 tracker.start();await tracker.settled();tracker.heartbeat();await tracker.settled();tracker.heartbeat();await tracker.settled();
 expect(send.mock.calls.map(call=>call[0].state)).toEqual(['START','START','HEARTBEAT']);expect(send.mock.calls.every(call=>call[0].sessionKey==='session')).toBe(true);
});
