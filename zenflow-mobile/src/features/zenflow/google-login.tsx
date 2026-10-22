import type { ZenflowConfig } from '@/core/zenflow/config';
import type { ZenflowSession } from '@/core/zenflow/session';
export type GoogleLoginProps = { config: ZenflowConfig; session: ZenflowSession; english: boolean; active: boolean; onSuccess(): void; onError(): void };
export function GoogleWebLogin(_props: GoogleLoginProps) { return null; }
