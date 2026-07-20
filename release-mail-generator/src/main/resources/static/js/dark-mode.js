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
        applyTheme(next);
        setStoredTheme(next);
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
