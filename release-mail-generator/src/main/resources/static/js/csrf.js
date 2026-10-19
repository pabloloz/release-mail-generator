/* ── CSRF — adjunta automáticamente el token a toda petición fetch() mutante ──
 *
 * Lee el token y el nombre de cabecera desde las meta etiquetas que cada
 * plantilla Thymeleaf expone en su <head>:
 *   <meta name="_csrf" th:content="${_csrf.token}"/>
 *   <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 *
 * Así ningún fetch() del resto de la aplicación necesita conocer ni manejar
 * el token manualmente: basta con que este script se cargue antes de que se
 * invoque cualquier fetch (ya que `window.fetch` se reemplaza una sola vez,
 * de forma síncrona, al cargar la página).
 */
(function () {
    'use strict';

    function getMeta(name) {
        var el = document.querySelector('meta[name="' + name + '"]');
        return el ? el.getAttribute('content') : null;
    }

    var token = getMeta('_csrf');
    var header = getMeta('_csrf_header');
    if (!token || !header) return; // página pública sin sesión (ej. /login)

    var MUTATING_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE'];
    var originalFetch = window.fetch;

    window.fetch = function (input, init) {
        init = init || {};
        var method = (init.method || 'GET').toUpperCase();
        if (MUTATING_METHODS.indexOf(method) !== -1) {
            var url = typeof input === 'string' ? input : (input && input.url) || '';
            var isAbsolute = /^([a-z][a-z0-9+.-]*:)?\/\//i.test(url);
            var isSameOrigin = !isAbsolute || url.indexOf(window.location.origin) === 0;
            if (isSameOrigin) {
                var headers = new Headers(init.headers || {});
                if (!headers.has(header)) headers.set(header, token);
                init = Object.assign({}, init, { headers: headers });
            }
        }
        return originalFetch.call(this, input, init);
    };
})();
