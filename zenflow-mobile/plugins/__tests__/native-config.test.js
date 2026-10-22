/* global jest, beforeEach, afterEach, it, expect */
const fs = require('fs');
const os = require('os');
const path = require('path');

jest.mock('expo/config-plugins', () => {
  const register = (name) => (config, action) => {
    config.testMods ??= {};
    (config.testMods[name] ??= []).push(name === 'dangerous' ? action[1] : action);
    return config;
  };
  return { withProjectBuildGradle: register('project'), withAppBuildGradle: register('app'), withStringsXml: register('strings'), withAndroidManifest: register('manifest'), withDangerousMod: register('dangerous') };
});
const withPush = require('../withMobilePush');
const withUpdates = require('../withEnterpriseUpdates');
let directory;
beforeEach(() => { directory = fs.mkdtempSync(path.join(os.tmpdir(), 'matrix-plugin-test-')); fs.mkdirSync(path.join(directory, 'app')); });
afterEach(() => { fs.rmSync(directory, { recursive: true, force: true }); });
async function apply(plugin, options, fixtures) {
  const config = plugin({}, options);
  for (const [name, actions] of Object.entries(config.testMods)) {
    let mod = { modResults: fixtures[name], modRequest: { platformProjectRoot: directory } };
    for (const action of actions) mod = await action(mod);
    fixtures[name] = mod.modResults;
  }
  return fixtures;
}
function fixtures() { return { project: { contents: 'buildscript {\n}\n' }, app: { contents: 'android {\n}\n' }, strings: { resources: { string: [{ $: { name: 'app_name' }, _: '衍智云' }] } }, manifest: { manifest: { application: [{ activity: [{ $: { 'android:name': '.MainActivity' } }] }] } } }; }
it.each([['test','com.avataraitest.mobile','12345678'],['production','com.avatarai.mobile','87654321']])('writes %s HMS client resources idempotently', async (_env, packageName, appId) => {
  const configPath = path.join(directory, 'client.json'); const content = JSON.stringify({ client: { package_name: packageName, app_id: appId } });
  fs.writeFileSync(configPath, content);
  const value = fixtures();
  await apply(withPush, { configPath, appId }, value); await apply(withPush, { configPath, appId }, value);
  expect(value.project.contents.match(/com.huawei.agconnect:agcp/g)).toHaveLength(1);
  expect(value.app.contents.match(/apply plugin: 'com.huawei.agconnect'/g)).toHaveLength(1);
  expect(value.manifest.manifest.application[0].activity[0]['intent-filter']).toHaveLength(1);
  expect(value.strings.resources.string.filter((item) => item.$.name === 'matrix_hms_app_id')).toEqual([{ $: { name: 'matrix_hms_app_id', translatable: 'false' }, _: appId }]);
  expect(fs.readFileSync(path.join(directory, 'app/agconnect-services.json'), 'utf8')).toBe(content);
});
it('removes the AGConnect application plugin when unconfigured and refuses a stale config file', async () => {
  const value = fixtures(); value.app.contents += "\napply plugin: 'com.huawei.agconnect'\n";
  await apply(withPush, {}, value);
  expect(value.app.contents).not.toContain('apply plugin');
  expect(value.strings.resources.string.find((item) => item.$.name === 'matrix_hms_app_id')._).toBe('');
  fs.writeFileSync(path.join(directory, 'app/agconnect-services.json'), '{}');
  await expect(apply(withPush, {}, value)).rejects.toThrow('old agconnect-services.json');
});
it('pins update resources, serializes certificate lines, and keeps release unsigned without duplicate hooks', async () => {
  const certificatePath = path.join(directory, 'certificate.pem'); fs.writeFileSync(certificatePath, 'CERTIFICATE\nLINE\n');
  const value = fixtures(); const options = { certificatePath, policyUrl: 'https://updates.example/policy', channel: 'enterprise', environment: 'test' };
  await apply(withUpdates, options, value); await apply(withUpdates, options, value);
  expect(value.project.contents.match(/child.android.ndkVersion/g)).toHaveLength(1);
  expect(value.app.contents.match(/android.buildTypes.release.signingConfig = null/g)).toHaveLength(1);
  const entries = value.strings.resources.string.filter((item) => item.$.name.startsWith('matrix_'));
  expect(entries).toHaveLength(4);
  expect(entries.find((item) => item.$.name === 'matrix_update_certificate')._).toBe('CERTIFICATE\\nLINE\\n');
  expect(entries.find((item) => item.$.name === 'matrix_app_environment')._).toBe('test');
});
