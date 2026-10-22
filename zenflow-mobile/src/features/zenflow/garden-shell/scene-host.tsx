import { requireNativeView } from 'expo';
import { type ViewProps, View, Text, Platform } from 'react-native';
export type SceneHostProps = ViewProps & {directory:string;html?:string;resources?:Record<string,ArrayBuffer>;context:string;command:string;onMessage:(data:string)=>void};
const NativeGarden=Platform.OS==='ios'?requireNativeView<{source:{directory:string;context:string};command:string;onMessage:(event:{nativeEvent:{data:string}})=>void}&ViewProps>('InvelleGarden'):null;
export function SceneHost({directory,context,command,onMessage,style}:SceneHostProps){
  if(!NativeGarden)return <View style={style}><Text>Garden rendering is currently available on iOS and web.</Text></View>;
  return <NativeGarden style={style} source={{directory,context}} command={command} onMessage={event=>onMessage(event.nativeEvent.data)}/>;
}
