import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef } from 'react';
import { View } from 'react-native';
import type { WebView as NativeWebView, WebViewProps } from 'react-native-webview';

// Only bundled product HTML is rendered here. The iframe has no storage/origin access.
export const WebView = forwardRef<NativeWebView, WebViewProps>(function WebView(props, ref) {
  const frame = useRef<HTMLIFrameElement>(null);
  const current = useRef(props); current.current = props;
  const channel = useMemo(() => `invelle-${Math.random().toString(36).slice(2)}`, []);
  const source = props.source as { html?: string; uri?: string } | undefined;
  const html = useMemo(() => {
    if (!source?.html) return undefined;
    const bridge = `<script>window.ReactNativeWebView={postMessage:function(data){parent.postMessage({channel:${JSON.stringify(channel)},data:data},'*')}};window.addEventListener('message',function(e){if(e.source===parent&&e.data?.channel===${JSON.stringify(channel)}&&typeof e.data.script==='string'){(0,eval)(e.data.script)}});</script>`;
    return source.html.replace(/<head[^>]*>/i, match => match + bridge);
  }, [source?.html, channel]);
  const uri = useMemo(() => {
    if (!source?.uri || !props.onMessage) return source?.uri;
    try {
      const url = new URL(source.uri, window.location.href);
      url.searchParams.set('__invelle_bridge', channel);
      return url.toString();
    } catch { return source.uri; }
  }, [source?.uri, Boolean(props.onMessage), channel]);
  useImperativeHandle(ref, () => ({ injectJavaScript(script: string) {
    frame.current?.contentWindow?.postMessage({ channel, script }, '*');
  }, postMessage(data: string) {
    frame.current?.contentWindow?.postMessage(data, '*');
  } }) as NativeWebView, [channel]);
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.source !== frame.current?.contentWindow || event.data?.channel !== channel || typeof event.data.data !== 'string') return;
      current.current.onMessage?.({ nativeEvent: { data: event.data.data } } as never);
    };
    window.addEventListener('message', receive);
    return () => window.removeEventListener('message', receive);
  }, [channel]);
  return <View style={[{ flex: 1, minHeight: 0 }, props.style]}><iframe ref={frame}
    title="Invelle interactive space" srcDoc={html} src={html ? undefined : uri}
    sandbox="allow-scripts" allow="autoplay" onLoad={() => current.current.onLoadEnd?.({} as never)}
    style={{ width: '100%', height: '100%', flex: 1, border: 0, display: 'block' }} /></View>;
});
