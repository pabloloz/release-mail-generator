/**
 * Utilidades para escribir en localStorage sin perder datos en silencio.
 * Las usan plantillas (templates.js) y borrador (autosave.js).
 */
(function () {
    'use strict';

    var DATA_IMG = /^data:image\//i;
    var IMG_TAG = /<img\b[^>]*?\ssrc\s*=\s*(["'])data:image\/[^"']*\1[^>]*>/gi;

    function isQuotaError(e) {
        return !!e && (e.name === 'QuotaExceededError' || e.name === 'NS_ERROR_DOM_QUOTA_REACHED'
            || e.code === 22 || e.code === 1014);
    }

    /** Escribe y devuelve null si salió bien, o el error si falló. */
    function write(key, value) {
        try { localStorage.setItem(key, value); return null; }
        catch (e) { return e || new Error('localStorage no disponible'); }
    }

    /** Copia del valor sin imágenes (data URIs y <img> embebidas). Devuelve { value, removed }. */
    function stripImages(value) {
        var removed = 0;
        function walk(v) {
            if (typeof v === 'string') {
                if (DATA_IMG.test(v)) { removed++; return ''; }
                if (v.indexOf('data:image/') >= 0) {
                    return v.replace(IMG_TAG, function () { removed++; return ''; });
                }
                return v;
            }
            if (Array.isArray(v)) {
                return v.filter(function (x) {
                    if (typeof x === 'string' && DATA_IMG.test(x)) { removed++; return false; }
                    return true;
                }).map(walk);
            }
            if (v && typeof v === 'object') {
                var out = {};
                Object.keys(v).forEach(function (k) { out[k] = walk(v[k]); });
                return out;
            }
            return v;
        }
        var result = walk(value);
        return { value: result, removed: removed };
    }

    var warned = {};
    /** Muestra un aviso una sola vez por sesión y tipo. */
    function warnOnce(kind, message) {
        if (warned[kind]) return;
        warned[kind] = true;
        if (typeof showToast === 'function') showToast(message, 'error', 7000);
    }

    window.RnqStorage = { isQuotaError: isQuotaError, write: write, stripImages: stripImages, warnOnce: warnOnce };
})();
