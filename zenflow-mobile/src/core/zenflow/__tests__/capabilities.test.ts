import { readCapabilities } from '../capabilities';
test('notes remain available with AI unavailable, and vice versa', () => {
  expect(readCapabilities({ notes: true, chat: false })).toMatchObject({ notes: true, chat: false, social: false, voiceMedia: false });
  expect(readCapabilities({ chat: true })).toMatchObject({ notes: false, chat: true });
});
test('unknown or malformed capability values never grant access', () => {
  expect(Object.values(readCapabilities(null)).every(v => !v)).toBe(true);
  expect(readCapabilities({ notes: 'true', chat: 1, social: {}, creation: true })).toMatchObject({ notes: false, chat: false, social: false, creation: true });
});
