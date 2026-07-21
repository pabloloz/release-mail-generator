/* ── Dark Mode — Toggle & Persistence ────────────────────────────────────── */
(function () {
    'use strict';

    var STORAGE_KEY = 'theme-preference';

    function getStoredTheme() {
        try { return localStorage.getItem(STORAGE_KEY); } catch (e) { return null; }
    }

    function setStoredTheme(theme) {
        try { localStorage.setItem(STORAGE_KEY, theme); } catch (e) { /* ignore */ }
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        // Update toggle icon if present
        var icon = document.getElementById('theme-toggle-icon');
        if (icon) icon.textContent = theme === 'dark' ? '\u2600\uFE0F' : '\uD83C\uDF19';
    }

    function getPreferredTheme() {
        var stored = getStoredTheme();
        if (stored) return stored;
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    // Apply immediately to prevent flash
    applyTheme(getPreferredTheme());

    // Toggle function exposed globally
    window.toggleDarkMode = function () {
        var current = document.documentElement.getAttribute('data-theme') || 'light';
        var next = current === 'dark' ? 'light' : 'dark';

        // Smooth crossfade
        var style = document.createElement('style');
        style.id = 'theme-transition';
        style.textContent = '*, *::before, *::after { transition: background-color .35s ease, color .35s ease, border-color .35s ease, box-shadow .35s ease !important; }';
        document.head.appendChild(style);

        applyTheme(next);
        setStoredTheme(next);

        // Also update any iframes (e.g. RFC Técnico)
        try {
            document.querySelectorAll('iframe').forEach(function (f) {
                if (f.contentDocument) {
                    // Inject transition animation into iframe
                    var iframeStyle = f.contentDocument.createElement('style');
                    iframeStyle.id = 'theme-transition';
                    iframeStyle.textContent = '*, *::before, *::after { transition: background-color .35s ease, color .35s ease, border-color .35s ease, box-shadow .35s ease !important; }';
                    f.contentDocument.head.appendChild(iframeStyle);
                    f.contentDocument.documentElement.setAttribute('data-theme', next);
                    setTimeout(function() { iframeStyle.remove(); }, 400);
                }
            });
        } catch (e) { /* cross-origin iframe, ignore */ }

        setTimeout(function () { style.remove(); }, 400);
    };

    // Re-apply on DOM ready (for dynamic toggle icon)
    document.addEventListener('DOMContentLoaded', function () {
        applyTheme(getPreferredTheme());
    });

    // Listen for OS theme changes (only if no explicit preference stored)
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
        if (!getStoredTheme()) {
            applyTheme(e.matches ? 'dark' : 'light');
        }
    });
})();
