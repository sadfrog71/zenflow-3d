import React from 'react';
import { AppState,Text } from 'react-native';
import { act,render } from '@testing-library/react-native';
import { useOwnerStore,type OwnerDraftStore } from '../use-owner-store';
import { LocalStore } from '@/platform/zenflow/local-store';
import type { SessionView } from '@/core/zenflow/session';
jest.mock('@/platform/zenflow/local-store',()=>({LocalStore:{openOwner:jest.fn()}}));
const config={origin:'https://invelle.example',environment:'local',clientId:'local'};
const identity=(id:string,generation:number)=>({profile:{userId:id,tenantId:'1',subjectType:'APP_USER'},busy:false,generation}) as SessionView;
let snapshot:OwnerDraftStore|undefined;
function Harness({view}:{view:SessionView}){snapshot=useOwnerStore(config,view);return <Text>{snapshot?'ready':'locked'}</Text>;}
beforeEach(()=>{jest.clearAllMocks();AppState.currentState='active';});
test('no profile keeps the private database locked, and an owner change closes the prior connection',async()=>{
 const first={closeOwner:jest.fn(async()=>{})},second={closeOwner:jest.fn(async()=>{})};
 (LocalStore.openOwner as jest.Mock).mockResolvedValueOnce(first).mockResolvedValueOnce(second);
 const screen=await render(<Harness view={{profile:null,busy:false,generation:0}}/>);
 expect(LocalStore.openOwner).not.toHaveBeenCalled();
 await screen.rerender(<Harness view={identity('A',1)}/>);
 expect(snapshot?.generation).toBe(1);
 await screen.rerender(<Harness view={identity('B',2)}/>);
 expect(first.closeOwner).toHaveBeenCalled();expect(snapshot?.generation).toBe(2);
 await screen.unmount();expect(second.closeOwner).toHaveBeenCalled();
});
test('background waits for registered foreground flush before locking, then resume opens a fresh handle',async()=>{
 let listener:(state:any)=>void=()=>{};jest.spyOn(AppState,'addEventListener').mockImplementation((_event,callback)=>{listener=callback;return {remove:jest.fn()};});
 const first={closeOwner:jest.fn(async()=>{})},second={closeOwner:jest.fn(async()=>{})};
 (LocalStore.openOwner as jest.Mock).mockResolvedValueOnce(first).mockResolvedValueOnce(second);
 const screen=await render(<Harness view={identity('A',3)}/>);
 let finish:()=>void=()=>{};snapshot?.registerFlush(()=>new Promise<void>(resolve=>{finish=resolve;}));
 await act(async()=>{listener('inactive');});expect(first.closeOwner).not.toHaveBeenCalled();
 await act(async()=>{finish();});expect(first.closeOwner).toHaveBeenCalled();expect(snapshot).toBeUndefined();
 await act(async()=>{listener('active');});expect(snapshot?.store).toBe(second);
 await screen.unmount();jest.restoreAllMocks();
});
