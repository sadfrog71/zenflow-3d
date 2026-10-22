import type { ZenflowProfile, ZenflowTokenPair } from '@/contracts/zenflow/identity';
import { identityScope } from '@/contracts/zenflow/identity';
import { productUrl, type ZenflowConfig } from './config';
import { refreshToken, signInWithBrowser } from './oauth';
import { zenflowSecureSession, type StoredSession } from '@/platform/zenflow/secure-session';

type SessionStorage = {
  load(): Promise<StoredSession | null>;
  save(value: StoredSession): Promise<void>;
  clear(): Promise<void>;
  withLock?<T>(action: () => Promise<T>): Promise<T>;
  subscribe?(listener: () => void): () => void;
};
export type SessionView = { profile: ZenflowProfile | null; busy: boolean; generation: number };

export class ZenflowSession {
  private storageQueue: Promise<void> = Promise.resolve();
  private storageChange(action: () => Promise<void>): Promise<void> {
    const result = this.storageQueue.then(action);
    this.storageQueue = result.catch(() => {});
    return result;
  }
  private accessToken: string | null = null;
  private refreshValue: string | null = null;
  private state: SessionView = { profile: null, busy: false, generation: 0 };
  private listeners = new Set<(view: SessionView) => void>();
  private pending = new Set<AbortController>();
  constructor(private config: ZenflowConfig,
              private storage: SessionStorage = zenflowSecureSession,
              private browserLogin = signInWithBrowser,
              private refresh = refreshToken) {}

  snapshot(): SessionView { return { ...this.state }; }
  subscribe(listener: (view: SessionView) => void): () => void {
    this.listeners.add(listener); listener(this.snapshot()); return () => this.listeners.delete(listener);
  }
  private notify(): void { const view = this.snapshot(); this.listeners.forEach(listener => listener(view)); }
  private startChange(): number {
    for (const controller of this.pending) controller.abort();
    this.pending.clear();
    this.state = { profile: null, busy: true, generation: this.state.generation + 1 };
    this.accessToken = null; this.refreshValue = null; this.refreshing = null; this.notify();
    return this.state.generation;
  }
  private finishIfCurrent(generation: number, profile: ZenflowProfile | null): void {
    if (generation !== this.state.generation) return;
    this.state = { ...this.state, profile, busy: false }; this.notify();
  }

  private exclusive<T>(action: () => Promise<T>): Promise<T> {
    return this.storage.withLock ? this.storage.withLock(action) : action();
  }
  watchStoredIdentity(): () => void {
    return this.storage.subscribe?.(() => {
      void this.storage.load().then(saved => {
        const profile = this.state.profile;
        if (!saved || (profile && (saved.origin !== this.config.origin || saved.scope !== identityScope(this.config.environment, profile)))) {
          const generation = this.startChange(); this.finishIfCurrent(generation, null);
        }
      }).catch(() => {});
    }) ?? (() => {});
  }
  async restore(): Promise<void> {
    return this.exclusive(() => this.restoreStored());
  }
  private async restoreStored(): Promise<void> {
    const generation = this.startChange();
    try {
      const saved = await this.storage.load();
      if (!saved || saved.origin !== this.config.origin) { this.finishIfCurrent(generation, null); return; }
      const tokens = await this.refresh(this.config, saved.refreshToken);
      if (generation !== this.state.generation) return;
      if (tokens.refresh_token) await this.storageChange(() => this.storage.save({ ...saved, refreshToken: tokens.refresh_token! }));
      const profile = await this.loadProfile(tokens.access_token, generation);
      if (identityScope(this.config.environment, profile) !== saved.scope) throw new Error('STORED_IDENTITY_MISMATCH');
      if (generation !== this.state.generation) return;
      await this.persist({ ...tokens, refresh_token: tokens.refresh_token || saved.refreshToken }, profile);
      if (generation !== this.state.generation) return;
      this.accessToken = tokens.access_token;
      this.refreshValue = tokens.refresh_token || saved.refreshToken;
      this.finishIfCurrent(generation, profile);
    } catch (error) {
      const code = error instanceof Error ? error.message : '';
      if (generation === this.state.generation && ['OAUTH_SESSION_EXPIRED', 'STORED_IDENTITY_MISMATCH', 'INVALID_PROFILE', 'PROFILE_UNAUTHORIZED', 'INVALID_TOKEN_RESPONSE'].includes(code)) await this.storageChange(() => this.storage.clear());
      this.finishIfCurrent(generation, null);
    }
  }

