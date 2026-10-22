import { openPrivateMedia, sealPrivateMedia, PRIVATE_AUDIO_LIMITS } from '../private-media-codec';
// Real AES-GCM via Node for portable contract tests; simulator must separately prove Expo's implementation.
jest.mock('expo-crypto', () => {
  const crypto = require('node:crypto');
  const Buffer = require('buffer').Buffer;
  return {
    AESEncryptionKey: { import: async (hex: string) => Buffer.from(hex, 'hex') },
    AESSealedData: { fromCombined: (bytes: Uint8Array) => Buffer.from(bytes) },
    aesEncryptAsync: async (bytes: Uint8Array, key: Uint8Array, options: any) => {
      const nonce = crypto.randomBytes(12);
      const cipher = crypto.createCipheriv('aes-256-gcm', key, nonce);
      cipher.setAAD(Buffer.from(options.additionalData));
      const encrypted = Buffer.concat([cipher.update(bytes), cipher.final()]);
      return { combined: async () => new Uint8Array(Buffer.concat([nonce, encrypted, cipher.getAuthTag()])) };
    },
    aesDecryptAsync: async (bytes: Uint8Array, key: Uint8Array, options: any) => {
      const decipher = crypto.createDecipheriv('aes-256-gcm', key, bytes.subarray(0, 12));
      decipher.setAAD(Buffer.from(options.additionalData)); decipher.setAuthTag(bytes.subarray(-16));
      return new Uint8Array(Buffer.concat([decipher.update(bytes.subarray(12, -16)), decipher.final()]));
    },
  };
});
const identity = { ownerDigest: 'a'.repeat(64), fileId: '12345678-1234-1234-1234-123456789abc', purpose: 'PRIVATE_NOTE' as const };
const key = 'ab'.repeat(32);
const bytes = new Uint8Array([82, 73, 70, 70, 1, 2, 3]);
test('round trips bounded original bytes with fresh random nonces', async () => {
  const first = await sealPrivateMedia(bytes, key, identity);
  const second = await sealPrivateMedia(bytes, key, identity);
  expect(first).not.toEqual(second);
  expect(await openPrivateMedia(first, key, identity)).toEqual(bytes);
});
test('rejects wrong owner, file, purpose, key and modified ciphertext', async () => {
  const sealed = await sealPrivateMedia(bytes, key, identity);
  for (const changed of [{ ...identity, ownerDigest: 'b'.repeat(64) }, { ...identity, fileId: '22345678-1234-1234-1234-123456789abc' }, { ...identity, purpose: 'LETTER' as const }]) {
    await expect(openPrivateMedia(sealed, key, changed)).rejects.toThrow();
  }
  await expect(openPrivateMedia(sealed, 'cd'.repeat(32), identity)).rejects.toThrow();
  sealed[20] ^= 1;
  await expect(openPrivateMedia(sealed, key, identity)).rejects.toThrow();
});
test('fails closed for invalid envelopes, keys, identity and oversize input', async () => {
  await expect(openPrivateMedia(bytes, key, identity)).rejects.toThrow('INVALID_MEDIA_ENVELOPE');
  await expect(sealPrivateMedia(bytes, '', identity)).rejects.toThrow('INVALID_MEDIA_KEY');
  await expect(sealPrivateMedia(bytes, key, { ...identity, fileId: '../other' })).rejects.toThrow('INVALID_MEDIA_IDENTITY');
  await expect(sealPrivateMedia(new Uint8Array(0), key, identity)).rejects.toThrow('MEDIA_SIZE_LIMIT');
  await expect(sealPrivateMedia(new Uint8Array(PRIVATE_AUDIO_LIMITS.maxBytes + 1), key, identity)).rejects.toThrow('MEDIA_SIZE_LIMIT');
});
