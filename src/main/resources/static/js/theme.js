(() => {
  const STORAGE_KEY = 'embedjnosql.theme';
  const themes = new Set(['light', 'dark']);
  let theme = 'light';

  function applyTheme(nextTheme) {
    if (!themes.has(nextTheme)) {
      throw new TypeError(`Unsupported theme: ${nextTheme}`);
    }
    theme = nextTheme;
    document.documentElement.dataset.theme = theme;
  }

  function setTheme(nextTheme) {
    applyTheme(nextTheme);
    try {
      window.localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Keep the selected theme active even when browser storage is unavailable.
    }
  }

  try {
    const storedTheme = window.localStorage.getItem(STORAGE_KEY);
    if (themes.has(storedTheme)) {
      theme = storedTheme;
    }
  } catch {
    // The default light theme is used when browser storage cannot be read.
  }

  applyTheme(theme);
  window.EmbedJNoSQLTheme = Object.freeze({ setTheme });
})();
