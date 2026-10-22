// A quiet ten-minute marker. Audio and seated/breathing controls remain independent.
(() => {
  const duration = 10 * 60 * 1000;
  let startedAt = null, timer = null;
  function paint() {
    const holder = document.getElementById('garden-incense');
    if (!holder || startedAt === null) return;
    const remaining = Math.max(0, 1 - (Date.now() - startedAt) / duration);
    const top = 150 - 104 * remaining;
    holder.querySelector('[data-incense-stick]').setAttribute('y1', top);
    holder.querySelector('[data-incense-ember]').setAttribute('cy', top);
    holder.querySelector('[data-incense-smoke]').setAttribute('transform', `translate(0 ${top - 46})`);
    holder.classList.toggle('burned', remaining === 0);
    holder.setAttribute('aria-valuenow', String(Math.round((1 - remaining) * 100)));
    if (remaining === 0) stop();
  }
  function stop() { if (timer !== null) clearInterval(timer); timer = null; }
  function resume() {
    paint();
    if (startedAt !== null && Date.now() - startedAt < duration && !document.hidden && timer === null) timer = setInterval(paint, 1000);
  }
  window.InvelleIncense = {
    start() { if (startedAt === null) startedAt = Date.now(); resume(); },
    stop,
    language(lang) {
      document.getElementById('garden-incense')?.setAttribute('aria-label', lang === 'en' ? 'Ten-minute incense' : '十分钟静坐香');
    },
  };
  document.addEventListener('visibilitychange', () => document.hidden ? stop() : resume());
  window.addEventListener('pagehide', stop);
})();
