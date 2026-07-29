// keyboard.js — Global keyboard shortcut manager for DMHelper
// Dispatches custom events that other modules listen to.

(function () {
  'use strict';

  const SHORTCUTS = {
    'k': { ctrl: true, shift: false, event: 'command-palette-toggle' },
    'r': { ctrl: true, shift: false, event: 'dice-roller-toggle' },
  };

  function hasVisibleModal() {
    return Array.from(document.querySelectorAll('[aria-modal="true"]')).some(modal => {
      const style = window.getComputedStyle(modal);
      return !modal.hidden && style.display !== 'none' && style.visibility !== 'hidden'
        && modal.getClientRects().length > 0;
    });
  }

  document.addEventListener('keydown', (e) => {
    if (e.target.closest?.('input, select, textarea, [contenteditable]')) return;
    if (hasVisibleModal()) return;

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
