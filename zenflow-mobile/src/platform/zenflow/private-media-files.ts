import { File } from 'expo-file-system';
import * as SecureStore from 'expo-secure-store';
import { getRandomBytesAsync } from 'expo-crypto';
import { sealPrivateMedia, openPrivateMedia, PRIVATE_AUDIO_LIMITS, type PrivateMediaIdentity } from './private-media-codec';

/** Only encrypted bytes persist here. The SQLCipher journal owns STAGED/READY references. */
export class PrivateMediaFiles {
  constructor(private directory: string, private ownerDigest: string, private guard: () => void) {}
  private file(id: string, staging = false) {
    if (!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(id)) throw Error('INVALID_MEDIA_ID');
    return new File(this.directory, `${id}.${staging ? 'staging' : 'enc'}`);
  }
  private async key(allowCreate: boolean): Promise<string> {
    this.guard();
    const name = `invelle.owner.${this.ownerDigest}.media.v1`;
    let key = await SecureStore.getItemAsync(name);
    this.guard();
    if (!key && !allowCreate) throw Error('LOCAL_MEDIA_KEY_MISSING');
    if (!key) {
      key = Array.from(await getRandomBytesAsync(32), byte => byte.toString(16).padStart(2, '0')).join('');
      this.guard();
      await SecureStore.setItemAsync(name, key, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY });
      this.guard();
    }
    return key;
  }
  async save(identity: Omit<PrivateMediaIdentity, 'ownerDigest'>, bytes: Uint8Array, allowKeyCreation: boolean) {
    const key = await this.key(allowKeyCreation);
    const envelope = await sealPrivateMedia(bytes, key, { ...identity, ownerDigest: this.ownerDigest });
    this.guard();
    const final = this.file(identity.fileId), staging = this.file(identity.fileId, true);
    if (final.exists || staging.exists) throw Error('LOCAL_MEDIA_ALREADY_EXISTS');
    staging.create(); staging.write(envelope);
    // Verify actual bytes from disk before the atomic rename. No plaintext file is written.
    try {
      await openPrivateMedia(await staging.bytes(), key, { ...identity, ownerDigest: this.ownerDigest });
      this.guard(); staging.move(final);
    } catch (error) { if (staging.exists) staging.delete(); throw error; }
  }
  async read(identity: Omit<PrivateMediaIdentity, 'ownerDigest'>): Promise<Uint8Array> {
    const key = await this.key(false), file = this.file(identity.fileId);
    this.guard();
    if (!file.exists || file.size > PRIVATE_AUDIO_LIMITS.maxBytes + 33) throw Error('LOCAL_MEDIA_FILE_INVALID');
    const bytes = await openPrivateMedia(await file.bytes(), key, { ...identity, ownerDigest: this.ownerDigest });
    this.guard(); return bytes;
  }
  exists(id: string) { this.guard(); return this.file(id).exists; }
  discardStaging(id: string) { this.guard(); const file = this.file(id, true); if (file.exists) file.delete(); }
}
