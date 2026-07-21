/* ── Favorites — Pin documents for quick access ──────────────────────────── */
(function () {
    'use strict';

    var STORAGE_KEY = 'rnq-favorites';

    function loadFavorites() {
        try { return JSON.parse(localStorage.getItem(STORAGE_KEY)) || []; }
        catch (e) { return []; }
    }
    function saveFavorites(list) {
        try { localStorage.setItem(STORAGE_KEY, JSON.stringify(list)); } catch (e) {}
    }

    window.toggleFavorite = function (type, id, title, url, icon) {
        var favs = loadFavorites();
        var idx = favs.findIndex(function (f) { return f.id === id && f.type === type; });
        if (idx >= 0) {
            favs.splice(idx, 1);
            showToast('Removido de favoritos', 'success');
        } else {
            favs.unshift({ type: type, id: id, title: title, url: url || '', icon: icon || '⭐', addedAt: new Date().toISOString() });
            showToast('Agregado a favoritos', 'success');
        }
        saveFavorites(favs);
        renderFavorites();
    };

    window.isFavorite = function (type, id) {
        return loadFavorites().some(function (f) { return f.id === id && f.type === type; });
    };

    window.removeFavorite = function (type, id) {
        var favs = loadFavorites().filter(function (f) { return !(f.id === id && f.type === type); });
        saveFavorites(favs);
        renderFavorites();
    };

    window.renderFavorites = function () {
        var container = document.getElementById('dashFavorites');
        if (!container) return;
        var favs = loadFavorites();

        if (favs.length === 0) {
            container.innerHTML = '<div class="fav-empty">Sin favoritos. Marca documentos con ⭐ para acceder rápidamente.</div>';
            return;
        }

        container.innerHTML = favs.map(function (f) {
            return '<div class="fav-item" onclick="navigateFavorite(\'' + esc(f.type) + '\',\'' + esc(f.id) + '\',\'' + esc(f.url) + '\')">'
                + '<span class="fav-item-icon">' + (f.icon || '⭐') + '</span>'
                + '<div class="fav-item-text">'
                + '<div class="fav-item-title">' + esc(f.title) + '</div>'
                + '<div class="fav-item-type">' + esc(f.type) + '</div>'
                + '</div>'
                + '<button class="fav-item-remove" onclick="event.stopPropagation();removeFavorite(\'' + esc(f.type) + '\',\'' + esc(f.id) + '\')" title="Quitar">✕</button>'
                + '</div>';
        }).join('');
    };

    window.navigateFavorite = function (type, id, url) {
        if (type === 'RFC' && url) {
            // Open RFC inside the iframe instead of navigating away
            if (typeof showPane === 'function') {
                showPane('rfc', null);
                var frame = document.getElementById('rfcFrame');
                if (frame) frame.src = url;
            } else {
                window.location.href = url;
            }
        } else if (url) {
            window.location.href = url;
        } else if (typeof showPane === 'function' && typeof viewVersion === 'function') {
            showPane('history', null);
            setTimeout(function () { viewVersion(id); }, 200);
        }
    };

    /* ── Refresh favorite titles from server ──────────────────────────────── */
    function refreshFavoriteTitles() {
        var favs = loadFavorites();
        if (favs.length === 0) return;

        var changed = false;
        var pending = 0;

        favs.forEach(function (f, idx) {
            if (f.type !== 'RFC') return;
            // Use the stored id directly (it's the DB id passed to toggleFavorite)
            var rfcId = f.id;
            if (!rfcId) return;
            pending++;
            fetch('/rfc/' + encodeURIComponent(rfcId) + '/json')
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function (data) {
                    if (!data) return;
                    var newTitle = 'RFC ' + (data.rfcNumber || rfcId) + ' \u2014 ' + (data.changeName || '');
                    if (newTitle !== f.title) {
                        favs[idx].title = newTitle;
                        changed = true;
                    }
                })
                .catch(function () {})
                .finally(function () {
                    pending--;
                    if (pending === 0 && changed) {
                        saveFavorites(favs);
                        renderFavorites();
                    }
                });
        });
    }

    /* ── Listen for RFC iframe changes (save/edit) ─────────────────────── */
    function watchRfcFrame() {
        var frame = document.getElementById('rfcFrame');
        if (!frame) return;
        frame.addEventListener('load', function () {
            setTimeout(refreshFavoriteTitles, 500);
        });
    }

    function esc(s) {
        if (!s) return '';
        return s.replace(/'/g, "\\'").replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    // Render on load + refresh titles
    document.addEventListener('DOMContentLoaded', function () {
        renderFavorites();
        refreshFavoriteTitles();
        watchRfcFrame();
    });

    // Also expose for manual trigger
    window.refreshFavoriteTitles = refreshFavoriteTitles;
})();