  async signIn(login = this.browserLogin): Promise<void> {
    const generation = this.startChange();
    try {
      await this.exclusive(() => this.storageChange(() => this.storage.clear()));
      if (generation !== this.state.generation) return;
      const tokens = await login(this.config);
      if (!tokens || generation !== this.state.generation) { this.finishIfCurrent(generation, null); return; }
      const profile = await this.loadProfile(tokens.access_token, generation);
      if (generation !== this.state.generation) return;
      await this.exclusive(() => this.persist(tokens, profile));
      if (generation !== this.state.generation) return;
      this.accessToken = tokens.access_token;
      this.refreshValue = tokens.refresh_token || null;
      this.finishIfCurrent(generation, profile);
    } catch (error) {
      this.finishIfCurrent(generation, null);
      throw error;
    }
  }

  async signInExternal(provider: import('./external-login').ExternalProvider): Promise<void> {
    await this.signIn(async config => {
      const generation = this.state.generation;
      const { externalFlow } = require('./external-login') as typeof import('./external-login');
      const result = await externalFlow(config, provider, () => generation === this.state.generation);
      return result === 'BOUND' ? null : result;
    });
  }

  async bind(provider: import('./external-login').ExternalProvider): Promise<void> {
    const generation = this.state.generation;
    const bearer = this.accessToken;
    if (!bearer || !this.state.profile) throw new Error('AUTH_REQUIRED');
    const { externalFlow } = require('./external-login') as typeof import('./external-login');
    await externalFlow(this.config, provider, () => generation === this.state.generation, bearer);
  }

  async methods(): Promise<{ external: { provider: string }[]; available: string[] }> {
    return this.authenticated('account/login-methods');
  }

  async unbind(provider: import('./external-login').ExternalProvider): Promise<void> {
    await this.authenticated('account/login-methods/' + provider, 'DELETE');
    await this.signOut();
  }

  private async authenticated<T>(path: string, method = 'GET'): Promise<T> {
    if (!this.accessToken || !this.state.profile) throw new Error('AUTH_REQUIRED');
    const generation = this.state.generation;
    const controller = new AbortController(); this.pending.add(controller);
    try {
      const response = await fetch(productUrl(this.config, path), { method,
        headers: { Authorization: `Bearer ${this.accessToken}` }, signal: controller.signal });
      const body = await response.json();
      if (generation !== this.state.generation) throw new Error('STALE_IDENTITY');
      if (!response.ok) throw new Error(body.errorCode || 'ACCOUNT_REQUEST_FAILED');
      return body as T;
    } finally { this.pending.delete(controller); }
  }

