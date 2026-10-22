import { GardenJournalStore, type GardenJournal } from '../garden-journal';
import type { LocalStore } from '@/platform/zenflow/local-store';
const value:GardenJournal={formatVersion:1,snapshot:null,pending:{expectedVersion:0,clientRequestId:'initialize-garden',operations:[{type:'INIT'}]}};
test('restored pending operation remains data, and cannot overwrite an unread local journal',async()=>{
  const store={readGardenJournal:jest.fn(async()=>({body:JSON.stringify(value),localRevision:5,persistedAt:10})),saveGardenJournal:jest.fn(async()=>({localRevision:6}))};
  const journal=new GardenJournalStore(store as unknown as LocalStore,7);
  await expect(journal.save(value)).rejects.toThrow('GARDEN_JOURNAL_NOT_READ');
  expect(await journal.read()).toEqual(value);
  expect(store.saveGardenJournal).not.toHaveBeenCalled();
  await journal.save(value);
  expect(store.saveGardenJournal).toHaveBeenCalledWith(JSON.stringify(value),5,7);
});
test('corrupt reread locks writes rather than permitting an old snapshot to replace it',async()=>{
  const store={readGardenJournal:jest.fn().mockResolvedValueOnce({body:JSON.stringify(value),localRevision:2}).mockResolvedValueOnce({body:'{}',localRevision:3}),saveGardenJournal:jest.fn()};
  const journal=new GardenJournalStore(store as unknown as LocalStore,7);
  await journal.read();await expect(journal.read()).rejects.toThrow('INVALID_GARDEN_JOURNAL');
  await expect(journal.save(value)).rejects.toThrow('GARDEN_JOURNAL_NOT_READ');
  expect(store.saveGardenJournal).not.toHaveBeenCalled();
});
