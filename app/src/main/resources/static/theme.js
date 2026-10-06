// app/src/main/resources/static/theme.js — applies the saved theme before first paint (no inline script, CSP-friendly).
try {
  const t = localStorage.getItem("weekend.theme");
  if (t === "light" || t === "dark") document.documentElement.dataset.theme = t;
} catch { /* storage blocked: follow the system theme */ }