  private refreshing: Promise<void> | null = null;
  private async renew(generation: number): Promise<void> {
    if (this.refreshing) return this.refreshing;
    const refreshValue = this.refreshValue;
    if (!refreshValue) throw new Error('AUTH_REQUIRED');
    const work = this.exclusive(async () => {
      if (generation !== this.state.generation || !this.state.profile) throw new Error('STALE_IDENTITY');
      const saved = this.storage.withLock ? await this.storage.load() : null;
      if (this.storage.withLock && (!saved || saved.origin !== this.config.origin || saved.scope !== identityScope(this.config.environment, this.state.profile))) throw new Error('STORED_IDENTITY_MISMATCH');
      const currentRefresh = saved?.refreshToken || refreshValue;
      const tokens = await this.refresh(this.config, currentRefresh);
      if (generation !== this.state.generation || !this.state.profile) throw new Error('STALE_IDENTITY');
      const profile = await this.loadProfile(tokens.access_token, generation);
      if (identityScope(this.config.environment, profile) !== identityScope(this.config.environment, this.state.profile)) throw new Error('STORED_IDENTITY_MISMATCH');
      await this.persist({ ...tokens, refresh_token: tokens.refresh_token || currentRefresh }, profile);
      if (generation !== this.state.generation) throw new Error('STALE_IDENTITY');
      this.accessToken = tokens.access_token; this.refreshValue = tokens.refresh_token || currentRefresh;
    });
    this.refreshing = work;
    try { await work; } finally { if (this.refreshing === work) this.refreshing = null; }
  }
  async request<T>(path: string, method = 'GET', input?: unknown, headers: Record<string, string> = {}, signal?: AbortSignal): Promise<T> {
    if (!this.accessToken || !this.state.profile) throw new Error('AUTH_REQUIRED');
    const generation = this.state.generation;
    const controller = new AbortController(); this.pending.add(controller);
    const abort = () => controller.abort();
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) abort();
    try {
      const send = () => fetch(productUrl(this.config, path), { method, headers: { ...headers, Authorization: `Bearer ${this.accessToken}`, ...(input === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: input === undefined ? undefined : JSON.stringify(input), signal: controller.signal });
      let response = await send();
      if (generation !== this.state.generation) throw new Error('STALE_IDENTITY');
      if (response.status === 401) { if (controller.signal.aborted) throw new Error('REQUEST_ABORTED'); await this.renew(generation); response = await send(); }
      const body = await response.json();
      if (generation !== this.state.generation) throw new Error('STALE_IDENTITY');
      if (!response.ok || (typeof body.code === 'number' && body.code !== 0)) throw new Error(body.data?.errorCode || body.errorCode || 'SERVICE_REQUEST_FAILED');
      return (Object.prototype.hasOwnProperty.call(body, 'code') && Object.prototype.hasOwnProperty.call(body, 'data') ? body.data : body) as T;
    } finally { signal?.removeEventListener('abort', abort); this.pending.delete(controller); }
  }
  async stream(path: string, onText: (text: string) => void, signal: AbortSignal): Promise<void> {
    if (!this.accessToken || !this.state.profile) throw new Error('AUTH_REQUIRED');
    const generation = this.state.generation;
    const controller = new AbortController(); this.pending.add(controller);
    const abort = () => controller.abort(); signal.addEventListener('abort', abort);
    if (signal.aborted) abort();
    try {
      const streamingFetch = (await import('expo/fetch')).fetch;
      const send = () => streamingFetch(productUrl(this.config, path), { headers: { Authorization: `Bearer ${this.accessToken}`, Accept: 'text/event-stream' }, signal: controller.signal });
      let response = await send();
      if (generation !== this.state.generation) throw new Error('STALE_IDENTITY');
      if (response.status === 401) { await this.renew(generation); response = await send(); }
      if (!response.ok || !response.body) throw new Error('STREAM_UNAVAILABLE');
      const reader = response.body.getReader(); const decoder = new TextDecoder();
      try {
        while (true) {
          const chunk = await reader.read();
          if (generation !== this.state.generation || signal.aborted) throw new Error('STALE_IDENTITY');
          if (chunk.done) { const end = decoder.decode(); if (end) onText(end); break; }
          onText(decoder.decode(chunk.value, { stream: true }));
        }
      } finally { await reader.cancel().catch(() => {}); }
    } finally { signal.removeEventListener('abort', abort); this.pending.delete(controller); }
  }

  async signOut(): Promise<void> {
    const oldAccess = this.accessToken;
    const generation = this.startChange();
    await this.exclusive(() => this.storageChange(() => this.storage.clear()));
    this.finishIfCurrent(generation, null);
    if (oldAccess) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 5000);
      try { await fetch(productUrl(this.config, 'auth/logout'), {
        method: 'POST', headers: { Authorization: `Bearer ${oldAccess}` }, signal: controller.signal,
      }); } catch { /* Local identity remains cleared if the server is unavailable. */ }
      finally { clearTimeout(timeout); }
    }
  }

  private async persist(tokens: ZenflowTokenPair, profile: ZenflowProfile): Promise<void> {
    if (!tokens.refresh_token) { await this.storageChange(() => this.storage.clear()); return; }
    const value: StoredSession = { refreshToken: tokens.refresh_token,
      scope: identityScope(this.config.environment, profile), origin: this.config.origin };
    await this.storageChange(() => this.storage.save(value));
  }

  private async loadProfile(token: string, generation: number): Promise<ZenflowProfile> {
    const controller = new AbortController(); this.pending.add(controller);
    try {
      const response = await fetch(productUrl(this.config, 'profile'), {
        headers: { Authorization: `Bearer ${token}` }, signal: controller.signal,
      });
      if (generation !== this.state.generation) throw new Error('STALE_IDENTITY');
      if (!response.ok) throw new Error(response.status === 401 || response.status === 403 ? 'PROFILE_UNAUTHORIZED' : 'PROFILE_UNAVAILABLE');
      const profile = await response.json() as ZenflowProfile;
      if (profile.subjectType !== 'APP_USER' || !profile.tenantId || !profile.userId) throw new Error('INVALID_PROFILE');
      return profile;
    } finally { this.pending.delete(controller); }
  }
}
