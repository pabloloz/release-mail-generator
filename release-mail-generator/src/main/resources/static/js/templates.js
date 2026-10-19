/* ── Templates — Save & Reuse form templates ─────────────────────────────── */
/*
 * Modelo de estado (por módulo):
 *   current[modKey] = { id, name, snapshot, savedAt }
 *   - id === null  → "plantilla nueva" (nunca guardada en esta sesión de edición)
 *   - id !== null  → estamos editando la plantilla con ese id (mismo id/nombre se conservan)
 *   - snapshot     → JSON.stringify(data) tal como quedó en el último guardado (baseline
 *                    para saber si hay cambios sin guardar)
 *
 * Este estado vive solo en memoria (no se persiste): al recargar la página se vuelve a
 * "plantilla nueva", igual que antes. Lo que sí persiste es el propio storage de plantillas
 * (localStorage, formato sin cambios: {id,name,module,moduleLabel,icon,data,createdAt,updatedAt}),
 * por lo que las plantillas ya guardadas siguen funcionando sin migración.
 */
(function () {
    'use strict';

    var STORAGE_KEY = 'rnq-templates';

    // ── Module definitions (which fields to capture per module) ──
    var MODULES = {
        releases: {
            label: 'Correo de Liberación',
            icon: '📧',
            pane: 'releases',
            clearFn: 'clearAll',
            collect: function () {
                return typeof collectReleaseFormData === 'function' ? collectReleaseFormData() : {};
            },
            restore: function (d) {
                if (typeof restoreReleaseFormData === 'function') restoreReleaseFormData(d);
            }
        },
        messages: {
            label: 'Telegram',
            icon: '✈️',
            pane: 'messages',
            clearFn: 'clearReleaseMessageForm',
            collect: function () {
                return typeof collectReleaseMessageData === 'function'
                    ? { ...collectReleaseMessageData(), messageChannel: (document.getElementById('messageChannel') || {}).value || 'telegram' }
                    : {};
            },
            restore: function (d) {
                if (typeof restoreMessageFormDraft === 'function') restoreMessageFormDraft(d);
            }
        },
        uat: {
            label: 'VoBo UAT',
            icon: '✅',
            pane: 'uat',
            clearFn: 'clearUatForm',
            collect: function () {
                return {
                    uatRfcNumber: gv('uatRfcNumber'),
                    uatRfcName: gv('uatRfcName'),
                    uatSaludo: gv('uatSaludo'),
                    uatAdjunto: gv('uatAdjunto'),
                    uatRequerimientos: gv('uatRequerimientos'),
                    reqImages: typeof getReqImagesData === 'function' ? getReqImagesData() : [],
                    uatNota: gv('uatNota'),
                    uatCierre: gv('uatCierre'),
                    uatEditorHtml: (document.getElementById('uatRichEditor') || {}).innerHTML || ''
                };
            },
            restore: function (d) {
                setv('uatRfcNumber', d.uatRfcNumber);
                setv('uatRfcName', d.uatRfcName);
                setv('uatSaludo', d.uatSaludo);
                setv('uatAdjunto', d.uatAdjunto);
                setv('uatRequerimientos', d.uatRequerimientos);
                setv('uatNota', d.uatNota);
                setv('uatCierre', d.uatCierre);
                // Restore requirements images
                if (typeof clearReqImages === 'function') clearReqImages();
                var images = d.reqImages || (d.reqImageData ? [d.reqImageData] : []);
                images.forEach(function(uri) {
                    if (uri && typeof addReqImage === 'function') addReqImage(uri, 'Imagen de plantilla');
                });
                // Restore rich editor content
                var editor = document.getElementById('uatRichEditor');
                if (editor) editor.innerHTML = d.uatEditorHtml || '';
            }
        },
        rdl: {
            label: 'Correo RDL',
            icon: '📊',
            pane: 'rdl',
            clearFn: 'clearRdlForm',
            collect: function () {
                return typeof collectRdlPayload === 'function' ? collectRdlPayload() : {};
            },
            restore: function (d) {
                if (!d) return;
                // Restore global fields
                if (d.rdlReleaseDate) {
                    // Parse "dd/MM/yyyy hh:mm a" back to date + time
                    var parts = (d.rdlReleaseDate || '').split(' ');
                    if (parts.length >= 1) {
                        var dateParts = parts[0].split('/');
                        if (dateParts.length === 3) {
                            setv('rdlReleaseDatePicker', dateParts[2] + '-' + dateParts[1] + '-' + dateParts[0]);
                        }
                        if (parts.length >= 3) {
                            var timeStr = parts[1] + ' ' + parts[2];
                            var timeSel = document.getElementById('rdlReleaseTimePicker');
                            if (timeSel) {
                                for (var i = 0; i < timeSel.options.length; i++) {
                                    if (timeSel.options[i].value === timeStr) { timeSel.selectedIndex = i; break; }
                                }
                            }
                        }
                    }
                }
                setv('rdlReleaseUrl', d.rdlReleaseUrl);
                // Restore RDL entries
                var rdls = d.rdls || [];
                if (rdls.length === 0) return;
                var container = document.getElementById('rdlEntries');
                if (!container) return;
                // Clear existing entries
                container.innerHTML = '';
                // Add entries
                for (var ri = 0; ri < rdls.length; ri++) {
                    if (typeof addRdlEntry === 'function') addRdlEntry();
                }
                // Fill values after DOM is ready
                setTimeout(function () {
                    var entries = container.querySelectorAll('.rdl-entry');
                    rdls.forEach(function (rdl, idx) {
                        var entry = entries[idx];
                        if (!entry) return;
                        var sv = function (name, val) {
                            var el = entry.querySelector('[name="rdls[' + idx + '].' + name + '"]');
                            if (el && val != null) {
                                if (el.type === 'checkbox') { el.checked = !!val; if (el.onchange) el.onchange(); }
                                else el.value = val;
                            }
                        };
                        sv('rdlReportName', rdl.rdlReportName);
                        sv('rdlReportFolder', rdl.rdlReportFolder);
                        sv('rdlUrlMegang', rdl.rdlUrlMegang);
                        sv('rdlUrlNtrs02', rdl.rdlUrlNtrs02);
                        sv('rdlPathMegang', rdl.rdlPathMegang);
                        sv('rdlPathNtrs02', rdl.rdlPathNtrs02);
                        sv('rdlProject', rdl.rdlProject);
                        // SPs and Scripts — toggle visibility
                        if (rdl.hasRdlSp) {
                            sv('hasRdlSp', true);
                            var spGrp = entry.querySelector('.rdl-sp-group');
                            if (spGrp) spGrp.style.display = 'block';
                            sv('rdlSpName', rdl.rdlSpName);
                            sv('rdlSpTicket', rdl.rdlSpTicket);
                        }
                        if (rdl.hasRdlScript) {
                            sv('hasRdlScript', true);
                            var scriptGrp = entry.querySelector('.rdl-script-group');
                            if (scriptGrp) scriptGrp.style.display = 'block';
                            sv('rdlScriptName', rdl.rdlScriptName);
                            sv('rdlScriptPath', rdl.rdlScriptPath);
                        }
                    });
                    if (typeof updateRdlPaths === 'function') updateRdlPaths();
                }, 100);
            }
        },
        'rdl-telegram': {
            label: 'Telegram RDL',
            icon: '✈️',
            pane: 'rdl-telegram',
            clearFn: 'clearRdlTelegramForm',
            collect: function () {
                return typeof collectRdlTelegramPayload === 'function' ? collectRdlTelegramPayload() : {};
            },
            restore: function (d) {
                if (!d) return;
                setv('rdlTgAction', d.rdlAction || '');
                if (d.rdlReleaseDate) {
                    var parts = (d.rdlReleaseDate || '').split(' ');
                    if (parts.length >= 1) {
                        var dp = parts[0].split('/');
                        if (dp.length === 3) setv('rdlTgDate', dp[2] + '-' + dp[1] + '-' + dp[0]);
                        if (parts.length >= 3) {
                            var ts = parts[1] + ' ' + parts[2];
                            var sel = document.getElementById('rdlTgTime');
                            if (sel) for (var i = 0; i < sel.options.length; i++) { if (sel.options[i].value === ts) { sel.selectedIndex = i; break; } }
                        }
                    }
                }
                var rdls = d.rdls || [];
                ['rdlTgRdlsContainer','rdlTgSpsContainer','rdlTgProjectsContainer'].forEach(function(id) {
                    var c = document.getElementById(id); if (c) c.innerHTML = '';
                });
                rdls.forEach(function (r) {
                    if (r.rdlReportName && typeof addRdlTgRdlRow === 'function') {
                        addRdlTgRdlRow();
                        var last = document.querySelector('#rdlTgRdlsContainer .dynamic-item:last-child input');
                        if (last) last.value = r.rdlReportName;
                    }
                    if (r.rdlSpName && typeof addRdlTgSpRow === 'function') {
                        addRdlTgSpRow();
                        var lastSp = document.querySelector('#rdlTgSpsContainer .dynamic-item:last-child input');
                        if (lastSp) lastSp.value = r.rdlSpName;
                    }
                    if (r.rdlProject && typeof addRdlTgProjectRow === 'function') {
                        addRdlTgProjectRow();
                        var lastPr = document.querySelector('#rdlTgProjectsContainer .dynamic-item:last-child input');
                        if (lastPr) lastPr.value = r.rdlProject;
                    }
                });
            }
        }
    };

    function gv(id) { var el = document.getElementById(id); return el ? el.value : ''; }
    function setv(id, val) { var el = document.getElementById(id); if (el && val != null) el.value = val; }

    // ── Storage ─────────────────────────────────────────────────
    function loadAll() {
        try { return JSON.parse(localStorage.getItem(STORAGE_KEY)) || []; }
        catch (e) { return []; }
    }
    function saveAll(list) {
        try { localStorage.setItem(STORAGE_KEY, JSON.stringify(list)); } catch (e) {}
    }

    // ── Detect current module ───────────────────────────────────
    function getCurrentModule() {
        var key = getCurrentModuleKey();
        return key ? MODULES[key] : null;
    }
    function getCurrentModuleKey() {
        var active = document.querySelector('.pane.active');
        if (!active) return null;
        var id = active.id.replace('pane-', '');
        return MODULES[id] ? id : null;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Estado "¿qué plantilla estoy editando?" — en memoria, por módulo
    // ══════════════════════════════════════════════════════════════════════
    var current = {};
    function getState(modKey) {
        if (!current[modKey]) current[modKey] = { id: null, name: null, snapshot: null, savedAt: null };
        return current[modKey];
    }
    function setState(modKey, patch) {
        current[modKey] = Object.assign(getState(modKey), patch);
    }
    function resetState(modKey) {
        current[modKey] = { id: null, name: null, snapshot: null, savedAt: null };
    }
    function newTemplateId() {
        return Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
    }

    /**
     * Snapshot del estado "intacto" de cada módulo, capturado poco después de cargar la
     * página (una vez que otros scripts ya inicializaron valores por defecto: año/semana
     * actuales, una fila de RDL vacía, canal de Telegram por defecto, etc.). Sirve como
     * referencia para el módulo cuando NO se está editando una plantilla guardada: así
     * evitamos falsos positivos de "cambios sin guardar" causados por esos valores por
     * defecto, y solo se considera "dirty" lo que el usuario realmente modificó.
     */
    var pristine = {};
    function capturePristine(modKey) {
        var mod = MODULES[modKey];
        if (!mod) return;
        try { pristine[modKey] = JSON.stringify(mod.collect()); } catch (e) { pristine[modKey] = null; }
    }

    function isDirty(modKey) {
        var mod = MODULES[modKey];
        if (!mod) return false;
        var state = getState(modKey);
        var currentSnap;
        try { currentSnap = JSON.stringify(mod.collect()); } catch (e) { return false; }
        if (state.id) return currentSnap !== state.snapshot;
        var base = pristine[modKey];
        if (base == null) return false; // aún no capturado — no molestar de más
        return currentSnap !== base;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Guardar — botón principal (Guardar plantilla / Guardar cambios)
    // ══════════════════════════════════════════════════════════════════════
    function commitSave(modKey, id, name, isNew) {
        var mod = MODULES[modKey];
        var data = mod.collect();
        var templates = loadAll();
        var idx = templates.findIndex(function (t) { return t.id === id; });
        var now = new Date().toISOString();
        if (idx >= 0) {
            templates[idx].data = data;
            templates[idx].name = name;
            templates[idx].updatedAt = now;
        } else {
            templates.push({
                id: id, name: name, module: modKey, moduleLabel: mod.label, icon: mod.icon,
                data: data, createdAt: now, updatedAt: now
            });
        }
        saveAll(templates);
        setState(modKey, { id: id, name: name, snapshot: JSON.stringify(data), savedAt: now });
        renderStatus(modKey);
        renderTemplatesList();
        showToast(isNew ? ('Plantilla "' + name + '" guardada') : ('Cambios guardados en "' + name + '"'), 'success');
        return true;
    }

    /** Botón principal: crea (pide nombre la 1ª vez) o actualiza directamente si ya existe. */
    window.saveAsTemplate = function () {
        var modKey = getCurrentModuleKey();
        var mod = getCurrentModule();
        if (!mod) { showToast('Navega a un formulario antes de guardar una plantilla.', 'error'); return; }

        var state = getState(modKey);
        if (state.id) {
            // Plantilla existente → sobrescribir directamente, sin pedir nombre.
            commitSave(modKey, state.id, state.name, false);
            return;
        }

        var name = prompt('Nombre de la plantilla:\n\nEj: VoBo Servicios, RFC Equipos, Liberación Semanal');
        if (!name || !name.trim()) return;
        name = name.trim();

        var templates = loadAll();
        var exists = templates.findIndex(function (t) { return t.module === modKey && t.name === name; });
        if (exists >= 0) {
            if (!confirm('Ya existe una plantilla "' + name + '" para ' + mod.label + '. ¿Sobreescribir?')) return;
            commitSave(modKey, templates[exists].id, name, false);
            return;
        }
        commitSave(modKey, newTemplateId(), name, true);
    };

    /** Botón secundario: siempre pide nombre y crea una plantilla independiente. */
    window.saveTemplateAsNew = function () {
        var modKey = getCurrentModuleKey();
        var mod = getCurrentModule();
        if (!mod) { showToast('Navega a un formulario antes de guardar una plantilla.', 'error'); return; }

        var state = getState(modKey);
        var suggestion = state.name ? 'Copia de ' + state.name : '';
        var name = prompt('Guardar como nueva plantilla\n\nNombre:', suggestion);
        if (!name || !name.trim()) return;
        name = name.trim();

        var templates = loadAll();
        var exists = templates.findIndex(function (t) { return t.module === modKey && t.name === name; });
        if (exists >= 0) {
            if (!confirm('Ya existe una plantilla "' + name + '" para ' + mod.label + '. ¿Sobreescribir?')) return;
            commitSave(modKey, templates[exists].id, name, false);
            return;
        }
        commitSave(modKey, newTemplateId(), name, true);
    };

    // ══════════════════════════════════════════════════════════════════════
    // Cargar plantilla (con guardia de cambios sin guardar)
    // ══════════════════════════════════════════════════════════════════════
    function doLoadTemplate(id) {
        var templates = loadAll();
        var tpl = templates.find(function (t) { return t.id === id; });
        if (!tpl) { showToast('Plantilla no encontrada', 'error'); return; }

        var mod = MODULES[tpl.module];
        if (!mod) { showToast('Módulo no disponible', 'error'); return; }

        mod.restore(tpl.data);
        setState(tpl.module, {
            id: tpl.id, name: tpl.name,
            snapshot: JSON.stringify(tpl.data),
            savedAt: tpl.updatedAt || tpl.createdAt
        });
        if (typeof _originalShowPane === 'function') _originalShowPane(tpl.module, null);
        closeTemplatesPanel();
        renderStatus(tpl.module);
        showToast('Plantilla "' + tpl.name + '" cargada', 'success');
    }

    window.loadTemplate = function (id) {
        guardUnsavedChanges(function () { doLoadTemplate(id); });
    };

    // ── Delete template ────────────────────────────────────────
    window.deleteTemplate = function (id) {
        if (!confirm('¿Eliminar esta plantilla?')) return;
        var templates = loadAll().filter(function (t) { return t.id !== id; });
        saveAll(templates);
        // Si estaba editando justo esta plantilla, volver a estado "nueva"
        Object.keys(current).forEach(function (modKey) {
            if (current[modKey] && current[modKey].id === id) { resetState(modKey); renderStatus(modKey); }
        });
        showToast('Plantilla eliminada', 'success');
        renderTemplatesList();
    };

    // ── Rename template ────────────────────────────────────────
    window.renameTemplate = function (id) {
        var templates = loadAll();
        var tpl = templates.find(function (t) { return t.id === id; });
        if (!tpl) return;
        var name = prompt('Nuevo nombre:', tpl.name);
        if (!name || !name.trim()) return;
        tpl.name = name.trim();
        tpl.updatedAt = new Date().toISOString();
        saveAll(templates);
        // Si estaba editando esta plantilla, refrescar el nombre en el estado actual
        Object.keys(current).forEach(function (modKey) {
            if (current[modKey] && current[modKey].id === id) { setState(modKey, { name: tpl.name }); renderStatus(modKey); }
        });
        renderTemplatesList();
    };

    // ── Duplicate template ─────────────────────────────────────
    window.duplicateTemplate = function (id) {
        var templates = loadAll();
        var tpl = templates.find(function (t) { return t.id === id; });
        if (!tpl) { showToast('Plantilla no encontrada', 'error'); return; }
        var name = prompt('Nombre para la copia:', 'Copia de ' + tpl.name);
        if (!name || !name.trim()) return;
        var copy = {
            id: newTemplateId(),
            name: name.trim(),
            module: tpl.module,
            moduleLabel: tpl.moduleLabel,
            icon: tpl.icon,
            data: JSON.parse(JSON.stringify(tpl.data)), // clonado profundo: conserva imágenes/HTML/listas tal cual
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
        };
        templates.push(copy);
        saveAll(templates);
        showToast('Plantilla duplicada como "' + copy.name + '"', 'success');
        renderTemplatesList();
    };

    // ══════════════════════════════════════════════════════════════════════
    // Confirmación de cambios sin guardar (navegación protegida)
    // ══════════════════════════════════════════════════════════════════════
    function ensureUnsavedModal() {
        var overlay = document.getElementById('tplUnsavedOverlay');
        if (overlay) return overlay;
        overlay = document.createElement('div');
        overlay.id = 'tplUnsavedOverlay';
        overlay.className = 'tpl-confirm-overlay';
        overlay.innerHTML =
            '<div class="tpl-confirm-box">' +
            '  <div class="tpl-confirm-title">⚠️ Tienes cambios sin guardar</div>' +
            '  <div class="tpl-confirm-text">Has realizado cambios desde el último guardado.</div>' +
            '  <div class="tpl-confirm-actions">' +
            '    <button type="button" class="tpl-btn-save" id="tplUnsavedSave">Guardar cambios</button>' +
            '    <button type="button" class="tpl-btn-discard" id="tplUnsavedDiscard">Descartar cambios</button>' +
            '    <button type="button" class="tpl-btn-cancel" id="tplUnsavedCancel">Cancelar</button>' +
            '  </div>' +
            '</div>';
        document.body.appendChild(overlay);
        return overlay;
    }

    /** Si el módulo activo tiene cambios sin guardar, pide confirmación antes de `proceed()`. */
    function guardUnsavedChanges(proceed) {
        var modKey = getCurrentModuleKey();
        if (!modKey || !isDirty(modKey)) { proceed(); return; }

        var overlay = ensureUnsavedModal();
        overlay.classList.add('open');

        var saveBtn = document.getElementById('tplUnsavedSave');
        var discardBtn = document.getElementById('tplUnsavedDiscard');
        var cancelBtn = document.getElementById('tplUnsavedCancel');

        function close() { overlay.classList.remove('open'); saveBtn.onclick = null; discardBtn.onclick = null; cancelBtn.onclick = null; }

        saveBtn.onclick = function () {
            var state = getState(modKey);
            if (state.id) {
                commitSave(modKey, state.id, state.name, false);
                close();
                proceed();
            } else {
                var name = prompt('Nombre de la plantilla:\n\nEj: VoBo Servicios, RFC Equipos, Liberación Semanal');
                if (!name || !name.trim()) return; // se queda en el modal
                commitSave(modKey, newTemplateId(), name.trim(), true);
                close();
                proceed();
            }
        };
        discardBtn.onclick = function () {
            var mod = MODULES[modKey];
            var state = getState(modKey);
            if (state.id && state.snapshot) {
                try { mod.restore(JSON.parse(state.snapshot)); } catch (e) {}
            } else if (pristine[modKey]) {
                try { mod.restore(JSON.parse(pristine[modKey])); } catch (e) {}
            } else if (mod.clearFn && typeof window[mod.clearFn] === 'function') {
                window[mod.clearFn]();
            }
            renderStatus(modKey);
            close();
            proceed();
        };
        cancelBtn.onclick = function () { close(); };
    }

    // ── Interceptar navegación (showPane) para proteger cambios sin guardar ──
    var _originalShowPane = window.showPane;
    if (typeof _originalShowPane === 'function') {
        window.showPane = function (name, triggerEl) {
            var currentKey = getCurrentModuleKey();
            if (!currentKey || currentKey === name || !isDirty(currentKey)) {
                _originalShowPane(name, triggerEl);
                return;
            }
            guardUnsavedChanges(function () { _originalShowPane(name, triggerEl); });
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // Indicador de estado (nombre de plantilla + último guardado + cambios)
    // ══════════════════════════════════════════════════════════════════════
    function timeAgo(iso) {
        if (!iso) return '';
        try {
            var d = new Date(iso);
            if (typeof getTimeAgo === 'function') return getTimeAgo(d);
            return d.toLocaleString('es-MX');
        } catch (e) { return ''; }
    }

    function renderStatus(modKey) {
        var mod = MODULES[modKey];
        if (!mod) return;
        var bar = document.getElementById('tpl-status-' + modKey);
        var saveBtn = document.getElementById('btn-tpl-save-' + modKey);
        var saveAsBtn = document.getElementById('btn-tpl-saveas-' + modKey);
        var state = getState(modKey);

        if (saveBtn) {
            saveBtn.innerHTML = state.id ? '&#x1F4BE; Guardar cambios' : '&#x1F4BE; Guardar plantilla';
            saveBtn.title = state.id ? 'Actualizar esta plantilla' : 'Guardar como plantilla';
        }
        if (saveAsBtn) saveAsBtn.style.display = state.id ? '' : 'none';

        if (!bar) return;
        if (!state.id) {
            bar.innerHTML = '<span class="tpl-status-name">🆕 Nueva plantilla — aún no guardada</span>';
            return;
        }
        var dirty = isDirty(modKey);
        var dirtyHtml = dirty
            ? '<span class="tpl-status-dirty">● Cambios sin guardar</span>'
            : '<span class="tpl-status-clean">✓ Guardado</span>';
        bar.innerHTML =
            '<span class="tpl-status-name">✏️ Editando plantilla: “' + esc(state.name) + '”</span>' +
            '<span class="tpl-status-sep">·</span>' +
            '<span>Último guardado: ' + esc(timeAgo(state.savedAt) || '—') + '</span>' +
            '<span class="tpl-status-sep">·</span>' + dirtyHtml;
    }

    function renderAllStatuses() {
        Object.keys(MODULES).forEach(renderStatus);
    }

    // Actualiza el indicador periódicamente (barato: solo compara JSON de campos del form).
    window.setInterval(renderAllStatuses, 2000);
    window.addEventListener('DOMContentLoaded', function () {
        // Esperamos a que el resto de scripts terminen su inicialización (año/semana
        // por defecto, primera fila de RDL, etc.) antes de fijar el snapshot "intacto".
        setTimeout(function () {
            Object.keys(MODULES).forEach(capturePristine);
            renderAllStatuses();
        }, 300);
    });

    // ── Panel UI ───────────────────────────────────────────────
    window.openTemplatesPanel = function () {
        var panel = document.getElementById('templatesPanel');
        if (!panel) return;
        // Auto-select filter to current module
        var modKey = getCurrentModuleKey();
        var filterEl = document.getElementById('templatesFilter');
        if (filterEl && modKey && MODULES[modKey]) {
            filterEl.value = modKey;
        }
        panel.classList.add('open');
        renderTemplatesList();
    };
    window.closeTemplatesPanel = function () {
        var panel = document.getElementById('templatesPanel');
        if (panel && panel.classList.contains('open')) {
            panel.style.opacity = '0';
            setTimeout(function () { panel.classList.remove('open'); panel.style.opacity = ''; }, 250);
        }
    };

    function renderTemplatesList() {
        var container = document.getElementById('templatesList');
        if (!container) return;
        var templates = loadAll();
        var filter = (document.getElementById('templatesFilter') || {}).value || 'ALL';


        var filtered = filter === 'ALL' ? templates : templates.filter(function (t) { return t.module === filter; });
        var filterMod = MODULES[filter];
        var filterLabel = filterMod ? filterMod.label : '';

        if (filtered.length === 0) {
            container.innerHTML = '<div class="tpl-empty">'
                + '<div style="font-size:1.8rem;opacity:.3;margin-bottom:8px;">📑</div>'
                + (templates.length === 0
                    ? 'No hay plantillas guardadas.<br><span style="font-size:.72rem;">Navega a un formulario, llena los datos y haz clic en <strong>Guardar plantilla</strong>.</span>'
                    : 'No hay plantillas de <strong>' + esc(filterLabel || filter) + '</strong>.<br><span style="font-size:.72rem;">Guarda una desde el formulario correspondiente.</span>')
                + '</div>';
            return;
        }

        // Group by module
        var groups = {};
        filtered.forEach(function (t) {
            var key = t.moduleLabel || t.module;
            if (!groups[key]) groups[key] = [];
            groups[key].push(t);
        });

        var html = '';
        Object.keys(groups).forEach(function (group) {
            html += '<div class="tpl-group-header">' + esc(group) + '</div>';
            groups[group].forEach(function (t) {
                var date = new Date(t.updatedAt || t.createdAt);
                var dateStr = date.toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' });
                html += '<div class="tpl-item">'
                    + '<div class="tpl-item-icon">' + (t.icon || '📄') + '</div>'
                    + '<div class="tpl-item-body" onclick="loadTemplate(\'' + t.id + '\')">'
                    + '<div class="tpl-item-name">' + esc(t.name) + '</div>'
                    + '<div class="tpl-item-meta">' + dateStr + '</div>'
                    + '</div>'
                    + '<div class="tpl-item-actions">'
                    + '<button class="tpl-action-btn" onclick="duplicateTemplate(\'' + t.id + '\')" title="Duplicar">⧉</button>'
                    + '<button class="tpl-action-btn" onclick="renameTemplate(\'' + t.id + '\')" title="Renombrar">✏️</button>'
                    + '<button class="tpl-action-btn tpl-action-delete" onclick="deleteTemplate(\'' + t.id + '\')" title="Eliminar">✕</button>'
                    + '</div>'
                    + '</div>';
            });
        });

        container.innerHTML = html;
    }

    window.filterTemplates = function () { renderTemplatesList(); };

    function esc(s) {
        if (!s) return '';
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
})();
