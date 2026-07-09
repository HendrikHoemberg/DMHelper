// keyboard.js — Global keyboard shortcut manager for DMHelper
// Dispatches custom events that other modules listen to.

(function () {
  'use strict';

  const SHORTCUTS = {
    'd': { ctrl: true, shift: true, event: 'dm-mode-toggle' },
    'k': { ctrl: true, shift: false, event: 'command-palette-toggle' },
  };

  document.addEventListener('keydown', (e) => {
    if (e.target.closest?.('input, select, textarea, [contenteditable]')) return;

    for (const [key, config] of Object.entries(SHORTCUTS)) {
      const ctrlMatch = config.ctrl ? (e.ctrlKey || e.metaKey) : !(e.ctrlKey || e.metaKey);
      const shiftMatch = config.shift ? e.shiftKey : !e.shiftKey;
      if (ctrlMatch && shiftMatch && e.key.toLowerCase() === key) {
        e.preventDefault();
        window.dispatchEvent(new CustomEvent(config.event));
        return;
      }
    }
  });
})();
