import { Alert } from '@/platform/zenflow/alert';
import { useOwnerStore } from '@/features/zenflow/use-owner-store';
import { InstallApp } from '@/features/zenflow/install-app';
import { GoogleWebLogin } from '@/features/zenflow/google-login';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useWindowDimensions, AppState, Image, Keyboard, KeyboardAvoidingView, Platform, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { RestSpace } from '@/features/zenflow/rest-space';
import { TabIcon } from '@/features/zenflow/tab-icon';
import { CommunitySpace } from '@/features/zenflow/community';
import { CoreSpace } from '@/features/zenflow/core-spaces';
import { addVoiceShortcutListener, takePendingVoiceShortcutRequest } from '@/features/zenflow/voice-input';
import { productUrl, readZenflowConfig } from '@/core/zenflow/config';
import { ZenflowSession, type SessionView } from '@/core/zenflow/session';

import { readCapabilities, unavailableCapabilities } from '@/core/zenflow/capabilities';
import { ActivityTracker } from '@/core/zenflow/activity';
import { externalConfigured, type ExternalProvider } from '@/core/zenflow/external-login';

type Tab = 'rest' | 'chat' | 'create' | 'notes';
const tabs: { key: Tab; zh: string; en: string }[] = [
  { key: 'rest', zh: '休息', en: 'Rest' },
  { key: 'chat', zh: '聊天', en: 'Talk' },
  { key: 'create', zh: '创作', en: 'Create' },
  { key: 'notes', zh: '记录', en: 'Notes' },
];

