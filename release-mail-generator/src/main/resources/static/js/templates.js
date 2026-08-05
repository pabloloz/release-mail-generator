/* ── Templates — Save & Reuse form templates ─────────────────────────────── */
(function () {
    'use strict';

    var STORAGE_KEY = 'rnq-templates';

    // ── Module definitions (which fields to capture per module) ──
    var MODULES = {
        releases: {
            label: 'Correo de Liberación',
            icon: '📧',
            pane: 'releases',
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
                if (editor && d.uatEditorHtml) editor.innerHTML = d.uatEditorHtml;
            }
        },
        rdl: {
            label: 'Correo RDL',
            icon: '📊',
            pane: 'rdl',
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
        var active = document.querySelector('.pane.active');
        if (!active) return null;
        var id = active.id.replace('pane-', '');
        return MODULES[id] || null;
    }
    function getCurrentModuleKey() {
        var active = document.querySelector('.pane.active');
        if (!active) return null;
        return active.id.replace('pane-', '');
    }

    // ── Save template ──────────────────────────────────────────
    window.saveAsTemplate = function () {
        var mod = getCurrentModule();
        var modKey = getCurrentModuleKey();
        if (!mod) { showToast('Navega a un formulario antes de guardar una plantilla.', 'error'); return; }

        var name = prompt('Nombre de la plantilla:\n\nEj: VoBo Servicios, RFC Equipos, Liberación Semanal');
        if (!name || !name.trim()) return;
        name = name.trim();

        var data = mod.collect();
        var templates = loadAll();

        // Check for duplicate name in same module
        var exists = templates.findIndex(function (t) { return t.module === modKey && t.name === name; });
        if (exists >= 0) {
            if (!confirm('Ya existe una plantilla "' + name + '" para ' + mod.label + '. ¿Sobreescribir?')) return;
            templates[exists].data = data;
            templates[exists].updatedAt = new Date().toISOString();
        } else {
            templates.push({
                id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
                name: name,
                module: modKey,
                moduleLabel: mod.label,
                icon: mod.icon,
                data: data,
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString()
            });
        }

        saveAll(templates);
        showToast('Plantilla "' + name + '" guardada', 'success');
        renderTemplatesList();
    };

    // ── Load template ──────────────────────────────────────────
    window.loadTemplate = function (id) {
        var templates = loadAll();
        var tpl = templates.find(function (t) { return t.id === id; });
        if (!tpl) { showToast('Plantilla no encontrada', 'error'); return; }

        var mod = MODULES[tpl.module];
        if (!mod) { showToast('Módulo no disponible', 'error'); return; }

        mod.restore(tpl.data);
        if (typeof showPane === 'function') showPane(tpl.module, null);
        closeTemplatesPanel();
        showToast('Plantilla "' + tpl.name + '" cargada', 'success');
    };

    // ── Delete template ────────────────────────────────────────
    window.deleteTemplate = function (id) {
        if (!confirm('¿Eliminar esta plantilla?')) return;
        var templates = loadAll().filter(function (t) { return t.id !== id; });
        saveAll(templates);
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
        renderTemplatesList();
    };

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
                    ? 'No hay plantillas guardadas.<br><span style="font-size:.72rem;">Navega a un formulario, llena los datos y haz clic en <strong>Guardar como plantilla</strong>.</span>'
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
