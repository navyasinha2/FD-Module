/* Apply before first paint; theme choice is presentation-only and shared across pages. */
(function () {
  function apply(theme, save) {
    theme = theme === 'light' ? 'light' : 'dark';
    document.documentElement.dataset.theme = theme;
    if (save) { try { localStorage.setItem('fd-theme', theme); } catch {} }
    document.querySelectorAll('[data-theme-choice]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.themeChoice === theme)));
  }
  let saved; try { saved = localStorage.getItem('fd-theme'); } catch {}
  apply(saved || 'dark', false);
  window.FDTheme = apply;
  window.addEventListener('storage', event => { if (event.key === 'fd-theme') apply(event.newValue, false); });
})();
