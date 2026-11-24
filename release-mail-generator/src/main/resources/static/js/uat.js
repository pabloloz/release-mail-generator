/* ── VoBo UAT — formulario estructurado con vista previa en vivo ─────────────
 *
 * El HTML del correo lo genera siempre el backend (POST /uat/preview para la vista previa
 * y POST /generate-uat para "Generar", que además guarda en el historial). Este script solo
 * mantiene el estado del formulario, pinta los ítems repetibles y pide la vista previa.
 *
 * Globales que otros scripts ya usan (se conservan): generateUatEmail, clearUatForm,
 * collectUatData, downloadUatExport, copyUatEmail, formatUatRfcNumber.
 * Nuevas: collectUatState / restoreUatState / uatStateIsEmpty (plantillas y borrador) y
 * uatHasRequirements (validation.js).
 */
(function () {
    'use strict';

    var DEFAULT_RECIPIENT = 'equipo de UAT';
    var DEFAULT_CIERRE = 'Quedo pendiente por cualquier duda o comentario.';
    var SCHEMA_VERSION = 2;
    var PREVIEW_DELAY = 500;

    var uidCounter = 0;
    function nid() { return 'u' + (++uidCounter); }
    function $(id) { return document.getElementById(id); }
    function val(id) { var e = $(id); return e ? e.value : ''; }
    function setVal(id, v) { var e = $(id); if (e) e.value = v == null ? '' : v; }

    // ── Estado de las listas (los campos simples viven en el DOM) ──────────
    var st = { related: [], requirements: [], examples: [], saludoAuto: true, subjectEdited: false, legacyReqImages: [], touched: false };
    var lastText = '';
    var lastHtml = '';
    var previewDirty = false;   // hay cambios en el formulario que la vista previa aún no refleja
    var previewVer = 0;
    var lastWarnings = [];

    // ── Helpers de DOM (sin innerHTML con texto del usuario) ───────────────
    function h(tag, props, kids) {
        var e = document.createElement(tag);
        Object.keys(props || {}).forEach(function (k) {
            var v = props[k];
            if (v == null || v === false) return;
            if (k === 'class') e.className = v;
            else if (k === 'text') e.textContent = v;
            else if (k === 'value') e.value = v;
            else if (k.indexOf('on') === 0) e.addEventListener(k.slice(2), v);
            else e.setAttribute(k, v === true ? '' : v);
        });
        (kids || []).forEach(function (c) {
            if (c == null || c === false) return;
            e.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
        });
        return e;
    }

    // ── Valores por defecto ────────────────────────────────────────────────
    function greetingByHour(date) {
        var hr = (date || new Date()).getHours();
        if (hr < 12) return 'Buenos d\u00edas';
        if (hr < 20) return 'Buenas tardes';
        return 'Buenas noches';
    }

    // ── Chips de RFCs relacionados ─────────────────────────────────────────
    function addRelated(raw) {
        String(raw || '').split(/[\s,;]+/).forEach(function (part) {
            var c = part.replace(/^rfc/i, '').trim();
            if (c && st.related.indexOf(c) < 0) st.related.push(c);
        });
        renderChips();
    }
    function renderChips() {
        var box = $('uatRelatedChips');
        if (!box) return;
        box.querySelectorAll('.uat-chip').forEach(function (c) { c.remove(); });
        var input = $('uatRelatedInput');
        st.related.forEach(function (rfc, i) {
            box.insertBefore(h('span', { class: 'uat-chip' }, [
                'RFC ' + rfc,
                h('button', {
                    type: 'button', class: 'uat-chip-x', 'aria-label': 'Quitar RFC ' + rfc,
                    onclick: function () { st.related.splice(i, 1); renderChips(); $('uatRelatedInput').focus(); schedulePreview(); }
                }, ['\u00d7'])
            ]), input);
        });
    }

    // ── Control de imagen reutilizable ─────────────────────────────────────
    function imageControl(idBase, getImg, setImg) {
        var box = h('div', { class: 'd-flex align-items-center flex-wrap gap-2' });
        function paint() {
            box.textContent = '';
            var img = getImg();
            var inputId = idBase + '-file';
            box.appendChild(h('label', { class: 'btn-clear btn-sm-uat uat-img-btn', for: inputId }, [
                img ? '\ud83d\udcf7 Cambiar imagen' : '\ud83d\udcf7 Agregar imagen',
                h('input', {
                    type: 'file', id: inputId, accept: 'image/png,image/jpeg,image/gif', class: 'uat-file',
                    onchange: function (e) {
                        var file = e.target.files && e.target.files[0];
                        if (!file) return;
                        compressImage(file).then(function (uri) { setImg(uri); paint(); schedulePreview(); })
                            .catch(function (err) { showToast(err.message, 'error'); });
                        e.target.value = '';
                    }
                })
            ]));
            if (img) {
                box.appendChild(h('img', { class: 'uat-img-thumb', src: img, alt: 'Miniatura de la imagen adjunta' }));
                box.appendChild(h('button', {
                    type: 'button', class: 'btn-remove-rdl btn-sm-uat',
                    onclick: function () { setImg(''); paint(); schedulePreview(); }
                }, ['\u2715 Quitar']));
            }
        }
        paint();
        return box;
    }

    // ── Tarjetas repetibles ────────────────────────────────────────────────
    function actBtn(act, glyph, label, disabled, onclick) {
        return h('button', {
            type: 'button', class: (act === 'del' ? 'btn-remove-rdl' : 'btn-clear') + ' btn-icon-uat', 'data-act': act, 'aria-label': label, title: label,
            disabled: disabled, onclick: onclick
        }, [glyph]);
    }

    /** Re-pinta una lista y devuelve el foco al control indicado (o a su hom\u00f3logo si qued\u00f3 deshabilitado). */
    function rerender(renderFn, id, selector, fallbackSelector) {
        renderFn();
        var scope = document.querySelector('[data-id="' + id + '"]');
        if (!scope) return;
        var node = scope.querySelector(selector);
        if ((!node || node.disabled) && fallbackSelector) node = scope.querySelector(fallbackSelector);
        if (node && !node.disabled) node.focus();
    }

    function moveItem(list, idx, dir) {
        var j = idx + dir;
        if (j < 0 || j >= list.length) return false;
        var t = list[idx]; list[idx] = list[j]; list[j] = t;
        return true;
    }

    function card(kind, list, idx, label, body, renderFn, inline) {
        var item = list[idx];
        function act(dir, actName) {
            return function () {
                if (moveItem(list, idx, dir)) {
                    rerender(renderFn, item.id, '[data-act="' + actName + '"]', '[data-act="' + (actName === 'up' ? 'down' : 'up') + '"]');
                    schedulePreview();
                }
            };
        }
        var head = h('div', { class: 'uat-item-head' }, [
            h('span', { class: 'uat-item-title', text: label }),
            inline ? h('div', { class: 'uat-inline' }, [inline]) : null,
            h('div', { class: 'uat-item-actions', role: 'group', 'aria-label': 'Acciones de ' + label }, [
                actBtn('up', '\u25b2', 'Subir ' + label, idx === 0, act(-1, 'up')),
                actBtn('down', '\u25bc', 'Bajar ' + label, idx === list.length - 1, act(1, 'down')),
                actBtn('del', '\u2715', 'Eliminar ' + label, false, function () {
                    list.splice(idx, 1);
                    renderFn();
                    schedulePreview();
                    var next = document.querySelector('[data-kind="' + kind + '"] [data-act="del"]') || document.querySelector('[data-add="' + kind + '"]');
                    if (next) next.focus();
                })
            ])
        ]);
        var c = h('div', { class: 'uat-block uat-item', 'data-id': item.id, 'data-kind': kind }, [head].concat(body));
        // Alt + flechas reordena desde cualquier campo, conservando el foco en ese campo
        c.addEventListener('keydown', function (e) {
            if (!e.altKey || (e.key !== 'ArrowUp' && e.key !== 'ArrowDown')) return;
            var field = e.target && e.target.getAttribute && e.target.getAttribute('data-field');
            e.preventDefault();
            if (moveItem(list, idx, e.key === 'ArrowUp' ? -1 : 1)) {
                rerender(renderFn, item.id, field ? '[data-field="' + field + '"]' : '[data-act="up"]', '[data-act="up"]');
                schedulePreview();
            }
        });
        return c;
    }

    function field(item, name, labelText, control) {
        var id = 'uat-' + item.id + '-' + name;
        control.id = id;
        control.setAttribute('data-field', name);
        return h('div', { class: 'mb-2' }, [h('label', { class: 'form-label', for: id, text: labelText }), control]);
    }

    function isReqEmpty(r) { return !(r.title || '').trim() && !(r.description || '').trim() && !(r.bullets || '').trim() && !r.image; }

    function reqNumber(idx) {
        var start = parseInt(val('uatReqStart'), 10);
        if (isNaN(start) || start < 0) start = 1;
        var n = start;
        for (var i = 0; i < idx; i++) if (!isReqEmpty(st.requirements[i])) n++;
        return n;
    }

    /** Los requerimientos vacíos no se numeran (el correo los omite). */
    function reqLabel(idx) {
        return isReqEmpty(st.requirements[idx]) ? 'Requerimiento nuevo' : 'Requerimiento ' + reqNumber(idx);
    }

    function updateReqNumbers() {
        document.querySelectorAll('#uatReqList .uat-item').forEach(function (node, i) {
            var label = reqLabel(i);
            var title = node.querySelector('.uat-item-title');
            if (title) title.textContent = label;
            node.querySelector('.uat-item-actions').setAttribute('aria-label', 'Acciones de ' + label);
            node.querySelector('[data-act="up"]').setAttribute('aria-label', 'Subir ' + label);
            node.querySelector('[data-act="down"]').setAttribute('aria-label', 'Bajar ' + label);
            node.querySelector('[data-act="del"]').setAttribute('aria-label', 'Eliminar ' + label);
        });
    }

    function bindInput(control, item, key, after) {
        control.addEventListener('input', function () {
            item[key] = control.value;
            if (after) after();
        });
        return control;
    }

    function newReq() { return { id: nid(), title: '', description: '', bullets: '', image: '', showDetail: false }; }

    function link(text, onclick) {
        return h('button', { type: 'button', class: 'uat-link', onclick: onclick }, [text]);
    }

    function renderRequirements() {
        var box = $('uatReqList');
        if (!box) return;
        if (!st.requirements.length) st.requirements.push(newReq());
        box.textContent = '';
        st.requirements.forEach(function (r, i) {
            var title = bindInput(h('input', { type: 'text', class: 'form-control', value: r.title, autocomplete: 'off', placeholder: 'T\u00edtulo, ej: Alta de promociones', 'aria-label': 'T\u00edtulo del requerimiento' }), r, 'title', updateReqNumbers);
            title.setAttribute('data-field', 'title');
            var body = [];
            var extras = h('div', { class: 'd-flex align-items-center flex-wrap gap-3 mt-2' });
            if (r.showDetail) {
                body.push(field(r, 'description', 'Descripci\u00f3n', bindInput(h('textarea', { class: 'form-control', rows: 2, value: r.description }), r, 'description', updateReqNumbers)));
                body.push(field(r, 'bullets', 'Vi\u00f1etas (una por l\u00ednea)', bindInput(h('textarea', { class: 'form-control', rows: 3, value: r.bullets }), r, 'bullets', updateReqNumbers)));
            } else {
                extras.appendChild(link('A\u00f1adir detalle', function () {
                    r.showDetail = true;
                    rerender(renderRequirements, r.id, '[data-field="description"]');
                }));
            }
            extras.appendChild(imageControl('uat-' + r.id, function () { return r.image; }, function (v) { r.image = v; updateReqNumbers(); }));
            body.push(extras);
            box.appendChild(card('req', st.requirements, i, reqLabel(i), body, renderRequirements, title));
        });
        updateReqNumbers();
    }

    function exampleHasContent(e) {
        return !!e.image || e.pairs.some(function (p) { return (p.key || '').trim() || (p.value || '').trim(); });
    }

    function renderExamples() {
        var box = $('uatExList');
        if (!box) return;
        box.textContent = '';
        st.examples.forEach(function (ex, i) {
            var pairsBox = h('div', { class: 'mb-1' });
            ex.pairs.forEach(function (p, pi) {
                var keyInput = h('input', { type: 'text', class: 'form-control', value: p.key, 'data-field': 'pair-' + pi + '-key', 'aria-label': 'Campo ' + (pi + 1) + ' del ejemplo ' + (i + 1), placeholder: 'Suscriptor', autocomplete: 'off' });
                var valInput = h('input', { type: 'text', class: 'form-control', value: p.value, 'data-field': 'pair-' + pi + '-value', 'aria-label': 'Valor ' + (pi + 1) + ' del ejemplo ' + (i + 1), placeholder: '513', autocomplete: 'off' });
                bindInput(keyInput, p, 'key');
                bindInput(valInput, p, 'value');
                pairsBox.appendChild(h('div', { class: 'uat-pair' }, [keyInput, valInput,
                    h('button', {
                        type: 'button', class: 'btn-remove-rdl btn-icon-uat', 'aria-label': 'Quitar campo ' + (pi + 1) + ' del ejemplo ' + (i + 1), title: 'Quitar campo',
                        onclick: function () {
                            ex.pairs.splice(pi, 1);
                            rerender(renderExamples, ex.id, '[data-add-pair]');
                            schedulePreview();
                        }
                    }, ['\u2715'])]));
            });
            var body = [];
            if (ex.showTitle) {
                body.push(field(ex, 'title', 'Encabezado', bindInput(h('input', { type: 'text', class: 'form-control', value: ex.title, autocomplete: 'off', placeholder: 'Ejemplo ' + (i + 1) + ':' }), ex, 'title')));
            }
            body.push(pairsBox);
            body.push(h('button', {
                type: 'button', class: 'btn-clear btn-sm-uat mb-2', 'data-add-pair': 'true',
                onclick: function () {
                    ex.pairs.push({ key: '', value: '' });
                    rerender(renderExamples, ex.id, '[data-field="pair-' + (ex.pairs.length - 1) + '-key"]');
                }
            }, ['\uff0b Agregar campo']));
            var extras = h('div', { class: 'd-flex align-items-center flex-wrap gap-3' });
            if (!ex.showTitle) {
                extras.appendChild(link('Personalizar encabezado', function () {
                    ex.showTitle = true;
                    rerender(renderExamples, ex.id, '[data-field="title"]');
                }));
            }
            extras.appendChild(imageControl('uat-' + ex.id, function () { return ex.image; }, function (v) { ex.image = v; }));
            body.push(extras);
            box.appendChild(card('ex', st.examples, i, 'Ejemplo ' + (i + 1), body, renderExamples));
        });
    }

    // ── Recolecci\u00f3n ───────────────────────────────────────────────────────
    function formatUatRfcNumber(value) {
        var raw = (value || '').trim();
        if (!raw) return '';
        var withoutPrefix = raw.replace(/^rfc\s*/i, '').trim();
        return withoutPrefix ? 'RFC ' + withoutPrefix : 'RFC';
    }

    function bulletLines(text) {
        return String(text || '').split(/\r?\n/).map(function (l) { return l.replace(/^\s*[-*\u2022]\s*/, '').trim(); }).filter(Boolean);
    }

    /** Payload para el backend (UatRequest). Siempre incluye campos estructurados. */
    function collectUatData() {
        var start = parseInt(val('uatReqStart'), 10);
        return {
            rfcNumber: formatUatRfcNumber(val('uatRfcNumber')),
            rfcName: val('uatRfcName'),
            saludo: val('uatSaludo'),
            recipient: val('uatRecipient'),
            subject: st.subjectEdited ? val('uatSubject') : '',
            relatedRfcs: st.related.slice(),
            publishDate: val('uatDate'),
            adjunto: val('uatAdjunto'),
            requirementsStart: isNaN(start) || start < 0 ? 1 : start,
            requirementsIntro: val('uatRequerimientos'),
            requirements: st.requirements.filter(function (r) { return !isReqEmpty(r); }).map(function (r) {
                return { title: r.title, description: r.description, bullets: bulletLines(r.bullets), image: r.image || '' };
            }),
            validationExamples: st.examples.filter(exampleHasContent).map(function (e) {
                return { title: e.title, pairs: e.pairs.map(function (p) { return { key: p.key, value: p.value }; }), image: e.image || '' };
            }),
            requerimientosImagenes: st.legacyReqImages.slice(),
            blocks: typeof collectEditorBlocks === 'function' ? collectEditorBlocks() : [],
            nota: val('uatNota'),
            cierre: val('uatCierre')
        };
    }

    /** Estado completo del formulario, para plantillas y borrador (sin ids internos). */
    function collectUatState() {
        var editor = $('uatRichEditor');
        return {
            schemaVersion: SCHEMA_VERSION,
            rfc: val('uatRfcNumber'),
            related: st.related.slice(),
            name: val('uatRfcName'),
            date: val('uatDate'),
            saludo: val('uatSaludo'),
            saludoAuto: st.saludoAuto,
            recipient: val('uatRecipient'),
            subject: st.subjectEdited ? val('uatSubject') : '',
            subjectEdited: st.subjectEdited,
            adjunto: val('uatAdjunto'),
            reqStart: val('uatReqStart'),
            reqIntro: val('uatRequerimientos'),
            requirements: st.requirements.filter(function (r) { return !isReqEmpty(r); }).map(function (r) { return { title: r.title, description: r.description, bullets: r.bullets, image: r.image || '' }; }),
            examples: st.examples.filter(exampleHasContent).map(function (e) {
                return { title: e.title, pairs: e.pairs.map(function (p) { return { key: p.key, value: p.value }; }), image: e.image || '' };
            }),
            nota: val('uatNota'),
            cierre: val('uatCierre'),
            legacyHtml: editor ? editor.innerHTML : '',
            legacyReqImages: st.legacyReqImages.slice()
        };
    }

    // ── Restauraci\u00f3n (con migraci\u00f3n de plantillas/borradores legacy) ────────
    /**
     * Formato anterior: uatRfcNumber, uatRfcName, uatSaludo, uatAdjunto, uatRequerimientos,
     * reqImages, uatNota, uatCierre, uatEditorHtml.
     * "Requerimientos" pasa a ser el texto introductorio libre y el editor rico va a
     * "Contenido libre (avanzado)"; ninguno se mezcla con los ejemplos de validaci\u00f3n.
     */
    function fromLegacy(d) {
        var saludo = String(d.uatSaludo || '').replace(/[\s.,;:]+$/, '');
        return {
            schemaVersion: SCHEMA_VERSION,
            rfc: String(d.uatRfcNumber || '').replace(/^rfc\s*/i, ''),
            related: [], name: d.uatRfcName || '', date: '',
            saludo: saludo || greetingByHour(),
            saludoAuto: !saludo || saludo === 'Buenas tardes',
            recipient: DEFAULT_RECIPIENT, subject: '', subjectEdited: false,
            adjunto: d.uatAdjunto || '', reqStart: '1', reqIntro: d.uatRequerimientos || '',
            requirements: [], examples: [],
            nota: d.uatNota || '', cierre: d.uatCierre || DEFAULT_CIERRE,
            legacyHtml: d.uatEditorHtml || '',
            legacyReqImages: Array.isArray(d.reqImages) ? d.reqImages : (d.reqImageData ? [d.reqImageData] : [])
        };
    }

    // ── Recompresión de imágenes pesadas al guardar/cargar ─────────────────
    var RECOMPRESS_BYTES = 200 * 1024;
    var slimCache = new Map();

    function dataUriBytes(uri) { return Math.floor((uri.length - uri.indexOf(',') - 1) * 3 / 4); }

    /** Recomprime una imagen solo si pesa más de ~200 KB (no toca GIF ni JPEG ya ligeros). */
    function slimImage(uri) {
        if (typeof uri !== 'string' || !/^data:image\/(png|jpe?g);/i.test(uri) || dataUriBytes(uri) <= RECOMPRESS_BYTES) {
            return Promise.resolve(uri);
        }
        if (slimCache.has(uri)) return slimCache.get(uri);
        var p = compressDataUri(uri).then(function (out) { return out && out.length < uri.length ? out : uri; },
            function () { return uri; });
        slimCache.set(uri, p);
        if (slimCache.size > 12) slimCache.delete(slimCache.keys().next().value);
        return p;
    }

    function slimHtml(html) {
        if (typeof html !== 'string' || html.indexOf('data:image/') < 0) return Promise.resolve(html);
        var re = /(<img\b[^>]*?\ssrc\s*=\s*)(["'])(data:image\/[^"']+)\2/gi;
        var found = [], m;
        while ((m = re.exec(html))) found.push(m[3]);
        return Promise.all(found.map(slimImage)).then(function (res) {
            var i = 0;
            return html.replace(re, function (all, pre, q) { return pre + q + res[i++] + q; });
        });
    }

    /**
     * Devuelve una copia del estado (v2 o legacy) con las imágenes pesadas recomprimidas:
     * requerimientos, ejemplos, imágenes legacy y las del HTML del editor.
     */
    function prepareUatState(d) {
        if (!d || typeof d !== 'object') return Promise.resolve(d);
        var c = Object.assign({}, d), jobs = [];
        function field(obj, key) {
            jobs.push(slimImage(obj[key]).then(function (v) { obj[key] = v; }));
        }
        ['requirements', 'examples'].forEach(function (k) {
            if (!Array.isArray(d[k])) return;
            c[k] = d[k].map(function (x) { return Object.assign({}, x); });
            c[k].forEach(function (x) { if (x.image) field(x, 'image'); });
        });
        ['legacyReqImages', 'reqImages'].forEach(function (k) {
            if (!Array.isArray(d[k])) return;
            c[k] = d[k].slice();
            c[k].forEach(function (_, i) { field(c[k], i); });
        });
        if (d.reqImageData) field(c, 'reqImageData');
        ['legacyHtml', 'uatEditorHtml'].forEach(function (k) {
            if (d[k]) jobs.push(slimHtml(d[k]).then(function (v) { c[k] = v; }));
        });
        return Promise.all(jobs).then(function () { return c; });
    }

    function withIds(list, extra) {
        return (list || []).map(function (x) { return Object.assign({ id: nid() }, x, extra ? extra(x) : {}); });
    }

    function restoreUatState(d) {
        if (!d) return;
        if (d.schemaVersion !== SCHEMA_VERSION) d = fromLegacy(d);
        st.touched = false;
        setVal('uatRfcNumber', d.rfc);
        setVal('uatRfcName', d.name);
        setVal('uatDate', d.date);
        setVal('uatRecipient', d.recipient || DEFAULT_RECIPIENT);
        setVal('uatAdjunto', d.adjunto);
        setVal('uatReqStart', d.reqStart === '' || d.reqStart == null ? '1' : d.reqStart);
        setVal('uatRequerimientos', d.reqIntro);
        setVal('uatNota', d.nota);
        setVal('uatCierre', d.cierre || DEFAULT_CIERRE);

        st.saludoAuto = d.saludoAuto !== false;
        setVal('uatSaludo', st.saludoAuto ? greetingByHour() : d.saludo);
        st.subjectEdited = !!d.subjectEdited;
        setVal('uatSubject', st.subjectEdited ? d.subject : '');
        st.related = (d.related || []).slice();
        st.requirements = withIds(d.requirements, function (r) {
            return { showDetail: !!(String(r.description || '').trim() || String(r.bullets || '').trim()) };
        });
        // Los ejemplos vac\u00edos no se conservan: el primero se crea al pulsar "Agregar ejemplo" o al abrir la secci\u00f3n
        st.examples = withIds((d.examples || []).filter(function (e) {
            return exampleHasContent({ image: e.image, pairs: e.pairs || [] });
        }), function (e) {
            return {
                showTitle: !!String(e.title || '').trim(),
                pairs: (e.pairs || []).map(function (p) { return { key: p.key, value: p.value }; })
            };
        });
        st.legacyReqImages = (d.legacyReqImages || []).slice();

        var editor = $('uatRichEditor');
        if (editor) editor.innerHTML = d.legacyHtml || '';

        renderChips();
        renderRequirements();
        renderExamples();
        applySectionStates();
        schedulePreview(0);
    }

    /** Cada secci\u00f3n colapsable queda abierta solo si tiene contenido; con el formulario limpio, todas colapsadas. */
    function applySectionStates() {
        var start = parseInt(val('uatReqStart'), 10);
        setOpen('uatMore', st.subjectEdited || st.related.length > 0 || val('uatAdjunto').trim() !== ''
            || (!isNaN(start) && start !== 1));
        setOpen('uatExamples', st.examples.some(exampleHasContent));
        setOpen('uatNotes', val('uatNota').trim() !== '');
        var editor = $('uatRichEditor');
        setOpen('uatAdvanced', !!editor && (editor.textContent.trim() !== '' || !!editor.querySelector('img')));
    }

    function uatStateIsEmpty(d) {
        if (!d) return true;
        if (d.schemaVersion !== SCHEMA_VERSION) return !d.uatRfcNumber && !d.uatRfcName && !d.uatRequerimientos && !d.uatEditorHtml;
        var anyReq = (d.requirements || []).some(function (r) { return !isReqEmpty({ title: r.title, description: r.description, bullets: r.bullets, image: r.image }); });
        var anyEx = (d.examples || []).some(function (e) { return e.image || (e.pairs || []).some(function (p) { return p.key || p.value; }); });
        var legacy = (d.legacyHtml || '').replace(/<[^>]*>|&nbsp;|\s/g, '');
        return !d.rfc && !d.name && !d.reqIntro && !anyReq && !anyEx && !legacy && !d.nota && !(d.related || []).length;
    }

    function uatHasRequirements() {
        return !!val('uatRequerimientos').trim() || st.requirements.some(function (r) { return !isReqEmpty(r); });
    }

    // ── Secciones colapsables y res\u00famenes de una l\u00ednea ───────────────────────
    var DETAILS = ['uatMore', 'uatExamples', 'uatAdvanced', 'uatNotes'];

    function syncExpanded(d) {
        var s = d.querySelector(':scope > summary');
        if (s) s.setAttribute('aria-expanded', d.open ? 'true' : 'false');
    }
    function setOpen(id, open) {
        var d = $(id);
        if (!d) return;
        d.open = open;
        syncExpanded(d);
    }
    function setSummary(id, text) { var e = $(id); if (e) e.textContent = text; }

    function updateSummaries() {
        var more = [st.subjectEdited ? 'Asunto editado' : 'Asunto autom\u00e1tico'];
        if (st.related.length) more.push(st.related.length + (st.related.length === 1 ? ' RFC relacionado' : ' RFCs relacionados'));
        if (val('uatAdjunto').trim()) more.push('con adjuntos');
        var start = parseInt(val('uatReqStart'), 10);
        if (!isNaN(start) && start !== 1) more.push('numeraci\u00f3n desde ' + start);
        setSummary('uatMoreSummary', more.join(' \u00b7 '));

        var n = st.examples.filter(exampleHasContent).length;
        setSummary('uatExamplesSummary', n ? n + (n === 1 ? ' ejemplo' : ' ejemplos') : 'Sin ejemplos');

        var cierre = val('uatCierre').trim();
        setSummary('uatNotesSummary', (val('uatNota').trim() ? 'Con nota' : 'Sin nota') + ' \u00b7 '
            + (!cierre || cierre === DEFAULT_CIERRE ? 'cierre est\u00e1ndar' : 'cierre personalizado'));

        var editor = $('uatRichEditor');
        var hasFree = !!editor && (editor.textContent.trim() !== '' || !!editor.querySelector('img'));
        setSummary('uatAdvancedSummary', hasFree ? 'Con contenido' : 'Vac\u00edo \u00b7 se agrega despu\u00e9s de los ejemplos');
    }

    // ── Vista previa en vivo ───────────────────────────────────────────────
    var previewTimer = null;
    var previewCtl = null;

    function setStatus(text) { var s = $('uatPreviewStatus'); if (s) s.textContent = text || ''; }

    function schedulePreview(delay) {
        clearTimeout(previewTimer);
        previewDirty = true;
        previewVer++;
        updateSummaries();
        setStatus('Actualizando vista previa\u2026');
        previewTimer = setTimeout(refreshPreview, delay == null ? PREVIEW_DELAY : delay);
    }

    /** Solo en pantalla: las capturas de los pasos (tras "Favor de validar...") se acotan en alto. */
    function limitStepImages(root) {
        var started = false;
        root.querySelectorAll('p').forEach(function (p) {
            if (/^Favor de validar con el m/i.test(p.textContent.trim())) started = true;
            if (started) p.querySelectorAll('img').forEach(function (img) { img.classList.add('uat-step-img'); });
        });
    }

    function paintPreview(data, ver) {
        previewDirty = ver !== previewVer;
        lastHtml = data.html || '';
        lastText = data.text || '';
        var preview = $('uatEmailPreview');
        preview.innerHTML = lastHtml;
        limitStepImages(preview);
        var subj = data.subject || '';
        if (!st.subjectEdited) setVal('uatSubject', subj);
        var sp = $('uatSubjectPreview');
        if (sp) sp.textContent = st.subjectEdited ? val('uatSubject') : subj;
        lastWarnings = data.warnings || [];
        renderWarnings();
    }

    /** Avisos suaves: solo se muestran tras escribir algo o pulsar Generar; nunca bloquean. */
    function renderWarnings() {
        var box = $('uatWarnings');
        if (!box) return;
        var items = st.touched ? lastWarnings : [];
        box.hidden = items.length === 0;
        var ul = $('uatWarnList');
        ul.textContent = '';
        items.forEach(function (w) { ul.appendChild(h('li', { text: w })); });
        $('uatWarnToggle').textContent = items.length + (items.length === 1 ? ' aviso' : ' avisos');
    }

    async function refreshPreview() {
        if (!$('uatEmailPreview')) return;
        if (previewCtl) previewCtl.abort();
        var ctl = previewCtl = new AbortController();
        var ver = previewVer;
        try {
            var resp = await fetch('/uat/preview', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(collectUatData()), signal: ctl.signal
            });
            if (!resp.ok) throw new Error('Error ' + resp.status);
            var data = await resp.json();
            if (ctl !== previewCtl) return;
            paintPreview(data, ver);
            setStatus('');
        } catch (err) {
            if (err && err.name === 'AbortError') return;
            setStatus('No se pudo actualizar la vista previa (' + err.message + ').');
        }
    }

    // ── Acciones ───────────────────────────────────────────────────────────
    async function generateUatEmail() {
        var btn = document.querySelector('#pane-uat .btn-generate');
        var orig = btn ? btn.innerHTML : '';
        if (btn) { btn.disabled = true; btn.innerHTML = '<span class="spinner"></span>&nbsp;Generando...'; }
        st.touched = true;
        try {
            var resp = await fetch('/generate-uat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(collectUatData()) });
            if (!resp.ok) throw new Error('Error ' + resp.status);
            var html = await resp.text();
            clearTimeout(previewTimer);
            await refreshPreview();
            if (!lastHtml) $('uatEmailPreview').innerHTML = html;
            $('uatEmailSection').scrollIntoView({ behavior: 'smooth', block: 'start' });
            showToast('Correo VoBo UAT generado y guardado en el historial', 'success');
        } catch (err) { showToast('Error al generar el correo: ' + err.message, 'error'); }
        finally { if (btn) { btn.disabled = false; btn.innerHTML = orig; } }
    }

    async function downloadUatExport(type) {
        try {
            var resp = await fetch('/uat/export/' + type, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(collectUatData()) });
            if (!resp.ok) throw new Error('Error ' + resp.status);
            var blob = await resp.blob();
            var cd = resp.headers.get('Content-Disposition') || '';
            var m = cd.match(/filename[*]?=(?:UTF-8'')?["']?([^;"'\n]+)/i);
            var rfc = (formatUatRfcNumber(val('uatRfcNumber')) || 'VoBo-UAT').replace(/[^a-zA-Z0-9\-_]/g, '-');
            var ext = type === 'markdown' ? 'md' : type;
            var filename = m ? decodeURIComponent(m[1].trim()) : rfc + '.' + ext;
            var url = URL.createObjectURL(blob);
            var a = document.createElement('a'); a.href = url; a.download = filename;
            document.body.appendChild(a); a.click(); document.body.removeChild(a);
            URL.revokeObjectURL(url);
        } catch (err) { showToast('Error al exportar: ' + err.message, 'error'); }
    }

    async function copyUatEmail() {
        // El HTML y el texto salen siempre del backend: si la vista previa está desactualizada, se refresca antes
        if (previewDirty || !lastHtml) {
            clearTimeout(previewTimer);
            await refreshPreview();
        }
        if (!lastHtml || !lastText) {
            showToast('No se pudo copiar: la vista previa del correo no est\u00e1 disponible. Int\u00e9ntalo de nuevo.', 'error', 6000);
            return;
        }
        if (window.ClipboardItem && navigator.clipboard && navigator.clipboard.write) {
            try {
                await navigator.clipboard.write([new ClipboardItem({
                    'text/html': new Blob([lastHtml], { type: 'text/html' }),
                    'text/plain': new Blob([lastText], { type: 'text/plain' })
                })]);
                showToast('Correo copiado con formato (listo para pegar en Outlook)', 'success');
                return;
            } catch (err) { /* se intenta solo texto */ }
        }
        try {
            await navigator.clipboard.writeText(lastText);
            showToast('Copiado solo como texto plano: se perdi\u00f3 el formato y las im\u00e1genes. Usa Exportar \u2192 HTML si los necesitas.', null, 7000);
        } catch (err) {
            showToast('No se pudo copiar al portapapeles. Usa Exportar \u2192 HTML o Markdown.', 'error', 6000);
        }
    }

    function copySubject() {
        var text = val('uatSubject') || (($('uatSubjectPreview') || {}).textContent || '');
        navigator.clipboard.writeText(text).then(function () { showToast('Asunto copiado', 'success'); });
    }

    function resetForm() {
        restoreUatState({
            schemaVersion: SCHEMA_VERSION, rfc: '', related: [], name: '', date: '', saludo: '', saludoAuto: true,
            recipient: DEFAULT_RECIPIENT, subject: '', subjectEdited: false, adjunto: '', reqStart: '1', reqIntro: '',
            requirements: [], examples: [], nota: '', cierre: DEFAULT_CIERRE, legacyHtml: '', legacyReqImages: []
        });
    }

    function clearUatForm() { resetForm(); }

    // ── Eventos ────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        var form = $('uatForm');
        if (!form) return;

        form.addEventListener('input', function (e) { if (e.isTrusted) st.touched = true; schedulePreview(); });
        form.addEventListener('change', function (e) { if (e.isTrusted) st.touched = true; schedulePreview(); });

        $('uatWarnToggle').addEventListener('click', function () {
            var open = this.getAttribute('aria-expanded') !== 'true';
            this.setAttribute('aria-expanded', open ? 'true' : 'false');
            $('uatWarnList').hidden = !open;
        });

        $('uatSaludo').addEventListener('input', function () {
            st.saludoAuto = !this.value.trim();
        });
        $('uatSaludo').addEventListener('blur', function () {
            if (st.saludoAuto) this.value = greetingByHour();
        });
        $('uatSubject').addEventListener('input', function () {
            st.subjectEdited = !!this.value.trim();
        });
        $('uatSubjectReset').addEventListener('click', function () {
            st.subjectEdited = false; setVal('uatSubject', ''); schedulePreview(0);
        });
        $('uatReqStart').addEventListener('input', updateReqNumbers);

        var relInput = $('uatRelatedInput');
        relInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ',') {
                e.preventDefault();
                if (relInput.value.trim()) { addRelated(relInput.value); relInput.value = ''; schedulePreview(); }
            } else if (e.key === 'Backspace' && !relInput.value && st.related.length) {
                st.related.pop(); renderChips(); schedulePreview();
            }
        });
        relInput.addEventListener('blur', function () {
            if (relInput.value.trim()) { addRelated(relInput.value); relInput.value = ''; schedulePreview(); }
        });
        relInput.addEventListener('paste', function (e) {
            var text = (e.clipboardData || window.clipboardData).getData('text');
            if (/[\s,;]/.test(text.trim())) { e.preventDefault(); addRelated(text); schedulePreview(); }
        });
        $('uatRelatedChips').addEventListener('click', function (e) { if (e.target === this) relInput.focus(); });

        $('uatReqAdd').addEventListener('click', function () {
            var r = newReq();
            st.requirements.push(r);
            renderRequirements();
            var f = document.querySelector('[data-id="' + r.id + '"] [data-field="title"]');
            if (f) f.focus();
        });
        $('uatExAdd').addEventListener('click', function () {
            var e = { id: nid(), title: '', showTitle: false, pairs: [{ key: '', value: '' }], image: '' };
            st.examples.push(e);
            renderExamples();
            var f = document.querySelector('[data-id="' + e.id + '"] [data-field="pair-0-key"]');
            if (f) f.focus();
        });
        $('uatSubjectCopy').addEventListener('click', copySubject);

        DETAILS.forEach(function (id) {
            var d = $(id);
            if (d) d.addEventListener('toggle', function () { syncExpanded(d); });
        });

        // Al abrir la secci\u00f3n de ejemplos vac\u00eda se crea el primero
        $('uatExamples').addEventListener('toggle', function () {
            if (this.open && !st.examples.length) {
                st.examples.push({ id: nid(), title: '', showTitle: false, pairs: [{ key: '', value: '' }], image: '' });
                renderExamples();
            }
        });

        // Al volver al pane con el formulario limpio, las secciones vuelven a quedar colapsadas
        var pane = $('pane-uat');
        var wasActive = pane.classList.contains('active');
        new MutationObserver(function () {
            var active = pane.classList.contains('active');
            if (active && !wasActive && uatStateIsEmpty(collectUatState())) DETAILS.forEach(function (id) { setOpen(id, false); });
            wasActive = active;
        }).observe(pane, { attributes: true, attributeFilter: ['class'] });

        resetForm();
    });

    window.formatUatRfcNumber = formatUatRfcNumber;
    window.collectUatData = collectUatData;
    window.collectUatState = collectUatState;
    window.restoreUatState = restoreUatState;
    window.prepareUatState = prepareUatState;
    window.uatStateIsEmpty = uatStateIsEmpty;
    window.uatHasRequirements = uatHasRequirements;
    window.generateUatEmail = generateUatEmail;
    window.downloadUatExport = downloadUatExport;
    window.copyUatEmail = copyUatEmail;
    window.clearUatForm = clearUatForm;
    window.addUatBlock = function () { /* legado: reemplazado por el formulario estructurado */ };
})();
