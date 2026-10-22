import type { ZenflowConfig } from '@/core/zenflow/config';

export const readingRoomRevision = 'hosted';
export function readingRoomSource(english: boolean, config: ZenflowConfig | null) {
  return config ? { uri: `${config.origin}/reading-room/experience/index.html?native=1&lang=${english ? 'en' : 'zh'}` } : null;
}
export const readingRoomOrigins = (config: ZenflowConfig | null) => config ? [config.origin] : [];
