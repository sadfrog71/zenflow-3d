type EnterEvent = Pick<KeyboardEvent, 'key' | 'shiftKey' | 'isComposing' | 'keyCode' | 'repeat' | 'preventDefault'> & { code?: string };
export function handleChatEnter(event: EnterEvent, disabled: boolean, send: () => void, composing = false): void {
  const enter = event.key === 'Enter' || event.key === 'Return' || event.code === 'Enter' || event.code === 'NumpadEnter';
  if (!enter || event.shiftKey || event.isComposing || composing) return;
  event.preventDefault();
  if (!disabled && !event.repeat) send();
}
