import React from 'react';
import { act, fireEvent, render } from '@testing-library/react-native';
import { GardenArchivePanel } from '../garden-shell/garden-archive-panel';
import type { OwnerDraftStore } from '../use-owner-store';
jest.mock('expo-crypto',()=>({randomUUID:()=> 'garden-request-123'}));
function handle(){return {store:{readGardenJournal:jest.fn(async()=>null),saveGardenJournal:jest.fn(async()=>({localRevision:1}))},generation:1,registerFlush:()=>()=>{}};}
test('selected night becomes an explicit encrypted initialization choice, with local-only confirmation',async()=>{
  const owner=handle();const screen=await render(<GardenArchivePanel localDrafts={owner as unknown as OwnerDraftStore} english={false}/>);
  await fireEvent.press(screen.getByText('夜晚'));await fireEvent.press(screen.getByText('保存到本机'));
  const body=JSON.parse((owner.store.saveGardenJournal.mock.calls as unknown as [string][])[0][0]);
  expect(body.pending.operations).toEqual([{type:'INIT',choices:{environment:{presetId:'night',lampEnabled:true}}}]);
  expect(screen.getByText('已存本机，云端连接待接通。')).toBeTruthy();
  expect(screen.getByText('保存到本机')).toBeTruthy();
});
test('late local save does not populate another owner panel',async()=>{
  const first=handle(),second=handle();second.generation=2;
  let complete!:(value:{localRevision:number})=>void;
  first.store.saveGardenJournal.mockImplementation(()=>new Promise(resolve=>{complete=resolve;}));
  const screen=await render(<GardenArchivePanel localDrafts={first as unknown as OwnerDraftStore} english={false}/>);
  await fireEvent.press(screen.getByText('保存到本机'));
  await screen.rerender(<GardenArchivePanel localDrafts={second as unknown as OwnerDraftStore} english={false}/>);
  await act(async()=>complete({localRevision:1}));
  expect(screen.queryByText('已存本机，云端连接待接通。')).toBeNull();
  expect(screen.getByText('保存到本机')).toBeTruthy();
});

test('an unsent local choice remains editable across saves, while unknown results stay locked',async()=>{
  const owner=handle();const preview=jest.fn();
  const screen=await render(<GardenArchivePanel localDrafts={owner as unknown as OwnerDraftStore} english={false} onEnvironment={preview}/>);
  await fireEvent.press(screen.getByText('夜晚'));await fireEvent.press(screen.getByText('保存到本机'));
  await fireEvent.press(screen.getByText('白昼'));await fireEvent.press(screen.getByText('保存到本机'));
  const calls=owner.store.saveGardenJournal.mock.calls as unknown as [string,number,number][];
  expect(calls).toHaveLength(2);
  expect(JSON.parse(calls[1][0]).pending.operations[0].choices.environment.presetId).toBe('day');
  expect(JSON.parse(calls[1][0]).pendingDisposition).toBe('LOCAL_ONLY');
  expect(preview).toHaveBeenLastCalledWith({presetId:'day',lampEnabled:true});
  const unknown=handle();
  unknown.store.readGardenJournal.mockResolvedValue({body:calls[1][0].replace('LOCAL_ONLY','UNKNOWN'),localRevision:4} as never);
  await screen.rerender(<GardenArchivePanel localDrafts={unknown as unknown as OwnerDraftStore} english={false}/>);
  expect(screen.queryByText('保存到本机')).toBeNull();
  expect(screen.getByText('有一份保存请求待核对，原内容已保留。')).toBeTruthy();
});
