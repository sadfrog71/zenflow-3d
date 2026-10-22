import type { ConfigContext, ExpoConfig } from 'expo/config';

import staticConfig from './app.json';

const localBundleId = 'com.zenflow.local';

export default ({ config }: ConfigContext): ExpoConfig => {
  const base = staticConfig.expo as ExpoConfig;
  const webBuild = process.env.ZENFLOW_BUILD_TARGET === 'web';
  const production = process.env.ZENFLOW_APP_ENV === 'production';
  const bundleIdentifier = production
    ? process.env.ZENFLOW_IOS_BUNDLE_ID?.trim()
    : (process.env.ZENFLOW_IOS_BUNDLE_ID?.trim() || localBundleId);
  if (!webBuild && !bundleIdentifier) {
    throw new Error('ZENFLOW_IOS_BUNDLE_ID is required for a production build');
  }
  const apiUrl = process.env.EXPO_PUBLIC_ZENFLOW_API_URL?.trim();
  if (production && (!apiUrl || !apiUrl.startsWith('https://'))) {
    throw new Error('Production builds require EXPO_PUBLIC_ZENFLOW_API_URL over HTTPS');
  }
  if (!webBuild && (production || process.env.CONFIGURATION === 'Release')
      && (!apiUrl || !apiUrl.startsWith('https://') || !process.env.EXPO_PUBLIC_ZENFLOW_OAUTH_CLIENT_ID?.trim())) {
    throw new Error('Native release builds require Invelle HTTPS API and OAuth client configuration; use scripts/build-invelle-ios.sh');
  }
  const googleIos = process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_IOS_CLIENT_ID?.trim();
  const metaApp = process.env.EXPO_PUBLIC_ZENFLOW_META_APP_ID?.trim();
  const metaToken = process.env.EXPO_PUBLIC_ZENFLOW_META_CLIENT_TOKEN?.trim();
  if (production && !webBuild && (!googleIos || !process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_WEB_CLIENT_ID || !metaApp || !metaToken
      || process.env.EXPO_PUBLIC_ZENFLOW_APPLE_LOGIN !== 'true')) {
    throw new Error('Overseas production builds require Google, Facebook and Apple login configuration');
  }
  if (webBuild && (!apiUrl || !process.env.EXPO_PUBLIC_ZENFLOW_OAUTH_CLIENT_ID || !process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_WEB_CLIENT_ID)) {
    throw new Error('Web builds require product API, OAuth client and Google Web client configuration');
  }
  const plugins: NonNullable<ExpoConfig['plugins']> = [...(base.plugins || []), 'expo-apple-authentication', ['expo-sqlite', { useSQLCipher: true }]];
  if (googleIos) plugins.push(['@react-native-google-signin/google-signin', {
    iosUrlScheme: `com.googleusercontent.apps.${googleIos.split('.apps.googleusercontent.com')[0]}`,
  }]);
  if (metaApp && metaToken) plugins.push(['react-native-fbsdk-next', {
    appID: metaApp, clientToken: metaToken, displayName: '此间', scheme: `fb${metaApp}`,
    isAutoInitEnabled: false, autoLogAppEventsEnabled: false, advertiserIDCollectionEnabled: false,
  }]);
  return {
    ...config, ...base,
    ios: { ...base.ios, bundleIdentifier, usesAppleSignIn: true,
      appleTeamId: process.env.ZENFLOW_APPLE_TEAM_ID?.trim(),
      infoPlist: { ...base.ios?.infoPlist, FacebookAutoInitEnabled: false,
        FacebookAutoLogAppEventsEnabled: false, FacebookAdvertiserIDCollectionEnabled: false } },
    plugins,
    extra: { product: 'zenflow', brand: 'Invelle', environment: process.env.ZENFLOW_APP_ENV || 'local', edition: 'overseas' },
  };
};
