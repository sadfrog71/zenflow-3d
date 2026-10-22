export function browserSpeechError(code: string | undefined, en: boolean): string {
  const messages: Record<string, [string, string]> = {
    network: ['无法连接浏览器的语音识别服务，请检查网络后重试，或使用系统听写输入。', 'Cannot reach the browser speech service. Check your connection or use system dictation.'],
    'not-allowed': ['麦克风或语音识别权限被阻止。请在浏览器的网站设置及系统隐私设置中允许麦克风，然后重试。', 'Microphone or speech access was blocked. Allow microphone access in browser site settings and system privacy settings, then retry.'],
    'service-not-allowed': ['浏览器不允许使用语音识别服务，请检查浏览器设置，或使用系统听写输入。', 'The browser blocked its speech service. Check browser settings or use system dictation.'],
    'audio-capture': ['无法读取麦克风，请检查输入设备、系统权限及麦克风是否被其他应用占用。', 'Cannot capture microphone audio. Check your input device, system permissions, and other apps using it.'],
    'no-speech': ['没有检测到语音。请检查麦克风是否静音，开始后再说话。', 'No speech was detected. Check that your microphone is not muted, then speak after starting.'],
    'language-not-supported': ['浏览器语音服务暂不支持当前语言，请使用系统听写或文字输入。', 'The speech service does not support this language. Use system dictation or type instead.'],
    SPEECH_UNAVAILABLE: ['当前浏览器不支持语音识别，请使用系统听写或文字输入。', 'Speech recognition is unavailable in this browser. Use system dictation or type instead.'],
    aborted: ['语音识别已中断，请重试。', 'Speech recognition was interrupted. Please retry.'],
  };
  const key = code === 'NotAllowedError' || code === 'SecurityError' ? 'not-allowed' : code;
  return messages[key || '']?.[en ? 1 : 0] ?? (en ? 'Speech recognition failed. Please retry or use system dictation.' : '语音识别暂不可用，请重试或使用系统听写输入。');
}
