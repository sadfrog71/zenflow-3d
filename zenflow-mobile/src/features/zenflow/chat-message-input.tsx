import { TextInput, type TextInputProps } from 'react-native';
export function ChatMessageInput({ onSend: _onSend, sendDisabled: _disabled, ...props }: TextInputProps & { onSend: () => void; sendDisabled: boolean }) {
  return <TextInput {...props} />;
}
