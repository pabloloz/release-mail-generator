/* ── Global Search ────────────────────────────────────────────────────────── */
(function () {
    'use strict';

    var _timer = null;
    var _activeIdx = -1;
    var _flatItems = [];

    // Keyboard shortcut: Ctrl+K
    document.addEventListener('keydown', function (e) {
        if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
            // Don't intercept if user is inside the rich editor
            var editor = document.getElementById('uatRichEditor');
            if (editor && (editor === document.activeElement || editor.contains(document.activeElement))) return;
            e.preventDefault();
            var input = document.getElementById('globalSearchInput');
            if (input) { input.focus(); input.select(); }
        }
        if (e.key === 'Escape') closeGlobalSearch();
    });

    window.onGlobalSearch = function () {
        clearTimeout(_timer);
        _timer = setTimeout(doSearch, 200);
    };

    window.onGlobalSearchFocus = function () {
        var input = document.getElementById('globalSearchInput');
        var kbd = document.getElementById('gsKbd');
        if (kbd) kbd.style.display = 'none';
        if (input && input.value.trim().length >= 2) doSearch();
    };

    window.closeGlobalSearch = function () {
        var results = document.getElementById('gsResults');
        var overlay = document.getElementById('gsOverlay');
        var kbd = document.getElementById('gsKbd');
        if (results) results.classList.remove('open');
        if (overlay) overlay.classList.remove('open');
        if (kbd) kbd.style.display = '';
        _activeIdx = -1;
    };

    async function doSearch() {
        var input = document.getElementById('globalSearchInput');
        var results = document.getElementById('gsResults');
        var overlay = document.getElementById('gsOverlay');
        if (!input || !results) return;

        var q = input.value.trim();
        if (q.length < 2) {
            results.classList.remove('open');
            overlay.classList.remove('open');
            return;
        }

        try {
            var resp = await fetch('/api/search?q=' + encodeURIComponent(q));
            if (!resp.ok) return;
            var data = await resp.json();
            renderResults(data, q);
        } catch (e) {
            results.innerHTML = '<div class="gs-empty">Error al buscar.</div>';
            results.classList.add('open');
            overlay.classList.add('open');
        }
    }

    function renderResults(data, query) {
        var results = document.getElementById('gsResults');
        var overlay = document.getElementById('gsOverlay');
        if (!results) return;

        var groups = data.groups || {};
        var keys = Object.keys(groups);
        _flatItems = [];

        if (keys.length === 0) {
            results.innerHTML = '<div class="gs-empty">No se encontraron resultados para "<strong>' + esc(query) + '</strong>"</div>';
            results.classList.add('open');
            overlay.classList.add('open');
            return;
        }

        var html = '';
        keys.forEach(function (groupName) {
            var items = groups[groupName];
            html += '<div class="gs-group-header">' + esc(groupName) + ' <span style="opacity:.5;">(' + items.length + ')</span></div>';
            items.forEach(function (item) {
                var idx = _flatItems.length;
                _flatItems.push(item);
                var title = highlightMatch(esc(item.title || ''), query);
                var sub = esc(item.subtitle || '');
                html += '<div class="gs-item" data-idx="' + idx + '" onmouseenter="gsHover(' + idx + ')" onclick="gsSelect(' + idx + ')">'
                    + '<span class="gs-item-icon">' + (item.icon || '📄') + '</span>'
                    + '<div class="gs-item-text">'
                    + '<div class="gs-item-title">' + title + '</div>'
                    + (sub ? '<div class="gs-item-sub">' + sub + '</div>' : '')
                    + '</div></div>';
            });
        });

        html += '<div class="gs-footer">' + data.total + ' resultado' + (data.total !== 1 ? 's' : '') + ' · ↑↓ navegar · Enter seleccionar · Esc cerrar</div>';

        results.innerHTML = html;
        results.classList.add('open');
        overlay.classList.add('open');
        _activeIdx = -1;
    }

    // Keyboard navigation
    document.addEventListener('keydown', function (e) {
        var results = document.getElementById('gsResults');
        if (!results || !results.classList.contains('open')) return;
        if (_flatItems.length === 0) return;

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            _activeIdx = Math.min(_activeIdx + 1, _flatItems.length - 1);
            updateActive();
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            _activeIdx = Math.max(_activeIdx - 1, 0);
            updateActive();
        } else if (e.key === 'Enter' && _activeIdx >= 0) {
            e.preventDefault();
            gsSelect(_activeIdx);
        }
    });

    function updateActive() {
        var results = document.getElementById('gsResults');
        if (!results) return;
        results.querySelectorAll('.gs-item').forEach(function (el) { el.classList.remove('gs-active'); });
        var active = results.querySelector('[data-idx="' + _activeIdx + '"]');
        if (active) {
            active.classList.add('gs-active');
            active.scrollIntoView({ block: 'nearest' });
        }
    }

    window.gsHover = function (idx) { _activeIdx = idx; updateActive(); };

    window.gsSelect = function (idx) {
        var item = _flatItems[idx];
        if (!item) return;
        closeGlobalSearch();

        if (item.url && item.url !== '') {
            // Navigate to URL (RFC view, etc.)
            window.location.href = item.url;
        } else if (item.action === 'view-history' && item.id) {
            // Open in history viewer
            if (typeof showPane === 'function') showPane('history', null);
            setTimeout(function () {
                if (typeof viewVersion === 'function') viewVersion(item.id);
            }, 200);
        }

        document.getElementById('globalSearchInput').value = '';
    };

    function highlightMatch(text, query) {
        if (!query) return text;
        var escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        return text.replace(new RegExp('(' + escaped + ')', 'gi'), '<mark>$1</mark>');
    }

    function esc(s) {
        if (!s) return '';
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
})();
