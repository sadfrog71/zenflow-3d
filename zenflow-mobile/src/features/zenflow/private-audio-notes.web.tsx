import { Text } from 'react-native';
import type { OwnerDraftStore } from './use-owner-store';
export function PrivateAudioNotes({en}:{store?:OwnerDraftStore;en:boolean;active:boolean}){
  return <Text>{en?'Original voice drafts are available in the iPad app.':'原声草稿请在 iPad App 中使用。'}</Text>;
}
