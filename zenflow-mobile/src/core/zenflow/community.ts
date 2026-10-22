import type { ZenflowSession } from './session';
import type { Page } from './core';
export type CommunityCategory = 'ESSAY' | 'SHORT' | 'POEM';
export type CommunityImage = { id: string; thumbnail: string; width?: number; height?: number };
export type CommunityPost = { id: string; title: string; text: string; author: string; category: CommunityCategory; state: string; featured: boolean; version: number; createdAt: string; isOwner: boolean; reviewNote?: string; images: CommunityImage[] };
export class CommunityApi {
 constructor(private session: ZenflowSession) {}
 posts(mine: boolean, category: string, before?: string) { const query = new URLSearchParams({ mine: String(mine), category }); if (before) query.set('before', before); return this.session.request<Page<CommunityPost>>(`community/posts?${query}`); }
 post(id: string) { return this.session.request<CommunityPost>(`community/posts/${id}`); }
 submit(title: string, text: string, category: CommunityCategory, images: CommunityImage[]) { return this.session.request<CommunityPost>('community/posts', 'POST', { title, text, category, attachmentIds: images.map(image => image.id) }); }
 withdraw(post: CommunityPost) { return this.session.request<CommunityPost>(`community/posts/${post.id}/withdraw`, 'POST', { expectedVersion: post.version }); }
 upload(base64: string) { return this.session.request<CommunityImage>('community/attachments', 'POST', { base64 }); }
 discard(id: string) { return this.session.request(`community/attachments/${id}`, 'DELETE'); }
 image(id: string) { return this.session.request<{ uri: string }>(`community/attachments/${id}`); }
}
