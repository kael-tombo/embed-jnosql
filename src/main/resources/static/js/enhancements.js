/* ============================================================
   Legacy entrypoint — retained for backward compatibility.
   Console application logic now lives in /js/console.js.
   This file only registers safe, optional progressive
   enhancements. It performs no DOM mutation at parse time and
   references no removed elements, so it cannot break the new UI.
   ============================================================ */
'use strict';

(function () {
  function safeInit(fn) {
    try { fn(); } catch (e) {
      if (window.console && console.warn) console.warn('[embedjnosql-enhancements]', e && e.message);
    }
  }

  safeInit(function markLoaded() {
    document.documentElement.dataset.enhancements = 'v2-compatible';
  });
})();
