/** @jest-environment node */
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, '../garden-incense.js'), 'utf8');
function setup() {
  let now = 1000, callback;
  const attributes = {}, parts = {}, classes = new Set(), events = {};
  const holder = {
    setAttribute: (key, value) => { attributes[key] = value; },
    querySelector: selector => ({ setAttribute: (key, value) => { parts[`${selector}:${key}`] = value; } }),
    classList: { toggle: (key, value) => value ? classes.add(key) : classes.delete(key) },
  };
  const document = { hidden: false, getElementById: () => holder, addEventListener: (key, fn) => { events[key] = fn; } };
  const audio = { stop: jest.fn() };
  const context = { window: { addEventListener: jest.fn() }, document, audioSource: audio, stopSound: audio.stop, Date: { now: () => now }, setInterval: jest.fn(fn => { callback = fn; return 1; }), clearInterval: jest.fn() };
  vm.runInNewContext(script, context);
  return { context, attributes, parts, classes, events, audio, advance(ms) { now += ms; callback?.(); } };
}
test('burns halfway at five minutes, disappears at ten, leaves audio untouched and never restarts on repeated entry', () => {
  const s = setup();
  s.context.window.InvelleIncense.start();
  expect(s.parts['[data-incense-stick]:y1']).toBe(46);
  s.advance(300000);
  expect(s.parts['[data-incense-stick]:y1']).toBe(98);
  expect(s.attributes['aria-valuenow']).toBe('50');
  s.context.window.InvelleIncense.start();
  expect(s.context.setInterval).toHaveBeenCalledTimes(1);
  s.advance(300000);
  expect(s.classes.has('burned')).toBe(true);
  expect(s.attributes['aria-valuenow']).toBe('100');
  expect(s.context.clearInterval).toHaveBeenCalled();
  expect(s.audio.stop).not.toHaveBeenCalled();
  s.context.window.InvelleIncense.start();
  expect(s.context.setInterval).toHaveBeenCalledTimes(1);
});
test('background suspends updates, foreground catches up without extending duration; language change preserves progress', () => {
  const s = setup();
  s.context.window.InvelleIncense.start();
  s.context.document.hidden = true;
  s.events.visibilitychange();
  expect(s.context.clearInterval).toHaveBeenCalled();
  s.advance(600000);
  s.context.document.hidden = false;
  s.events.visibilitychange();
  s.context.window.InvelleIncense.language('en');
  expect(s.attributes['aria-label']).toBe('Ten-minute incense');
  expect(s.classes.has('burned')).toBe(true);
  expect(s.context.setInterval).toHaveBeenCalledTimes(1);
});
