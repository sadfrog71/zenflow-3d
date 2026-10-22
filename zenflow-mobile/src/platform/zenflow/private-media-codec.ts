import { AESEncryptionKey, AESSealedData, aesEncryptAsync, aesDecryptAsync } from 'expo-crypto';

// One bounded recording per envelope. Keys belong to the owner's SecureStore adapter.
export const PRIVATE_AUDIO_LIMITS = Object.freeze({ maxBytes: 5 * 1024 * 1024, maxDurationSeconds: 60 });
export type PrivateMediaIdentity = {
  ownerDigest: string;
  fileId: string;
  purpose: 'PRIVATE_NOTE' | 'WISH' | 'LETTER';
};
const HEADER = new Uint8Array([73, 78, 86, 77, 1]); // INVM, format version 1
function aad(identity: PrivateMediaIdentity): Uint8Array {
  if (!/^[a-f0-9]{64}$/.test(identity.ownerDigest)
      || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(identity.fileId)
      || !['PRIVATE_NOTE', 'WISH', 'LETTER'].includes(identity.purpose)) throw Error('INVALID_MEDIA_IDENTITY');
  return new TextEncoder().encode(JSON.stringify(['INVM', 1, identity.ownerDigest, identity.fileId, identity.purpose]));
}
function checkKey(key: string) {
  if (!/^[a-f0-9]{64}$/.test(key)) throw Error('INVALID_MEDIA_KEY');
}
export async function sealPrivateMedia(bytes: Uint8Array, keyHex: string, identity: PrivateMediaIdentity): Promise<Uint8Array> {
  checkKey(keyHex);
  const additionalData = aad(identity);
  if (!bytes.length || bytes.length > PRIVATE_AUDIO_LIMITS.maxBytes) throw Error('MEDIA_SIZE_LIMIT');
  const key = await AESEncryptionKey.import(keyHex, 'hex');
  // The native library generates a fresh nonce for every seal; callers cannot supply one.
  const sealed = await aesEncryptAsync(bytes, key, { additionalData, nonce: { length: 12 }, tagLength: 16 });
  const combined = await sealed.combined();
  const envelope = new Uint8Array(HEADER.length + combined.length);
  envelope.set(HEADER); envelope.set(combined, HEADER.length);
  return envelope;
}
export async function openPrivateMedia(envelope: Uint8Array, keyHex: string, identity: PrivateMediaIdentity): Promise<Uint8Array> {
  checkKey(keyHex);
  const additionalData = aad(identity);
  if (envelope.length <= HEADER.length + 28 || envelope.length > PRIVATE_AUDIO_LIMITS.maxBytes + HEADER.length + 28
      || !HEADER.every((value, index) => envelope[index] === value)) throw Error('INVALID_MEDIA_ENVELOPE');
  const key = await AESEncryptionKey.import(keyHex, 'hex');
  const sealed = AESSealedData.fromCombined(envelope.subarray(HEADER.length), { ivLength: 12, tagLength: 16 });
  return aesDecryptAsync(sealed, key, { additionalData, output: 'bytes' });
}
