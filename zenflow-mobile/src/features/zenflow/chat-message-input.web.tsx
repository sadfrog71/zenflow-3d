import { useEffect, useRef } from 'react';
import { TextInput, type TextInputProps } from 'react-native';
import { handleChatEnter } from './chat-enter';
export function ChatMessageInput({ onSend, sendDisabled, ...props }: TextInputProps & { onSend: () => void; sendDisabled: boolean }) {
  const host = useRef<HTMLDivElement>(null);
  const latest = useRef({ onSend, sendDisabled }); latest.current = { onSend, sendDisabled };
  useEffect(() => {
    const element = host.current;
    if (!element) return;
    let composing = false, justComposed = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const start = () => { composing = true; justComposed = false; clearTimeout(timer); };
    const end = () => {
      composing = false; justComposed = true;
      // Some Mac IMEs end composition before the Enter keydown in the same task.
      clearTimeout(timer); timer = setTimeout(() => { justComposed = false; }, 0);
    };
    const keydown = (event: KeyboardEvent) => {
      handleChatEnter(event, latest.current.sendDisabled, latest.current.onSend, composing || justComposed);
    };
    element.addEventListener('compositionstart', start, true);
    element.addEventListener('compositionend', end, true);
    element.addEventListener('keydown', keydown, true);
    return () => {
      clearTimeout(timer);
      element.removeEventListener('compositionstart', start, true);
      element.removeEventListener('compositionend', end, true);
      element.removeEventListener('keydown', keydown, true);
    };
  }, []);
  return <div ref={host} style={{ display: 'contents' }}><TextInput {...props} /></div>;
}