export default function ZenflowApp() {
  const { width } = useWindowDimensions();
  const desktop = Platform.OS === 'web' && width >= 900;
  const [tab, setTab] = useState<Tab>('rest');
  const [voiceEntryRequestId, setVoiceEntryRequestId] = useState(0);
  const [voiceEntryRequestConsumed, setVoiceEntryRequestConsumed] = useState(0);
  const siriVoiceRequestRead = useRef<Promise<boolean> | null>(null);
  const [writingFocused, setWritingFocused] = useState(false);
  const [seated, setSeated] = useState(false);
  const [keyboardVisible, setKeyboardVisible] = useState(false);
  const [appForeground,setAppForeground]=useState(AppState.currentState==='active');
  useEffect(()=>{const listener=AppState.addEventListener('change',value=>setAppForeground(value==='active'));return()=>listener.remove();},[]);
  useEffect(() => {
    const show = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow', () => setKeyboardVisible(true));
    const hide = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide', () => setKeyboardVisible(false));
    return () => { show.remove(); hide.remove(); };
  }, []);
  const [english, setEnglish] = useState(() => !/^zh/i.test(Intl.DateTimeFormat().resolvedOptions().locale));
  const [error, setError] = useState('');
  const [capabilities, setCapabilities] = useState(unavailableCapabilities);
  const [homeSlogans,setHomeSlogans]=useState<{zh:string;en:string}[]>([]);
  const [loginOpen, setLoginOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [manage, setManage] = useState(false);
  const [methods, setMethods] = useState<string[]>([]);
  const [working, setWorking] = useState(false);
  const config = useMemo(() => readZenflowConfig(), []);
  const session = useMemo(() => config ? new ZenflowSession(config) : null, [config]);
  const [identity, setIdentity] = useState<SessionView>({ profile: null, busy: false, generation: 0 });
  const ownerDrafts=useOwnerStore(config,identity);
  const pendingVoiceEntryRequest = identity.profile && capabilities.notes && voiceEntryRequestId > voiceEntryRequestConsumed
    ? voiceEntryRequestId : 0;
  useEffect(() => {
    if (!session) return;
    const unsubscribe = session.subscribe(setIdentity);
    const unwatch = session.watchStoredIdentity();
    const retry = () => { if (!session.snapshot().profile && !session.snapshot().busy) void session.restore(); };
    if (Platform.OS === 'web') window.addEventListener('online', retry);
    session.restore().catch(() => setError(english ? 'Could not restore your session.' : '登录状态恢复失败。'));
    return () => { unsubscribe(); unwatch(); if (Platform.OS === 'web') window.removeEventListener('online', retry); };
  }, [session]);
  useEffect(() => {
    let active = true;
    const readRequest = () => {
      siriVoiceRequestRead.current ??= takePendingVoiceShortcutRequest();
      return siriVoiceRequestRead.current;
    };
    const consumeRequest = async () => {
      if (await readRequest() && active) {
        setTab('notes');
        setVoiceEntryRequestId(value => value + 1);
      }
    };
    const subscription = addVoiceShortcutListener(() => {
      siriVoiceRequestRead.current = null;
      void consumeRequest();
    });
    void consumeRequest();
    return () => { active = false; subscription?.remove(); };
  }, []);
  useEffect(() => {
    if (!session || !identity.profile) return;
    const generation = identity.generation, owner = identity.profile.userId;
    const tracker = new ActivityTracker(pulse => {
      const current = session.snapshot();
      if (current.generation !== generation || current.profile?.userId !== owner) return Promise.resolve();
      return session.request('activity', 'POST', pulse);
    });
    if (AppState.currentState === 'active') tracker.start();
    const state = AppState.addEventListener('change', value => { if (value === 'active') tracker.start(); else if (value === 'background') tracker.stop(); });
    const timer = setInterval(() => tracker.heartbeat(), 30000);
    return () => { clearInterval(timer); state.remove(); tracker.stop(); };
  }, [session, identity.generation, identity.profile?.userId]);
  useEffect(() => {
    if (!config) return;
    let active = true; const controller = new AbortController();
    const update = async () => {
      try { const response = await fetch(productUrl(config, 'bootstrap'), { signal: controller.signal }); const value = await response.json(); if (active) {setCapabilities(readCapabilities(response.ok ? value.capabilities : null));if(response.ok&&Array.isArray(value.homeSlogans))setHomeSlogans(value.homeSlogans.filter((x:any)=>typeof x.zh==='string'&&typeof x.en==='string'));} }
      catch { if (active) setCapabilities(unavailableCapabilities); }
    };
    void update(); const interval = setInterval(() => void update(), 60000);
    const subscription = AppState.addEventListener('change', state => { if (state === 'active') void update(); });
    return () => { active = false; controller.abort(); clearInterval(interval); subscription.remove(); };
  }, [config, identity.generation]);
  const emailSignIn = async () => { if (!session) return; setError('');
    try { await session.signIn(); } catch { setError(english ? 'Sign in did not finish. Please try again.' : '登录没有完成，请重试。'); }
  };
  const signIn = () => { setManage(false); setLoginOpen(true); };
  const manageLogin = async () => {
    if (!session) return;
    try { const data = await session.methods(); setMethods(data.external.map(item => item.provider)); setManage(true); setLoginOpen(true); }
    catch { setError(english ? 'Could not load login methods.' : '无法读取登录方式。'); }
  };
  const providerAction = async (provider: ExternalProvider, remove = false) => {
    if (!session || working) return;
    setError(''); setWorking(true);
    try {
      if (manage) { if (remove) await session.unbind(provider); else await session.bind(provider); }
      else await session.signInExternal(provider);
      if (manage && !remove) { const data = await session.methods(); setMethods(data.external.map(item => item.provider)); }
      else setLoginOpen(false);
    } catch (error) {
      const code = error instanceof Error ? error.message : '';
      setError(code === 'LAST_LOGIN_METHOD' ? (english ? 'Keep at least one login method.' : '至少保留一种登录方式。')
        : code === 'RECENT_LOGIN_REQUIRED' ? (english ? 'Sign in again before changing login methods.' : '修改登录方式前，请重新登录。')
        : code === 'IDENTITY_ALREADY_BOUND' ? (english ? 'This login is already linked to another account.' : '该登录方式已绑定其他账号。')
        : (english ? 'Could not complete this action. Please try again.' : '操作未完成，请重试。'));
    } finally { setWorking(false); }
  };
  const signOut = async (discardUnsaved=false):Promise<boolean> => {
    if(!session)return false;setError('');
    if(!discardUnsaved)try{await ownerDrafts?.flushAll?.();}catch{
      Alert.alert(english?'The local draft has not saved':'本机草稿尚未保存',english?'Keep editing to retry, or leave without saving the latest edits.':'可继续编辑并重试，或放弃最新未保存的修改后退出。',[
        {text:english?'Keep editing':'继续编辑',style:'cancel'},
        {text:english?'Leave without saving':'放弃最新修改并退出',style:'destructive',onPress:()=>{void signOut(true).then(()=>setAccountOpen(false));}},
      ]);return false;
    }
    await session.signOut();setTab('rest');return true;
  };
  const copy = english ? {
    unavailable: 'Service connection is not configured yet.', greeting: 'A little room to pause.',
    signIn: 'Sign in', emailSignIn: 'Sign in with email', soon: 'Sign in to continue in your own space.',
    switch: 'Switch account', signOut: 'Sign out', account: 'Signed in as',
  } : {
    unavailable: '服务连接尚未配置。', greeting: '给自己留一小会儿。',
    signIn: '登录', emailSignIn: '用邮箱登录', soon: '登录后，继续属于你的此间。',
    switch: '切换账号', signOut: '退出登录', account: '当前账号',
  };
  const accountName = identity.profile?.displayName || identity.profile?.email?.split('@')[0]
    || (english ? 'Your space' : '我的此间');
  return <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}><SafeAreaView style={[styles.root, desktop && styles.desktop]} edges={keyboardVisible ? ['top'] : ['top', 'bottom']}>
    {!seated && !writingFocused && <View style={styles.header}><View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}><Image source={require('../../assets/invelle-icon.png')} style={{ width: 42, height: 42 }} /><View><Text style={styles.brand}>此间</Text><Text style={styles.brandEn}>INVELLE</Text></View></View>
      <View style={styles.headerActions}>
        <InstallApp en={english} />
        <Pressable onPress={() => setEnglish(!english)} accessibilityRole="button"><Text style={styles.language}>{english ? '中文' : 'EN'}</Text></Pressable>
        {identity.profile && <Pressable style={styles.accountChip} onPress={() => setAccountOpen(true)} accessibilityRole="button" accessibilityLabel={english ? 'My account' : '我的账户'}>
          <View style={styles.avatar}><Text style={styles.avatarText}>{Array.from(accountName)[0]?.toUpperCase()}</Text></View>
          <Text style={styles.chipName} numberOfLines={1}>{accountName}</Text>
        </Pressable>}
      </View></View>}
    <View style={[styles.body, desktop && tab !== 'rest' && styles.desktopBody]}>
      {<View style={{flex:1,display:tab === 'rest' ? 'flex' : 'none'}}><RestSpace localDrafts={ownerDrafts} active={tab === 'rest'} slogans={homeSlogans} english={english} onSeated={setSeated} onWrite={() => setTab('notes')} onSignIn={signIn} config={config} session={session} ownerId={identity.profile?.userId || null} identityGeneration={identity.generation} onChat={() => setTab('chat')} onCreate={() => setTab('create')} /></View>}
      {session && identity.profile ? (['chat', 'create', 'notes'] as const).map(space => <View key={`${identity.generation}-${space}`} style={{ flex: 1, display: tab === space ? 'flex' : 'none' }}>{space === 'create' ? <CommunitySpace session={session} english={english} active={tab === space} onFocusChange={setWritingFocused} /> : <CoreSpace localDrafts={ownerDrafts} session={session} space={space} english={english} ready={space === 'chat' ? capabilities.chat : capabilities.notes} active={tab === space} onFocusChange={setWritingFocused} onRest={() => setTab('rest')} voiceEntryRequest={space === 'notes' ? pendingVoiceEntryRequest : 0} onVoiceEntryRequestConsumed={space === 'notes' ? request => setVoiceEntryRequestConsumed(current => Math.max(current, request)) : undefined} />}</View>) : tab !== 'rest' && <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.section}>{tabs.find(item => item.key === tab)?.[english ? 'en' : 'zh']}</Text>
        <Text style={styles.copy}>{copy.greeting}</Text>
        <Text style={styles.detail}>{copy.soon}</Text>
        {config && <Pressable style={styles.action} onPress={signIn} disabled={identity.busy}><Text style={styles.actionText}>{copy.signIn}</Text></Pressable>}
      </ScrollView>}

    </View>
    {!identity.profile && !seated && <View style={styles.account}>
      {!config ? <Text style={styles.notice}>{copy.unavailable}</Text> :
        <Pressable accessibilityRole="button" onPress={signIn} disabled={identity.busy}><Text style={styles.accountAction}>{identity.busy ? '…' : copy.signIn}</Text></Pressable>}
    </View>}
    {!!error && <Text style={[styles.error, { paddingHorizontal: 22 }]}>{error}</Text>}
    <Modal visible={accountOpen} animationType="slide" transparent onRequestClose={() => setAccountOpen(false)}>
      <View style={styles.modalBackdrop}><View style={[styles.loginPanel, desktop && styles.desktopPanel]}>
        <Text style={styles.section}>{english ? 'My account' : '我的账户'}</Text>
        <View style={styles.accountIdentity}><View style={styles.profileAvatar}><Text style={styles.profileInitial}>{Array.from(accountName)[0]?.toUpperCase()}</Text></View>
          <View style={{ flex: 1 }}><Text style={styles.profileName}>{accountName}</Text>
            {!!(identity.profile?.accountEmail || identity.profile?.email) && <Text style={styles.accountEmail}>{identity.profile?.accountEmail || identity.profile?.email}</Text>}
          </View></View>
        <Pressable style={styles.loginButton} onPress={async () => { setAccountOpen(false); await manageLogin(); }}><Text style={styles.loginButtonText}>{english ? 'Login methods' : '登录方式'}</Text></Pressable>
        <Pressable style={styles.loginButton} onPress={async () => { if(await signOut()){setAccountOpen(false);signIn();} }}><Text style={styles.loginButtonText}>{copy.switch}</Text></Pressable>
        <Pressable style={styles.loginButton} onPress={async () => { if(await signOut())setAccountOpen(false); }}><Text style={styles.loginButtonText}>{copy.signOut}</Text></Pressable>
        <Pressable onPress={() => setAccountOpen(false)}><Text style={styles.accountAction}>{english ? 'Close' : '关闭'}</Text></Pressable>
      </View></View>
    </Modal>
    <Modal visible={loginOpen} animationType="slide" transparent onRequestClose={() => setLoginOpen(false)}>
      <View style={styles.modalBackdrop}><View style={[styles.loginPanel, desktop && styles.desktopPanel]}>
        <Text style={styles.section}>{manage ? (english ? 'Login methods' : '登录方式') : (english ? 'Sign in' : '登录')}</Text>
        {Platform.OS === 'web' && !manage && config && session && <GoogleWebLogin config={config} session={session} english={english} active={loginOpen} onSuccess={() => { setError(''); setLoginOpen(false); }} onError={() => setError(english ? 'Google sign-in did not finish. Please retry.' : 'Google 登录未完成，请重试。')} />}
        {Platform.OS === 'web' && manage && <Text style={styles.notice}>{english ? 'Manage linked login methods in the iPhone app.' : '已绑定的登录方式可在 iPhone App 中管理。'}</Text>}
        {(Platform.OS === 'web' ? [] : ['google', 'facebook', 'apple'] as ExternalProvider[]).map(provider => {
          const bound = methods.includes(provider);
          const configured = externalConfigured(provider);
          const enabled = configured || (manage && bound);
          const disabled = !enabled || working || identity.busy;
          const AppleButton = configured && provider === 'apple' ? (require('expo-apple-authentication') as typeof import('expo-apple-authentication')).AppleAuthenticationButton : null;
          const GoogleButton = configured && provider === 'google' ? (require('@react-native-google-signin/google-signin') as typeof import('@react-native-google-signin/google-signin')).GoogleSigninButton : null;
          const name = provider === 'google' ? 'Google' : provider === 'facebook' ? 'Facebook' : 'Apple';
          return <View key={provider}>
            {!manage && AppleButton ? <AppleButton buttonType={0} buttonStyle={2} cornerRadius={10} style={{ height: 48, width: '100%', marginVertical: 8, opacity: disabled ? 0.5 : 1 }} onPress={() => { if (!disabled) void providerAction(provider); }} /> : !manage && GoogleButton ? <GoogleButton size={1} color="light" style={{ width: '100%', height: 48 }} disabled={disabled} onPress={() => providerAction(provider)} /> : <Pressable accessibilityRole="button" style={[styles.loginButton, !enabled && styles.disabled]} disabled={disabled}
              onPress={() => providerAction(provider, manage && bound)}>
              <Text style={styles.loginButtonText}>{manage ? `${bound ? (english ? 'Unlink' : '解绑') : (english ? 'Link' : '绑定')} ${name}` : (english ? `Continue with ${name}` : `使用 ${name} 登录`)}</Text>
            </Pressable>}
            {!configured && <Text style={styles.notice}>{english ? `${name} is not configured for this local build.` : `本地版本尚未配置 ${name}。`}</Text>}
          </View>;
        })}
        {Platform.OS === 'web' && <Text style={styles.notice}>{english ? 'Your session stays in this page. Sign in again after reloading.' : '登录仅在当前页面保留，刷新后需要重新登录。'}</Text>}
        {!manage && <Pressable accessibilityRole="button" style={styles.loginButton} disabled={working || identity.busy} onPress={async () => { await emailSignIn(); setLoginOpen(false); }}><Text style={styles.loginButtonText}>{copy.emailSignIn}</Text></Pressable>}
        {manage && <Text style={styles.detail}>{english ? 'Linking requires a recent sign-in. Matching emails never merge accounts automatically.' : '绑定需要近期登录验证；相同邮箱不会自动合并账号。'}</Text>}
        {!!error && <Text style={styles.error}>{error}</Text>}
        <Pressable onPress={() => setLoginOpen(false)}><Text style={styles.accountAction}>{english ? 'Close' : '关闭'}</Text></Pressable>
      </View></View>
    </Modal>
    {!keyboardVisible && !seated && !writingFocused && <View style={styles.nav}>{tabs.map(item => <Pressable key={item.key} style={styles.navItem} onPress={() => setTab(item.key)} onLongPress={item.key === 'notes' ? () => { setTab('notes'); if (identity.profile && capabilities.notes) setVoiceEntryRequestId(value => value + 1); } : undefined} delayLongPress={500} accessibilityRole="tab" accessibilityLabel={english ? item.en : item.zh} accessibilityHint={item.key === 'notes' ? (english ? 'Long press to start a voice note' : '长按直接开始语音记录') : undefined} accessibilityState={{ selected: item.key === tab }}>
      <TabIcon tab={item.key} selected={tab === item.key} />
      <Text style={[styles.navText, tab === item.key && styles.selected]}>{english ? item.en : item.zh}</Text>
    </Pressable>)}</View>}
    {!appForeground&&<View accessibilityViewIsModal style={[StyleSheet.absoluteFill,{backgroundColor:'#f7f5ec',alignItems:'center',justifyContent:'center'}]}><Text>{english?'Invelle paused':'此间已暂停'}</Text></View>}
  </SafeAreaView></KeyboardAvoidingView>;
}
const styles = StyleSheet.create({
  desktop: { width: '100%', maxWidth: 1440, alignSelf: 'center', borderLeftWidth: 1, borderRightWidth: 1, borderColor: '#e1e5db' },
  desktopBody: { width: '100%', maxWidth: 960, alignSelf: 'center' },
  desktopPanel: { width: 480, alignSelf: 'center', borderRadius: 24, marginBottom: 48 },
  headerActions: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  accountChip: { flexDirection: 'row', alignItems: 'center', gap: 6, maxWidth: 122, padding: 5, borderRadius: 18, backgroundColor: '#e8ece3' },
  avatar: { width: 26, height: 26, borderRadius: 13, backgroundColor: '#355a45', alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: '#fff', fontSize: 12 }, chipName: { color: '#355a45', fontSize: 12, flexShrink: 1, paddingRight: 5 },
  accountIdentity: { flexDirection: 'row', alignItems: 'center', gap: 14, marginBottom: 12 },
  profileAvatar: { width: 48, height: 48, borderRadius: 24, backgroundColor: '#355a45', alignItems: 'center', justifyContent: 'center' },
  profileInitial: { color: '#fff', fontSize: 22 }, profileName: { color: '#2c4938', fontSize: 21 }, accountEmail: { color: '#738376', fontSize: 13, marginTop: 4 },
  modalBackdrop: { flex: 1, backgroundColor: '#0004', justifyContent: 'flex-end' },
  loginPanel: { backgroundColor: '#f5f4ed', padding: 28, paddingBottom: 45, borderTopLeftRadius: 24, borderTopRightRadius: 24, gap: 14 },
  loginButton: { borderWidth: 1, borderColor: '#a8b4a5', borderRadius: 8, padding: 14, alignItems: 'center' },
  loginButtonText: { color: '#2c4938', fontSize: 16 }, disabled: { opacity: 0.45 },
  root: { flex: 1, backgroundColor: '#f5f4ed' }, header: { height: 72, paddingHorizontal: 25, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 24, letterSpacing: 7, color: '#2c4938' }, brandEn: { fontSize: 9, letterSpacing: 3, color: '#738376', marginTop: 2 },
  language: { color: '#355c48', fontSize: 14, padding: 12 }, body: { flex: 1 },
  content: { flexGrow: 1, padding: 32, justifyContent: 'center' }, section: { fontSize: 30, color: '#2b4636', marginBottom: 18 },
  copy: { fontSize: 21, color: '#48604d', marginBottom: 12 }, detail: { color: '#748174', lineHeight: 24 },
  action: { backgroundColor: '#355a45', alignSelf: 'flex-start', borderRadius: 24, paddingVertical: 12, paddingHorizontal: 20, marginTop: 28 },
  actionText: { color: '#fff' }, account: { paddingHorizontal: 22, paddingVertical: 9, minHeight: 40, backgroundColor: '#f8f8f2' },
  accountRow: { flexDirection: 'row', alignItems: 'center', gap: 12, flexWrap: 'wrap' }, accountLabel: { color: '#66756a', fontSize: 11 },
  accountAction: { color: '#365b48', fontSize: 12 }, notice: { color: '#886b43', fontSize: 12 }, error: { color: '#a33f30', fontSize: 12 },
  nav: { height: 70, flexDirection: 'row', borderTopWidth: 1, borderTopColor: '#d9dfd5', backgroundColor: '#f8f8f3' },
  navItem: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 2 },
  navText: { fontSize: 12, color: '#7b877d' }, selected: { color: '#2f513d' },
});
