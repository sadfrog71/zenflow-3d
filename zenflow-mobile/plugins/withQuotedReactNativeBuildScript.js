const { withXcodeProject } = require('@expo/config-plugins');

const phaseName = 'Bundle React Native code and images';
const unsafeInvocation =
  '`"$NODE_BINARY" --print "require(\'path\').dirname(require.resolve(\'react-native/package.json\')) + \'/scripts/react-native-xcode.sh\'"`';
const safeInvocation = [
  'REACT_NATIVE_XCODE_SCRIPT="$("$NODE_BINARY" --print "require(\'path\').dirname(require.resolve(\'react-native/package.json\')) + \'/scripts/react-native-xcode.sh\'")"',
  '"$REACT_NATIVE_XCODE_SCRIPT"',
].join('\n');

module.exports = function withQuotedReactNativeBuildScript(config) {
  return withXcodeProject(config, (configWithProject) => {
    const phases = configWithProject.modResults.hash.project.objects.PBXShellScriptBuildPhase;
    const phase = Object.values(phases).find(
      (candidate) => candidate && candidate.name === `"${phaseName}"`
    );

    if (!phase || typeof phase.shellScript !== 'string') {
      throw new Error(`Unable to find the ${phaseName} Xcode build phase.`);
    }

    const script = JSON.parse(phase.shellScript);
    if (script.includes(unsafeInvocation)) {
      phase.shellScript = JSON.stringify(script.replace(unsafeInvocation, safeInvocation));
    } else if (!script.includes(safeInvocation)) {
      throw new Error(`Unexpected ${phaseName} script format; refusing an unsafe rewrite.`);
    }

    return configWithProject;
  });
};
