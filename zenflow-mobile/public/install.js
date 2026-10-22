// Capture the browser's one-use installation event before React starts.
window.addEventListener('beforeinstallprompt', event => {
  event.preventDefault();
  window.invelleInstallPrompt = event;
  window.dispatchEvent(new Event('invelle-install-ready'));
});
window.addEventListener('appinstalled', () => { window.invelleInstallPrompt = null; });
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js', { scope: '/', updateViaCache: 'none' }).catch(() => {});
  });
}
