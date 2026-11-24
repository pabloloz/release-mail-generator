/* ── AutoSave — Unified draft persistence for all forms ─────────────────── */
(function () {
    'use strict';

    var AUTOSAVE_INTERVAL = 5000; // 5 seconds
    var DRAFT_PREFIX = 'rnq-draft-';
    var DRAFT_TIMESTAMP_SUFFIX = '-ts';
    var DRAFT_ACK_SUFFIX = '-ack';  // tracks acknowledged/dismissed state
    var SESSION_KEY = 'rnq-session-id';

    // ── Form definitions ───────────────────────────────────────
    var FORMS = {
        rdl: {
            key: DRAFT_PREFIX + 'rdl',
            label: 'Correo RDL',
            pane: 'rdl',
            collect: function () {
                return {
                    rdlSemana: v('rdlSemana'),
                    rdlAnio: v('rdlAnio'),
                    rdlReleaseDatePicker: v('rdlReleaseDatePicker'),
                    rdlReleaseTimePicker: v('rdlReleaseTimePicker'),
                    rdlReleaseUrl: v('rdlReleaseUrl'),
                    rdlAction: v('rdlAction'),
                    entries: collectRdlEntries()
                };
            },
            restore: function (d) {
                sv('rdlSemana', d.rdlSemana);
                sv('rdlAnio', d.rdlAnio);
                sv('rdlReleaseDatePicker', d.rdlReleaseDatePicker);
                sv('rdlReleaseTimePicker', d.rdlReleaseTimePicker);
                sv('rdlReleaseUrl', d.rdlReleaseUrl);
                sv('rdlAction', d.rdlAction);
                if (typeof updateRdlPaths === 'function') updateRdlPaths();
            },
            isEmpty: function (d) {
                return !d.rdlReleaseUrl && (!d.entries || d.entries.length === 0);
            }
        },
        rdlTelegram: {
            key: DRAFT_PREFIX + 'rdl-telegram',
            label: 'Telegram RDL',
            pane: 'rdl-telegram',
            collect: function () {
                return {
                    rdlTgAction: v('rdlTgAction'),
                    rdlTgDate: v('rdlTgDate'),
                    rdlTgTime: v('rdlTgTime'),
                    rdlTgRdls: collectDynamic('rdlTgRdls'),
                    rdlTgSps: collectDynamic('rdlTgSps'),
                    rdlTgProjects: collectDynamic('rdlTgProjects')
                };
            },
            restore: function (d) {
                sv('rdlTgAction', d.rdlTgAction);
                sv('rdlTgDate', d.rdlTgDate);
                sv('rdlTgTime', d.rdlTgTime);
                restoreDynamic('rdlTgRdlsContainer', 'rdlTgRdls', d.rdlTgRdls, window.addRdlTgRdlRow);
                restoreDynamic('rdlTgSpsContainer', 'rdlTgSps', d.rdlTgSps, window.addRdlTgSpRow);
                restoreDynamic('rdlTgProjectsContainer', 'rdlTgProjects', d.rdlTgProjects, window.addRdlTgProjectRow);
            },
            isEmpty: function (d) {
                return !d.rdlTgDate && (!d.rdlTgRdls || d.rdlTgRdls.length === 0);
            }
        },
        uat: {
            key: DRAFT_PREFIX + 'uat',
            label: 'VoBo UAT',
            pane: 'uat',
            collect: function () {
                return typeof collectUatState === 'function' ? collectUatState() : {};
            },
            restore: function (d) {
                // Acepta el formato v2 y los borradores legacy
                if (typeof restoreUatState === 'function') restoreUatState(d);
            },
            // Recomprime las imágenes pesadas (>200 KB) al guardar y al restaurar
            prepare: function (d) {
                return typeof prepareUatState === 'function' ? prepareUatState(d) : Promise.resolve(d);
            },
            isEmpty: function (d) {
                return typeof uatStateIsEmpty === 'function' ? uatStateIsEmpty(d) : true;
            }
        }
    };

    // ── Helpers ─────────────────────────────────────────────────
    function v(id) { var el = document.getElementById(id); return el ? el.value : ''; }
    function sv(id, val) { var el = document.getElementById(id); if (el && val != null) el.value = val; }

    function collectDynamic(name) {
        return [].slice.call(document.querySelectorAll('input[name="' + name + '"]'))
            .map(function (el) { return el.value.trim(); })
            .filter(function (v) { return v; });
    }

    function restoreDynamic(containerId, inputName, values, addRowFn) {
        var container = document.getElementById(containerId);
        if (!container || !values || !values.length) return;
        container.innerHTML = '';
        values.forEach(function (val) {
            if (addRowFn) addRowFn();
            var last = container.querySelector('.dynamic-item:last-child input[name="' + inputName + '"]');
            if (last) last.value = val;
        });
    }

    function collectRdlEntries() {
        var entries = document.querySelectorAll('#rdlEntries .rdl-entry');
        var result = [];
        entries.forEach(function (entry, idx) {
            var g = function (name) {
                var el = entry.querySelector('[name="rdls[' + idx + '].' + name + '"]');
                return el ? el.value : '';
            };
            result.push({
                rdlReportName: g('rdlReportName'),
                rdlReportFolder: g('rdlReportFolder'),
                rdlUrlMegang: g('rdlUrlMegang'),
                rdlUrlNtrs02: g('rdlUrlNtrs02'),
                rdlPathMegang: g('rdlPathMegang'),
                rdlPathNtrs02: g('rdlPathNtrs02')
            });
        });
        return result;
    }

    // ── Save / Load ────────────────────────────────────────────
    var saveSeq = {}; // descarta escrituras asíncronas que llegan después de limpiar el borrador

    /**
     * Escribe el borrador; si el almacenamiento está lleno reintenta sin imágenes.
     * `notify` avisa (una vez por sesión) cuando se pierden imágenes o no se pudo guardar.
     */
    function persistDraft(formDef, data, notify) {
        var err = RnqStorage.write(formDef.key, JSON.stringify(data));
        var dropped = false;
        if (err && RnqStorage.isQuotaError(err)) {
            var stripped = RnqStorage.stripImages(data);
            if (stripped.removed) {
                err = RnqStorage.write(formDef.key, JSON.stringify(stripped.value));
                dropped = !err;
            }
        }
        if (err) {
            if (notify) RnqStorage.warnOnce('draft-failed',
                'El almacenamiento del navegador está lleno: el borrador no se pudo guardar. Guarda una plantilla o libera espacio.');
            return;
        }
        RnqStorage.write(formDef.key + DRAFT_TIMESTAMP_SUFFIX, new Date().toISOString());
        if (dropped && notify) RnqStorage.warnOnce('draft-noimages',
            'El almacenamiento del navegador está lleno: el borrador se guardó SIN imágenes. Las imágenes no se guardaron.');
    }

    function saveDraft(formDef, onDone) {
        var data;
        try { data = formDef.collect(); } catch (e) { return; }
        var seq = saveSeq[formDef.key] = (saveSeq[formDef.key] || 0) + 1;
        if (formDef.isEmpty(data)) {
            // Form is empty — clear draft entirely
            clearDraft(formDef);
            if (onDone) onDone();
            return;
        }
        function write(toSave) {
            if (saveSeq[formDef.key] === seq) persistDraft(formDef, toSave, true);
            if (onDone) onDone();
        }
        if (typeof formDef.prepare === 'function') {
            formDef.prepare(data).then(write, function () { write(data); });
        } else {
            write(data);
        }
    }

    function loadDraft(formDef) {
        try {
            var raw = localStorage.getItem(formDef.key);
            return raw ? JSON.parse(raw) : null;
        } catch (e) { return null; }
    }

    function clearDraft(formDef) {
        saveSeq[formDef.key] = (saveSeq[formDef.key] || 0) + 1;
        try {
            localStorage.removeItem(formDef.key);
            localStorage.removeItem(formDef.key + DRAFT_TIMESTAMP_SUFFIX);
            localStorage.removeItem(formDef.key + DRAFT_ACK_SUFFIX);
        } catch (e) {}
    }

    /** Mark a draft as "seen" so the banner won't reappear for the same content. */
    function acknowledgeDraft(formDef) {
        try {
            var ts = localStorage.getItem(formDef.key + DRAFT_TIMESTAMP_SUFFIX);
            if (ts) localStorage.setItem(formDef.key + DRAFT_ACK_SUFFIX, ts);
        } catch (e) {}
    }

    /** Returns true if the draft has been acknowledged (banner already shown & handled). */
    function isDraftAcknowledged(formDef) {
        try {
            var draftTs = localStorage.getItem(formDef.key + DRAFT_TIMESTAMP_SUFFIX);
            var ackTs = localStorage.getItem(formDef.key + DRAFT_ACK_SUFFIX);
            // Acknowledged if the ack timestamp matches or is newer than the draft
            return ackTs && draftTs && ackTs >= draftTs;
        } catch (e) { return true; }
    }

    function getDraftTimestamp(formDef) {
        try {
            var ts = localStorage.getItem(formDef.key + DRAFT_TIMESTAMP_SUFFIX);
            return ts ? new Date(ts) : null;
        } catch (e) { return null; }
    }

    // ── Recovery Banner ────────────────────────────────────────
    function showRecoveryBanner(formDef, data, timestamp) {
        // Don't show if already acknowledged
        if (isDraftAcknowledged(formDef)) return;

        var existing = document.getElementById('recovery-banner-' + formDef.pane);
        if (existing) existing.remove();

        var timeStr = timestamp
            ? timestamp.toLocaleDateString('es-MX') + ' ' + timestamp.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
            : '';

        var banner = document.createElement('div');
        banner.id = 'recovery-banner-' + formDef.pane;
        banner.className = 'recovery-banner';
        banner.innerHTML =
            '<div class="recovery-banner-content">'
            + '<span class="recovery-banner-icon">💾</span>'
            + '<div class="recovery-banner-text">'
            + '<strong>Borrador recuperado:</strong> ' + formDef.label
            + (timeStr ? ' <span class="recovery-banner-time">(' + timeStr + ')</span>' : '')
            + '</div>'
            + '<div class="recovery-banner-actions">'
            + '<button class="recovery-btn recovery-btn-restore">Restaurar</button>'
            + '<button class="recovery-btn recovery-btn-dismiss">Descartar</button>'
            + '</div>'
            + '</div>';

        banner.querySelector('.recovery-btn-restore').addEventListener('click', function () {
            function apply(restored) {
                formDef.restore(restored);
                acknowledgeDraft(formDef); // Mark as handled so it won't reappear
                if (typeof showPane === 'function') showPane(formDef.pane, null);
                banner.classList.add('recovery-banner-hide');
                setTimeout(function () { banner.remove(); }, 300);
                if (typeof showToast === 'function') showToast('Borrador restaurado: ' + formDef.label, 'success');
            }
            if (typeof formDef.prepare === 'function') {
                formDef.prepare(data).then(apply, function () { apply(data); });
            } else {
                apply(data);
            }
        });

        banner.querySelector('.recovery-btn-dismiss').addEventListener('click', function () {
            clearDraft(formDef); // Remove draft entirely
            banner.classList.add('recovery-banner-hide');
            setTimeout(function () { banner.remove(); }, 300);
        });

        document.body.appendChild(banner);
        requestAnimationFrame(function () {
            requestAnimationFrame(function () { banner.classList.add('recovery-banner-show'); });
        });

        // Auto-dismiss after 10 seconds to avoid being annoying
        setTimeout(function () {
            if (banner.parentNode) {
                acknowledgeDraft(formDef);
                banner.classList.add('recovery-banner-hide');
                setTimeout(function () { banner.remove(); }, 300);
            }
        }, 10000);
    }

    // ── Periodic autosave ──────────────────────────────────────
    function startAutosave() {
        setInterval(function () {
            Object.keys(FORMS).forEach(function (key) {
                // Auto-acknowledge during active session so banner won't show on reload
                saveDraft(FORMS[key], function () { acknowledgeDraft(FORMS[key]); });
            });
        }, AUTOSAVE_INTERVAL);
    }

    // ── Wire up clear buttons to also clear drafts ─────────────
    function hookClearButtons() {
        // Patch existing clear functions to also remove drafts
        var origClearRdl = window.clearRdlForm;
        if (origClearRdl) {
            window.clearRdlForm = function () {
                origClearRdl();
                clearDraft(FORMS.rdl);
            };
        }
        var origClearRdlTg = window.clearRdlTelegramForm;
        if (origClearRdlTg) {
            window.clearRdlTelegramForm = function () {
                origClearRdlTg();
                clearDraft(FORMS.rdlTelegram);
            };
        }
        var origClearUat = window.clearUatForm;
        if (origClearUat) {
            window.clearUatForm = function () {
                origClearUat();
                clearDraft(FORMS.uat);
            };
        }
    }

    // ── Init ───────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        // Check for recoverable drafts — only show banner if:
        // 1. Draft exists and is not empty
        // 2. Draft has NOT been acknowledged already
        // 3. Draft is older than 10 seconds (not from this page load's autosave)
        var now = Date.now();
        Object.keys(FORMS).forEach(function (key) {
            var formDef = FORMS[key];
            var data = loadDraft(formDef);
            if (!data || formDef.isEmpty(data)) return;
            if (isDraftAcknowledged(formDef)) return;

            var ts = getDraftTimestamp(formDef);
            // Skip if draft is very fresh (< 10s) — it's from the autosave of the current session
            if (ts && (now - ts.getTime()) < 10000) return;

            showRecoveryBanner(formDef, data, ts);
        });

        hookClearButtons();
        startAutosave();

        // Save on page unload WITHOUT acknowledging — this is the crash recovery path
        window.addEventListener('beforeunload', function () {
            Object.keys(FORMS).forEach(function (key) {
                var formDef = FORMS[key];
                try {
                    var data = formDef.collect();
                    if (formDef.isEmpty(data)) return;
                    persistDraft(formDef, data, false);
                    // Intentionally do NOT acknowledge — next load should show recovery banner
                    localStorage.removeItem(formDef.key + DRAFT_ACK_SUFFIX);
                } catch (e) {}
            });
        });
    });
})();
