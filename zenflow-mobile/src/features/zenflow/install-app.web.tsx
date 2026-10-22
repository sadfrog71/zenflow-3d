import { useEffect, useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
type InstallPrompt = Event & { prompt(): Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> };
declare global { interface Window { invelleInstallPrompt?: InstallPrompt | null; } }
export function InstallApp({ en }: { en: boolean }) {
  const [installed, setInstalled] = useState(() => window.matchMedia('(display-mode: standalone)').matches);
  const [help, setHelp] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const done = () => { setInstalled(true); setHelp(false); };
    window.addEventListener('appinstalled', done);
    const media = window.matchMedia('(display-mode: standalone)');
    const changed = () => setInstalled(media.matches);
    media.addEventListener('change', changed);
    return () => { window.removeEventListener('appinstalled', done); media.removeEventListener('change', changed); };
  }, []);
  if (installed) return null;
  const install = async () => {
    const prompt = window.invelleInstallPrompt;
    if (!prompt) { setHelp(true); return; }
    window.invelleInstallPrompt = null;
    setBusy(true);
    try { await prompt.prompt(); if ((await prompt.userChoice).outcome === 'accepted') setInstalled(true); }
    catch { setHelp(true); }
    finally { setBusy(false); }
  };
  return <><Pressable accessibilityRole="button" disabled={busy} onPress={() => void install()} style={{paddingVertical:8,paddingHorizontal:10}}><Text style={{color:'#637e56',fontSize:13}}>{en ? 'Install app' : '安装应用'}</Text></Pressable>
    <Modal visible={help} transparent animationType="fade" onRequestClose={() => setHelp(false)}><View style={{flex:1,justifyContent:'center',alignItems:'center',backgroundColor:'#0006',padding:24}}><View style={{backgroundColor:'#f5f4ed',padding:24,borderRadius:18,width:'100%',maxWidth:420,gap:16}}><Text style={{fontSize:20,color:'#355a45'}}>{en ? 'Install Invelle' : '安装此间'}</Text><Text style={{fontSize:15,lineHeight:25,color:'#355a45'}}>{en ? 'In Chrome, use the install icon in the address bar, or open the browser menu and look for “Install page as app” under “Cast, save, and share”. If already installed, open Invelle from your applications. An internet connection is needed to chat.' : '在 Chrome 中点击地址栏的安装图标，或打开浏览器菜单，在“投放、保存和分享”中选择“将网页安装为应用”。如果已经安装，可从电脑的应用列表打开此间。聊天需要联网。'}</Text><Pressable accessibilityRole="button" onPress={() => setHelp(false)} style={{padding:10,alignSelf:'flex-end'}}><Text style={{color:'#355a45'}}>{en ? 'Got it' : '知道了'}</Text></Pressable></View></View></Modal></>;
}
