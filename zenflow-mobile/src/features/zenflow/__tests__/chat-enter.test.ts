import { handleChatEnter } from '../chat-enter';
const key = (extra = {}) => ({ key: 'Enter', shiftKey: false, isComposing: false, keyCode: 13, repeat: false, preventDefault: jest.fn(), ...extra });
test('Enter sends once without inserting a newline', () => {
  const event = key(), send = jest.fn(); handleChatEnter(event, false, send);
  expect(send).toHaveBeenCalledTimes(1); expect(event.preventDefault).toHaveBeenCalled();
});
test.each([{shiftKey:true}, {isComposing:true}, {key:'a'}])('newline and composition keep native behavior: %j', extra => {
  const event = key(extra), send = jest.fn(); handleChatEnter(event, false, send);
  expect(send).not.toHaveBeenCalled(); expect(event.preventDefault).not.toHaveBeenCalled();
});
test.each([true, false])('disabled or held Enter cannot send: %s', disabled => {
  const event = key({repeat:!disabled}), send = jest.fn(); handleChatEnter(event, disabled, send);
  expect(send).not.toHaveBeenCalled(); expect(event.preventDefault).toHaveBeenCalled();
});

test('Mac IME legacy code 229 does not turn a finished Enter into a newline', () => {
  const event = key({ keyCode: 229 }), send = jest.fn(); handleChatEnter(event, false, send, false);
  expect(send).toHaveBeenCalledTimes(1); expect(event.preventDefault).toHaveBeenCalled();
});
test('composition lifecycle guards confirmation even when native isComposing is false', () => {
  const event = key({ keyCode: 229 }), send = jest.fn(); handleChatEnter(event, false, send, true);
  expect(send).not.toHaveBeenCalled(); expect(event.preventDefault).not.toHaveBeenCalled();
});

test.each([{key:'Return'},{key:'Unidentified',code:'NumpadEnter'}])('physical return keys send: %j', extra=>{
 const event=key(extra),send=jest.fn();handleChatEnter(event,false,send);expect(send).toHaveBeenCalledTimes(1);
});
