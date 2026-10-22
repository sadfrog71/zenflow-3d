import { GardenSaveAttempt } from '../garden-api';
import type { ZenflowSession } from '../session';
import type { GardenPatch, OperationReceipt } from '@/contracts/zenflow/garden';

const patch=():GardenPatch=>({expectedVersion:1,clientRequestId:'garden-request-1',operations:[{type:'SET_ENVIRONMENT',lampEnabled:false}]});
const success:OperationReceipt={clientRequestId:'garden-request-1',operationType:'GARDEN_PATCH',state:'SUCCEEDED',resultType:'GARDEN',resultId:'123',resultVersion:2};
function fixture(){
  const identity={profile:{tenantId:'1',userId:'42',subjectType:'APP_USER'},busy:false,generation:1};
  const request=jest.fn();
  return {identity,request,session:{snapshot:()=>identity,request} as unknown as ZenflowSession};
}
test('lost save response reconciles the existing receipt without a second write',async()=>{
  const f=fixture();f.request.mockRejectedValueOnce(Error('NETWORK')).mockResolvedValueOnce(success);
  const attempt=new GardenSaveAttempt(f.session,patch());await attempt.submit();
  expect(attempt.state).toBe('CONFIRMED');expect(attempt.receipt?.resultVersion).toBe(2);
  expect(f.request.mock.calls.filter(([,method])=>method==='PATCH')).toHaveLength(1);
  await attempt.submit();expect(f.request).toHaveBeenCalledTimes(2);
});
test('missing receipt remains unknown and never causes automatic resubmission',async()=>{
  const f=fixture();f.request.mockRejectedValue(Error('NOT_FOUND'));
  const attempt=new GardenSaveAttempt(f.session,patch());await attempt.submit();await attempt.reconcile();
  expect(attempt.state).toBe('UNKNOWN');expect(f.request.mock.calls.filter(([,method])=>method==='PATCH')).toHaveLength(1);
});
test('explicit retry retains original content even after the editor changes',async()=>{
  const f=fixture();f.request.mockRejectedValue(Error('NETWORK'));const body=patch();
  const attempt=new GardenSaveAttempt(f.session,body);await attempt.submit();
  body.expectedVersion=9;body.operations=[{type:'SET_ENVIRONMENT',presetId:'rain'}];
  f.request.mockResolvedValue(success);await attempt.submit();
  expect(f.request).toHaveBeenLastCalledWith('garden/state','PATCH',patch());
  expect(attempt.state).toBe('CONFIRMED');
});
test('conflict is reconciled as rejected and cannot silently rebase or retry',async()=>{
  const f=fixture();f.request.mockRejectedValueOnce(Error('CONFLICT')).mockResolvedValueOnce({...success,state:'REJECTED',safeErrorCode:'GARDEN_VERSION_CONFLICT'});
  const attempt=new GardenSaveAttempt(f.session,patch());await attempt.submit();
  expect(attempt.state).toBe('REJECTED');await attempt.submit();expect(f.request).toHaveBeenCalledTimes(2);
});
test('account switch including A to B to A invalidates a pending save generation',async()=>{
  const f=fixture();const attempt=new GardenSaveAttempt(f.session,patch());
  f.identity.generation=3;
  await expect(attempt.submit()).rejects.toThrow('STALE_IDENTITY');
  expect(attempt.state).toBe('LOCKED');expect(f.request).not.toHaveBeenCalled();
});
test('late response from the previous identity cannot report saved or query with new credentials',async()=>{
  const f=fixture();let complete!:(value:OperationReceipt)=>void;
  f.request.mockImplementation(()=>new Promise(resolve=>{complete=resolve;}));
  const attempt=new GardenSaveAttempt(f.session,patch());const pending=attempt.submit();
  f.identity.generation++;complete(success);
  await expect(pending).rejects.toThrow('STALE_IDENTITY');expect(attempt.state).toBe('LOCKED');
  expect(f.request).toHaveBeenCalledTimes(1);
});
test('a receipt for another request cannot masquerade as a confirmed save',async()=>{
  const f=fixture();f.request.mockResolvedValue({...success,clientRequestId:'another-request'});
  const attempt=new GardenSaveAttempt(f.session,patch());await attempt.submit();expect(attempt.state).toBe('UNKNOWN');
});
