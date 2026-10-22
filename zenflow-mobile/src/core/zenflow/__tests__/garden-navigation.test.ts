import { GardenNavigation } from '../garden-navigation';
const envelope = (n: GardenNavigation, type: string, requestId: string, payload: object = {}) => ({protocolVersion: 1, sceneInstanceId: n.instanceId, identityGeneration: n.generation, type, requestId, payload:type==='navigation.arrived'?{level:n.snapshot.pending?.level,...payload}:payload});
describe('garden native authority', () => {
  test('starts at the world map and enters the area before first person',()=>{
    const n=new GardenNavigation('s',0);expect(n.snapshot.level).toBe('WORLD_OVERVIEW');n.receive(envelope(n,'scene.ready','ready'));
    expect(n.navigate('N03','skip','FIRST_PERSON')).toBe(false);
    n.navigate('N03','area');n.receive(envelope(n,'navigation.arrived','area',{viewpointId:'N03'}));expect(n.snapshot.level).toBe('SPACE_OVERVIEW');
    n.navigate('N03','sit','FIRST_PERSON');n.receive(envelope(n,'navigation.arrived','sit',{viewpointId:'N03'}));expect(n.snapshot.level).toBe('FIRST_PERSON');
    n.navigate('N03','back','SPACE_OVERVIEW');n.receive(envelope(n,'navigation.arrived','back',{viewpointId:'N03'}));expect(n.snapshot.level).toBe('SPACE_OVERVIEW');
  });
  test('ready during background loading resumes without an endless loading screen', () => {
    const n=new GardenNavigation('s',0);n.suspend();
    expect(n.receive(envelope(n,'scene.ready','ready'))).toBe(true);
    expect(n.snapshot.mode).toBe('SUSPENDED');n.resume();expect(n.snapshot.mode).toBe('IDLE_LOOK');
  });
  test('stale identity, instance, unknown fields and duplicate requests cannot execute', () => {
    const n = new GardenNavigation('scene-a', 2);
    expect(n.receive(envelope(n, 'scene.ready', 'r0'))).toBe(true);
    expect(n.receive({...envelope(n,'hotspot.activate','r1',{anchorId:'wish.take',viewpointId:'N03'})})).toBe(false);
    expect(n.receive({...envelope(n,'scene.ready','r2'), identityGeneration:1})).toBe(false);
    expect(n.receive({...envelope(n,'scene.ready','r2'), sceneInstanceId:'old'})).toBe(false);
    expect(n.receive({...envelope(n,'scene.ready','r2'), token:'secret'})).toBe(false);
    expect(n.receive(envelope(n,'scene.ready','r0'))).toBe(false);
  });
  test('late arrival after cancellation cannot move the stable viewpoint', () => {
    const n = new GardenNavigation('scene-a', 2); n.receive(envelope(n,'scene.ready','ready'));
    n.navigate('N03', 'trip'); n.cancel();
    expect(n.receive(envelope(n,'navigation.arrived','trip',{viewpointId:'N03'}))).toBe(false);
    expect(n.snapshot.nodeId).toBe('N00');
  });
  test('panels and suspension block navigation; close retains original viewpoint', () => {
    const n=new GardenNavigation('s',0);n.receive(envelope(n,'scene.ready','ready'));n.navigate('N03','trip');
    expect(n.receive(envelope(n,'navigation.arrived','trip',{viewpointId:'N03'}))).toBe(true);
    n.openPanel('settings');expect(n.navigate('N00','blocked')).toBe(false);n.closePanel();expect(n.snapshot.nodeId).toBe('N03');
    n.suspend();expect(n.navigate('N00','background')).toBe(false);n.resume();expect(n.snapshot.mode).toBe('IDLE_LOOK');
  });
});
