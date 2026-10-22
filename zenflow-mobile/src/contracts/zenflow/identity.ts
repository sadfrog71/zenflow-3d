export type ZenflowProfile = {
  userId: string;
  tenantId: string;
  subjectType: 'APP_USER';
  publicId: string;
  email: string;
  displayName?: string;
  accountEmail?: string;
  locale: 'zh-CN' | 'en-US';
  memoryEnabled: boolean;
  version: number;
};

export type ZenflowTokenPair = {
  access_token: string;
  refresh_token?: string;
  token_type: string;
  expires_in: number;
};

export type IdentityScope = `${string}:${string}:APP_USER:${string}`;

export function identityScope(environment: string, profile: ZenflowProfile): IdentityScope {
  return `${environment}:${profile.tenantId}:APP_USER:${profile.userId}`;
}
