import { useEffect, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { prepareGoogleLogin, externalConfigured } from '@/core/zenflow/external-login.web';
import type { GoogleLoginProps } from './google-login';

type GoogleIdentity = {
  initialize(options: { client_id: string; nonce: string; auto_select: boolean; callback(response: { credential?: string }): void }): void;
  renderButton(element: HTMLElement, options: { theme: string; size: string; text: string; locale: string; width: number }): void;
};
let loading: Promise<GoogleIdentity> | null = null;
function loadGoogle(): Promise<GoogleIdentity> {
  const identity = () => (window as unknown as { google?: { accounts?: { id?: GoogleIdentity } } }).google?.accounts?.id;
  if (identity()) return Promise.resolve(identity()!);
  if (loading) return loading;
  loading = new Promise<GoogleIdentity>((resolve, reject) => {
    const script = document.createElement('script'); script.src = 'https://accounts.google.com/gsi/client'; script.async = true;
    const timer = setTimeout(() => { script.remove(); reject(new Error('GOOGLE_LOAD_FAILED')); }, 15000);
    script.onload = () => { clearTimeout(timer); if (identity()) resolve(identity()!); else reject(new Error('GOOGLE_LOAD_FAILED')); };
    script.onerror = () => { clearTimeout(timer); script.remove(); reject(new Error('GOOGLE_LOAD_FAILED')); };
    document.head.appendChild(script);
  }).catch(error => { loading = null; throw error; });
  return loading;
}
export function GoogleWebLogin({ config, session, english, active, onSuccess, onError }: GoogleLoginProps) {
  const host = useRef<HTMLDivElement>(null);
  const callbacks = useRef({ onSuccess, onError }); callbacks.current = { onSuccess, onError };
  const [status, setStatus] = useState('loading'), [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!active || !externalConfigured('google')) return;
    let alive = true, submitted = false;
    const generation = session.snapshot().generation;
    setStatus('loading');
    const expiry = setTimeout(() => { if (alive && !submitted) setAttempt(value => value + 1); }, 240000);
    Promise.all([loadGoogle(), prepareGoogleLogin(config)]).then(([google, transaction]) => {
      if (!alive || !host.current || session.snapshot().generation !== generation) return;
      google.initialize({ client_id: process.env.EXPO_PUBLIC_ZENFLOW_GOOGLE_WEB_CLIENT_ID!, nonce: transaction.nonce, auto_select: false,
        callback: response => {
          if (!alive || submitted || !response.credential || session.snapshot().generation !== generation) return;
          submitted = true; setStatus('signing-in');
          void session.signIn(async () => {
            const signingGeneration = session.snapshot().generation;
            return transaction.complete(response.credential!, () => alive && session.snapshot().generation === signingGeneration);
          }).then(() => { if (alive) callbacks.current.onSuccess(); }).catch(() => {
            if (alive) { setStatus('error'); callbacks.current.onError(); }
          });
        } });
      host.current.replaceChildren();
      google.renderButton(host.current, { theme: 'outline', size: 'large', text: 'continue_with', locale: english ? 'en' : 'zh_CN', width: Math.min(300, Math.max(200, window.innerWidth - 64)) });
      setStatus('ready');
    }).catch(() => { if (alive) setStatus('error'); });
    return () => { alive = false; clearTimeout(expiry); host.current?.replaceChildren(); };
  }, [active, config, session, english, attempt]);
  if (!externalConfigured('google')) return <Text>{english ? 'Google sign-in is not configured.' : 'Google 登录尚未配置。'}</Text>;
  return <View style={{ alignItems: 'center', gap: 10 }}>
    <div ref={host} style={{ display: status === 'ready' ? 'block' : 'none', minHeight: 44 }} />
    {status === 'loading' && <Text style={{ color: '#738376' }}>{english ? 'Preparing Google sign-in…' : '正在准备 Google 登录…'}</Text>}
    {status === 'signing-in' && <Text>{english ? 'Signing in…' : '正在登录…'}</Text>}
    {status === 'error' && <Pressable accessibilityRole="button" onPress={() => setAttempt(value => value + 1)}><Text style={{ color: '#355a45' }}>{english ? 'Google sign-in is unavailable. Retry' : 'Google 登录暂不可用，点击重试'}</Text></Pressable>}
  </View>;
}
