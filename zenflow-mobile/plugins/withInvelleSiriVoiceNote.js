const fs = require('node:fs');
const path = require('node:path');
const { withDangerousMod, withXcodeProject } = require('@expo/config-plugins');

const sourceName = 'InvelleSiriVoiceNoteIntent.swift';
const relativeSourcePath = `Invelle/${sourceName}`;
const privacyManifestName = 'PrivacyInfo.xcprivacy';
const relativePrivacyManifestPath = `Invelle/${privacyManifestName}`;
const siriLocalizationFiles = [
  'AppShortcuts.strings',
  'Localizable.strings',
].map((fileName) => ({
  source: path.join(__dirname, 'zh-Hans.lproj', fileName),
  relativeDestination: `Invelle/zh-Hans.lproj/${fileName}`,
}));

module.exports = function withInvelleSiriVoiceNote(config) {
  config = withDangerousMod(config, [
    'ios',
    async (modConfig) => {
      const projectRoot = modConfig.modRequest.platformProjectRoot;
      const sourcePath = path.join(__dirname, sourceName);
      const destinationPath = path.join(projectRoot, relativeSourcePath);
      fs.copyFileSync(sourcePath, destinationPath);
      fs.copyFileSync(
        path.join(__dirname, privacyManifestName),
        path.join(projectRoot, relativePrivacyManifestPath)
      );
      const localizationDirectory = path.join(projectRoot, 'Invelle/zh-Hans.lproj');
      fs.mkdirSync(localizationDirectory, { recursive: true });
      for (const localizationFile of siriLocalizationFiles) {
        fs.copyFileSync(
          localizationFile.source,
          path.join(projectRoot, localizationFile.relativeDestination)
        );
      }
      return modConfig;
    },
  ]);

  return withXcodeProject(config, (modConfig) => {
    const project = modConfig.modResults;
    const target = project.pbxTargetByName('Invelle') || project.getFirstTarget();
    const group = project.findPBXGroupKey({ name: 'Invelle' });
    const projectSection = project.pbxProjectSection();
    const projectObject = Object.values(projectSection).find((entry) => entry.isa === 'PBXProject');

    if (!target || !group) {
      throw new Error('Unable to find the Invelle iOS target or source group for Siri voice notes.');
    }
    if (projectObject && !projectObject.knownRegions.includes('zh-Hans')) {
      projectObject.knownRegions.push('zh-Hans');
    }

    if (!project.hasFile(relativeSourcePath)) {
      project.addSourceFile(relativeSourcePath, { target: target.uuid }, group);
    }
    if (!project.hasFile(relativePrivacyManifestPath)) {
      // xcode's resource helper expects a Resources group to exist.
      if (!project.pbxGroupByName('Resources')) {
        const resources = project.addPbxGroup([], 'Resources');
        project.addToPbxGroup(resources.uuid, group);
      }
      project.addResourceFile(relativePrivacyManifestPath, { target: target.uuid }, group);
    }
    for (const localizationFile of siriLocalizationFiles) {
      if (!project.hasFile(localizationFile.relativeDestination)) {
        project.addResourceFile(localizationFile.relativeDestination, { target: target.uuid }, group);
      }
    }

    return modConfig;
  });
};
