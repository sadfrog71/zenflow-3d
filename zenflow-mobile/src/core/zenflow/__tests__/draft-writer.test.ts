import { DraftWriter } from '../draft-writer';
beforeEach(()=>{jest.useFakeTimers();jest.setSystemTime(0);});
afterEach(()=>jest.useRealTimers());
test('debounces edits but writes after at most two seconds of continuous input',async()=>{
 const saveDraft=jest.fn(async()=>{}),notify=jest.fn();const writer=new DraftWriter({saveDraft},'editor',4,notify);
 for(let i=0;i<5;i++){writer.schedule(`body-${i}`);await jest.advanceTimersByTimeAsync(400);}
 expect(saveDraft).toHaveBeenCalledWith('editor','NOTE','body-4',4);
 expect(notify).toHaveBeenCalledWith('body-4');writer.cancel();
});
test('failure never emits a saved success and can retry the same snapshot',async()=>{
 const saveDraft=jest.fn().mockRejectedValueOnce(Error('disk full')).mockResolvedValueOnce(undefined),notify=jest.fn();
 const writer=new DraftWriter({saveDraft},'editor',2,notify);writer.schedule('pending');
 await expect(writer.flush()).rejects.toThrow('disk full');expect(notify).toHaveBeenCalledWith('pending',expect.any(Error));
 await writer.flush();expect(notify).toHaveBeenLastCalledWith('pending');writer.cancel();
});
test('cancel prevents late completion from reporting private draft success to a new owner',async()=>{
 let finish:()=>void=()=>{};const notify=jest.fn(),saveDraft=jest.fn(()=>new Promise<void>(resolve=>{finish=resolve;}));
 const writer=new DraftWriter({saveDraft},'editor',1,notify);writer.schedule('private');const work=writer.flush();
 await Promise.resolve();writer.cancel();finish();await work;expect(notify).not.toHaveBeenCalled();
});
test('cloud acknowledgement prevents a failed local snapshot from being resurrected during cleanup',async()=>{
 const saveDraft=jest.fn().mockRejectedValueOnce(Error('disk full')),notify=jest.fn();
 const writer=new DraftWriter({saveDraft},'editor',1,notify);writer.schedule('submitted');
 await writer.flush().catch(()=>{});writer.acknowledge('submitted');await writer.flush().catch(()=>{});
 expect(saveDraft).toHaveBeenCalledTimes(1);writer.cancel();
});
