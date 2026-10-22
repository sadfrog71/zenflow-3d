// Active-time clock: background time is never carried into the water garden.
function createPondClock() {
  let elapsed = 0, last = null, next = 0;
  const milestones = [60, 180, 300];
  return {
    step(now, running) {
      if (!running) { last = null; return null; }
      if (last !== null) elapsed += Math.max(0, (now - last) / 1000);
      last = now;
      // Count foreground wall time independently of render FPS. A stalled frame
      // consumes old milestones and returns only the latest, never a queued burst.
      let event = null;
      while (next < milestones.length && elapsed >= milestones[next]) event = ['splash', 'together', 'leaf'][next++];
      return event;
    },
    get elapsed() { return elapsed; },
  };
}
module.exports = { createPondClock };
