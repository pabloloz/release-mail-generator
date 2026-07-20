/* ── Form Validation — Visual warnings before document generation ─────────── */
(function () {
    'use strict';

    // ── Validation rules per form ─────────────────────────────
    var RULES = {
        releases: {
            required: [
                { check: function () { return getVal(null, '[name="publishDate"]'); }, label: 'Fecha de publicación', selector: '[name="publishDate"]' },
                { check: function () { return hasAnyArtifact(); }, label: 'Al menos un artefacto seleccionado', field: 'hasModules' }
            ],
            warnings: [
                { check: function () { return getVal('vPubYear') && getVal('vPubMinor'); }, label: 'Versión a publicar (recomendado)' },
                { check: function () { return getVal('vRbYear') && getVal('vRbMinor'); }, label: 'Versión de rollback (recomendado)' },
                { check: function () { return getVal(null, '[name="projects"]'); }, label: 'Proyectos / RFCs (recomendado)' },
                { check: function () { return getVal(null, '[name="branchModules"]') || getVal(null, '[name="branchWinter"]'); }, label: 'Branches de compilación (recomendado)' }
            ]
        },
        messages: {
            required: [
                { check: function () { return document.querySelectorAll('input[name="telegramModules"]:checked').length > 0; }, label: 'Módulos afectados (selecciona al menos uno)', selector: 'input[name="telegramModules"]' },
                { check: function () { return getVal('telegramPublishDate'); }, label: 'Fecha de publicación', field: 'telegramPublishDate' },
                { check: function () { return getVal('telegramVersionModule'); }, label: 'Versión de módulo', field: 'telegramVersionModule' },
                { check: function () { return getVal('telegramRollbackVersion'); }, label: 'Versión de rollback', field: 'telegramRollbackVersion' }
            ],
            warnings: [
                { check: function () { return getVal('telegramBranchModules'); }, label: 'Branch de módulos (recomendado)' },
                { check: function () { return hasDynamicContent('telegramChangesContainer'); }, label: 'Cambios de release (recomendado)' }
            ]
        },
        rdl: {
            required: [
                { check: function () { return getVal('rdlReleaseDatePicker'); }, label: 'Fecha de liberación', field: 'rdlReleaseDatePicker' },
                { check: function () { return hasRdlEntryName(); }, label: 'Nombre de al menos un reporte RDL', selector: '[name="rdls[0].rdlReportName"]' }
            ],
            warnings: [
                { check: function () { return getVal('rdlReleaseTimePicker'); }, label: 'Hora de liberación (recomendado)' }
            ]
        },
        'rdl-telegram': {
            required: [
                { check: function () { return getVal('rdlTgDate'); }, label: 'Fecha de publicación', field: 'rdlTgDate' },
                { check: function () { return hasDynamicContent('rdlTgRdlsContainer'); }, label: 'Al menos un RDL ingresado', selector: '#rdlTgRdlsContainer input' },
                { check: function () { return getVal('rdlTgAction'); }, label: 'Descripción de la acción', field: 'rdlTgAction' }
            ],
            warnings: []
        },
        uat: {
            required: [
                { check: function () { return getVal('uatRfcNumber'); }, label: 'Número de RFC', field: 'uatRfcNumber' },
                { check: function () { return getVal('uatRfcName'); }, label: 'Nombre del RFC', field: 'uatRfcName' }
            ],
            warnings: [
                { check: function () { return getVal('uatRequerimientos') || getEditorContent(); }, label: 'Requerimientos o evidencias (recomendado)' }
            ]
        }
    };

    // ── Helpers ────────────────────────────────────────────────
    function getVal(id, selector) {
        var el = id ? document.getElementById(id) : document.querySelector(selector);
        if (!el) return '';
        return (el.value || '').trim();
    }

    function hasAnyArtifact() {
        return document.getElementById('hasModules')?.checked
            || document.getElementById('hasCitrix')?.checked
            || document.getElementById('hasDll')?.checked
            || document.getElementById('hasWinterX')?.checked
            || document.getElementById('hasScripts')?.checked;
    }

    function hasRdlEntryName() {
        var el = document.querySelector('[name="rdls[0].rdlReportName"]');
        return el && el.value.trim().length > 0;
    }

    function hasDynamicContent(containerId) {
        var inputs = document.querySelectorAll('#' + containerId + ' input');
        for (var i = 0; i < inputs.length; i++) {
            if (inputs[i].value.trim()) return true;
        }
        return false;
    }

    function getEditorContent() {
        var editor = document.getElementById('uatRichEditor');
        return editor ? editor.textContent.trim() : '';
    }

    // ── Core validation ───────────────────────────────────────
    /**
     * Validates a form and returns { valid: boolean, errors: [], warnings: [] }
     * Also applies visual indicators.
     */
    window.validateForm = function (formKey) {
        var rules = RULES[formKey];
        if (!rules) return { valid: true, errors: [], warnings: [] };

        clearValidationState();

        var errors = [];
        var warnings = [];

        // Check required
        (rules.required || []).forEach(function (rule) {
            if (!rule.check()) {
                errors.push(rule.label);
                markField(rule.field, rule.selector, 'error');
            }
        });

        // Check warnings
        (rules.warnings || []).forEach(function (rule) {
            if (!rule.check()) {
                warnings.push(rule.label);
            }
        });

        // Show feedback
        if (errors.length > 0) {
            showValidationSummary(errors, warnings);
            // Shake the first error field
            var firstRule = rules.required.find(function (r) { return !r.check(); });
            if (firstRule) {
                var el = firstRule.field ? document.getElementById(firstRule.field) : document.querySelector(firstRule.selector);
                if (el) {
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    if (typeof shakeElement === 'function') shakeElement(el.closest('.section-block') || el);
                }
            }
        } else if (warnings.length > 0) {
            showWarningOnly(warnings);
        }

        return { valid: errors.length === 0, errors: errors, warnings: warnings };
    };

    // ── Visual markers ────────────────────────────────────────
    function markField(fieldId, selector, type) {
        var el = fieldId ? document.getElementById(fieldId) : document.querySelector(selector);
        if (!el) return;
        var target = el.closest('.version-input-group') || el;
        target.classList.add('v-' + type);
    }

    function clearValidationState() {
        document.querySelectorAll('.v-error, .v-warning').forEach(function (el) {
            el.classList.remove('v-error', 'v-warning');
        });
        var summary = document.getElementById('validation-summary');
        if (summary) summary.remove();
    }

    // ── Summary popup ─────────────────────────────────────────
    function showValidationSummary(errors, warnings) {
        var html = '<div id="validation-summary" class="v-summary v-summary-error">'
            + '<div class="v-summary-header"><span>⚠️ Información faltante</span><button onclick="this.closest(\'.v-summary\').remove()" class="v-summary-close">✕</button></div>'
            + '<div class="v-summary-body">';

        if (errors.length) {
            html += '<div class="v-list-title">Campos obligatorios:</div><ul class="v-list">';
            errors.forEach(function (e) { html += '<li class="v-list-error">' + e + '</li>'; });
            html += '</ul>';
        }
        if (warnings.length) {
            html += '<div class="v-list-title" style="margin-top:8px;">Recomendaciones:</div><ul class="v-list">';
            warnings.forEach(function (w) { html += '<li class="v-list-warn">' + w + '</li>'; });
            html += '</ul>';
        }

        html += '</div></div>';

        var container = document.querySelector('.pane.active .pane-body') || document.body;
        var existing = document.getElementById('validation-summary');
        if (existing) existing.remove();
        container.insertAdjacentHTML('afterbegin', html);
        document.getElementById('validation-summary').scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function showWarningOnly(warnings) {
        if (typeof showToast === 'function') {
            showToast('Campos recomendados vacíos: ' + warnings[0], 'warning');
        }
    }

    // ── Hook into generation functions ────────────────────────
    function wrapWithValidation(fnName, formKey, allowWarnings) {
        var original = window[fnName];
        if (!original) return;

        window[fnName] = function () {
            var result = validateForm(formKey);
            if (!result.valid) return; // Block generation

            // If only warnings, ask user (optional)
            if (result.warnings.length > 0 && !allowWarnings) {
                // Let it pass — warnings are just informational
            }

            return original.apply(this, arguments);
        };
    }

    document.addEventListener('DOMContentLoaded', function () {
        // Wrap generation functions with validation
        wrapWithValidation('generateReleaseMessage', 'messages', true);
        wrapWithValidation('submitRdlForm', 'rdl', true);
        wrapWithValidation('generateRdlTelegramMessage', 'rdl-telegram', true);
        wrapWithValidation('generateUatEmail', 'uat', true);

        // For the releases form (traditional submit), hook into the existing handler
        var origSubmit = window.onReleasesFormSubmit;
        if (origSubmit) {
            window.onReleasesFormSubmit = function (e) {
                var result = validateForm('releases');
                if (!result.valid) { e.preventDefault(); return; }
                return origSubmit.call(this, e);
            };
        }

        // Clear validation on input
        document.addEventListener('input', function (e) {
            var el = e.target;
            if (el.classList.contains('v-error') || el.classList.contains('v-warning')) {
                el.classList.remove('v-error', 'v-warning');
            }
            var parent = el.closest('.v-error, .v-warning');
            if (parent) parent.classList.remove('v-error', 'v-warning');
        });
    });
})();
