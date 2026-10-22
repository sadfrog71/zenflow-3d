import React from 'react';
import { AppState } from 'react-native';
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { PrivateAudioNotes } from '../private-audio-notes';
let mockPermission=true,mockFileExists=true;
let mockPermissionWait:Promise<{granted:boolean}>|null=null;
const mockRecorder={currentTime:2,uri:'file:///cache/ExpoAudio/recording-fixture.m4a',prepareToRecordAsync:jest.fn(async()=>{}),record:jest.fn(),stop:jest.fn(async()=>{})};
const mockPlayer={pause:jest.fn(),replace:jest.fn(),play:jest.fn()};
const mockDelete=jest.fn(()=>{mockFileExists=false;});
jest.mock('@/platform/zenflow/private-media-codec',()=>({PRIVATE_AUDIO_LIMITS:{maxBytes:5242880,maxDurationSeconds:60}}));
jest.mock('expo-audio',()=>({RecordingPresets:{HIGH_QUALITY:{}},requestRecordingPermissionsAsync:async()=>mockPermissionWait??({granted:mockPermission}),setAudioModeAsync:jest.fn(async()=>{}),useAudioRecorder:()=>mockRecorder,useAudioRecorderState:()=>({durationMillis:1000}),useAudioPlayer:()=>mockPlayer,useAudioPlayerStatus:()=>({playing:false,didJustFinish:false})}));
jest.mock('expo-file-system',()=>({File:class{get exists(){return mockFileExists;}get size(){return 3;}bytes=async()=>new Uint8Array([1,2,3]);delete=mockDelete;}}));
jest.mock('expo-modules-core',()=>({...jest.requireActual('expo-modules-core'),requireNativeModule:(name:string)=>name==='InvelleGarden'?({preparePrivateAudioDirectory:async()=>{}}):jest.requireActual('expo-modules-core').requireNativeModule(name)}));
function handle(){return {generation:1,registerFlush:jest.fn(()=>()=>{}),store:{listMedia:jest.fn(async()=>[]),saveMedia:jest.fn(async()=>({id:'saved'})),releaseMediaPlayback:jest.fn(async()=>{})}} as any;}
beforeEach(()=>{jest.clearAllMocks();AppState.currentState='active';mockPermissionWait=null;mockPermission=true;mockFileExists=true;});
async function open(store:any){const screen=await render(<PrivateAudioNotes store={store} en={false} active/>);await fireEvent.press(screen.getByRole('button',{name:'本机原声草稿'}));await waitFor(()=>expect(screen.getByRole('button',{name:'开始录音'})).toBeTruthy());return screen;}
test('recording is explicit and stop saves original bytes privately before deleting its temporary file',async()=>{
 const store=handle(),screen=await open(store);
 expect(mockRecorder.record).not.toHaveBeenCalled();
 await fireEvent.press(screen.getByRole('button',{name:'开始录音'}));
 await waitFor(()=>expect(screen.getByRole('button',{name:'停止并存本机'})).toBeTruthy());
 await fireEvent.press(screen.getByRole('button',{name:'停止并存本机'}));
 await waitFor(()=>expect(store.store.saveMedia).toHaveBeenCalledWith(new Uint8Array([1,2,3]),'PRIVATE_NOTE',2,1));
 expect(mockDelete).toHaveBeenCalled();
 await screen.unmount();
});
test('permission denial never records or saves',async()=>{
 mockPermission=false;const store=handle(),screen=await open(store);
 await fireEvent.press(screen.getByRole('button',{name:'开始录音'}));
 await waitFor(()=>expect(screen.getByText('请在系统设置中允许麦克风权限后再录音。')).toBeTruthy());
 expect(mockRecorder.record).not.toHaveBeenCalled();expect(store.store.saveMedia).not.toHaveBeenCalled();await screen.unmount();
});
test('failed encryption keeps temporary audio for an explicit retry without claiming saved success',async()=>{
 const store=handle();store.store.saveMedia.mockRejectedValueOnce(Error('DISK_FULL'));const screen=await open(store);
 await fireEvent.press(screen.getByRole('button',{name:'开始录音'}));
 await waitFor(()=>expect(screen.getByRole('button',{name:'停止并存本机'})).toBeTruthy());
 await fireEvent.press(screen.getByRole('button',{name:'停止并存本机'}));
 await waitFor(()=>expect(screen.getByText('原声未能保存，请保留此页重试，或明确放弃。')).toBeTruthy());
 expect(mockDelete).not.toHaveBeenCalled();
 await fireEvent.press(screen.getByRole('button',{name:'重试加密保存'}));
 await waitFor(()=>expect(screen.getByText('原声已加密保存到本机。')).toBeTruthy());
 expect(store.store.saveMedia).toHaveBeenCalledTimes(2);expect(mockDelete).toHaveBeenCalled();await screen.unmount();
});
test('native recorder preparation failure cleans its temporary file and never claims a saved draft',async()=>{
 mockRecorder.prepareToRecordAsync.mockRejectedValueOnce(Error('NO_AUDIO_INPUT'));
 const store=handle(),screen=await open(store);
 await fireEvent.press(screen.getByRole('button',{name:'开始录音'}));
 await waitFor(()=>expect(screen.getByText('录音暂未能开始，请重试。')).toBeTruthy());
 expect(store.store.saveMedia).not.toHaveBeenCalled();expect(mockDelete).toHaveBeenCalled();await screen.unmount();
});

test('leaving while permission is pending prevents a late recording start',async()=>{
 let resolve!:(value:{granted:boolean})=>void;
 mockPermissionWait=new Promise(done=>{resolve=done;});
 const store=handle(),screen=await open(store);
 await fireEvent.press(screen.getByRole('button',{name:'开始录音'}));
 await screen.rerender(<PrivateAudioNotes store={store} en={false} active={false}/>);
 await act(async()=>{resolve({granted:true});});
 expect(mockRecorder.prepareToRecordAsync).not.toHaveBeenCalled();expect(mockRecorder.record).not.toHaveBeenCalled();
 expect(store.store.saveMedia).not.toHaveBeenCalled();await screen.unmount();
});
