import type { ZenflowProfile } from '@/contracts/zenflow/identity';
import type { ZenflowConfig } from './config';

export type LocalOwner = { origin: string; environment: string; tenantId: string; subjectType: 'APP_USER'; userId: string };
export function localOwner(config: ZenflowConfig, profile: ZenflowProfile): LocalOwner {
  const url = new URL(config.origin);
  if (url.protocol !== 'https:' || url.origin !== config.origin || profile.subjectType !== 'APP_USER'
      || !config.environment || !profile.tenantId || !profile.userId) throw Error('INVALID_LOCAL_OWNER');
  return { origin: url.origin, environment: config.environment, tenantId: profile.tenantId, subjectType: 'APP_USER', userId: profile.userId };
}
export function serializeOwner(owner: LocalOwner): string {
  if (owner.subjectType !== 'APP_USER' || !owner.tenantId || !owner.userId || !owner.environment
      || new URL(owner.origin).protocol !== 'https:' || new URL(owner.origin).origin !== owner.origin) throw Error('INVALID_LOCAL_OWNER');
  return JSON.stringify([owner.origin, owner.environment, owner.tenantId, owner.subjectType, owner.userId]);
}
