import * as WebBrowser from 'expo-web-browser';
import { Text, View } from 'react-native';
WebBrowser.maybeCompleteAuthSession();
export default function Callback() { return <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}><Text>登录完成后，此窗口会自动关闭。 / This window closes after sign-in.</Text></View>; }
