import type { GardenCapabilities } from '@/contracts/zenflow/garden';

export const unavailableCapabilities: GardenCapabilities = Object.freeze({
  scene: false, chat: false, notes: false, creation: false, voiceMedia: false, social: false,
});

/** Missing and malformed flags stay unavailable; one service never enables another. */
export function readCapabilities(value: unknown): GardenCapabilities {
  if (!value || typeof value !== 'object') return { ...unavailableCapabilities };
  const flags = value as Record<string, unknown>;
  return Object.fromEntries(Object.keys(unavailableCapabilities).map(key => [key, flags[key] === true])) as GardenCapabilities;
}
