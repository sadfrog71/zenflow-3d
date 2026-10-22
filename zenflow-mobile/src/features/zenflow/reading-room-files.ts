import source from './reading-room-source.json';
import type { ZenflowConfig } from '@/core/zenflow/config';

export const readingRoomRevision = source.revision;
export function readingRoomSource(english: boolean, _config: ZenflowConfig | null) {
  return { html: source.html.replace('__INVELLE_ROOM_LANGUAGE__', english ? 'en' : 'zh') };
}
export const readingRoomOrigins = (_config: ZenflowConfig | null) => ['about:blank'];
