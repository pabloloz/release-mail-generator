/* ── Document History — UI Logic ──────────────────────────────────────────── */
(function () {
    'use strict';

    const TYPE_LABELS = {
        RFC: '📋 RFC Técnico',
        UAT: '✅ VoBo UAT',
        RELEASE: '📧 Correo Liberación',
        RDL: '📊 Reporte RDL',
        TELEGRAM: '✈️ Telegram',
        RDL_TELEGRAM: '✈️ Telegram RDL'
    };
    const ACTION_LABELS = {
        CREATED: 'Creado',
        MODIFIED: 'Modificado',
        EXPORTED: 'Exportado',
        RESTORED: 'Restaurado'
    };

    let allVersions = [];
    let selectedForCompare = [];

    window.loadHistory = async function (typeFilter) {
        const container = document.getElementById('historyTableBody');
        const statsEl = document.getElementById('historyStats');
        if (!container) return;

        container.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:24px;color:var(--text-muted);">Cargando...</td></tr>';

        try {
            const q = document.getElementById('historySearch')?.value || '';
            let url = '/history/api';
            const params = [];
            if (typeFilter && typeFilter !== 'ALL') params.push('type=' + typeFilter);
            if (q.trim()) params.push('q=' + encodeURIComponent(q.trim()));
            if (params.length) url += '?' + params.join('&');

            const resp = await fetch(url);
            if (!resp.ok) throw new Error('Error ' + resp.status);
            allVersions = await resp.json();

            if (allVersions.length === 0) {
                container.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--text-muted);">'
                    + '<div style="font-size:2rem;opacity:.4;margin-bottom:8px;">📂</div>'
                    + 'No hay documentos en el historial.</td></tr>';
                if (statsEl) statsEl.textContent = '0 documentos';
                return;
            }

            if (statsEl) statsEl.textContent = allVersions.length + ' documento' + (allVersions.length !== 1 ? 's' : '');

            container.innerHTML = allVersions.map(v => {
                const date = new Date(v.createdAt);
                const dateStr = date.toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric' });
                const timeStr = date.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
                const isFav = typeof window.isFavorite === 'function' && window.isFavorite(v.documentType, v.id);

                return '<tr class="history-row" data-id="' + v.id + '">'
                    + '<td><input type="checkbox" class="compare-check" data-id="' + v.id + '" title="Seleccionar para comparar"></td>'
                    + '<td><span class="history-type-badge history-type-' + v.documentType + '">' + (TYPE_LABELS[v.documentType] || v.documentType) + '</span></td>'
                    + '<td><div class="history-title">' + esc(v.title || v.documentRef) + '</div>'
                    + '<div class="history-ref">' + esc(v.documentRef) + ' · v' + v.versionNumber + '</div></td>'
                    + '<td><span class="history-action history-action-' + v.action + '">' + (ACTION_LABELS[v.action] || v.action) + '</span></td>'
                    + '<td class="history-author">' + esc(v.author || '—') + '</td>'
                    + '<td><div class="history-date">' + dateStr + '</div><div class="history-time">' + timeStr + '</div></td>'
                    + '<td class="history-actions-cell">'
                    + '<button class="btn-fav' + (isFav ? ' is-fav' : '') + '" onclick="toggleFavorite(\'' + v.documentType + '\',\'' + v.id + '\',\'' + esc(v.title || v.documentRef).replace(/'/g, "\\'") + '\',\'\',\'' + (TYPE_LABELS[v.documentType] || '📄').charAt(0) + '\')" title="Favorito">' + (isFav ? '★' : '☆') + '</button>'
                    + '<button class="hbtn hbtn-view" onclick="viewVersion(\'' + v.id + '\')" title="Ver">👁</button>'
                    + '<button class="hbtn hbtn-restore" onclick="restoreVersion(\'' + v.id + '\')" title="Restaurar">↩</button>'
                    + '<button class="hbtn hbtn-delete" onclick="deleteVersion(\'' + v.id + '\')" title="Eliminar">✕</button>'
                    + '</td></tr>';
            }).join('');

        } catch (err) {
            container.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:24px;color:var(--danger);">Error: ' + esc(err.message) + '</td></tr>';
        }
    };

    window.viewVersion = async function (id) {
        try {
            const resp = await fetch('/history/api/' + id);
            if (!resp.ok) throw new Error('Error ' + resp.status);
            const v = await resp.json();

            const modal = document.getElementById('historyViewModal');
            document.getElementById('hvmTitle').textContent = v.title || v.documentRef;
            document.getElementById('hvmMeta').innerHTML =
                '<strong>Tipo:</strong> ' + (TYPE_LABELS[v.documentType] || v.documentType)
                + ' · <strong>Versión:</strong> ' + v.versionNumber
                + ' · <strong>Autor:</strong> ' + esc(v.author)
                + ' · <strong>Fecha:</strong> ' + new Date(v.createdAt).toLocaleString('es-MX')
                + ' · <strong>Acción:</strong> ' + (ACTION_LABELS[v.action] || v.action);

            const contentEl = document.getElementById('hvmContent');
            if (v.format === 'HTML') {
                contentEl.innerHTML = v.content || '';
            } else {
                contentEl.textContent = v.content || '';
                contentEl.style.whiteSpace = 'pre-wrap';
                contentEl.style.fontFamily = "'Cascadia Code', Consolas, monospace";
            }

            modal.classList.add('open');
        } catch (err) {
            showToast('Error al cargar versión: ' + err.message, 'error');
        }
    };

    window.closeHistoryModal = function () {
        document.getElementById('historyViewModal')?.classList.remove('open');
        document.getElementById('historyCompareModal')?.classList.remove('open');
    };

    window.restoreVersion = async function (id) {
        if (!confirm('¿Restaurar esta versión? Se creará una nueva entrada en el historial.')) return;
        try {
            const author = localStorage.getItem('history-author') || 'Sistema';
            const resp = await fetch('/history/api/' + id + '/restore', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ author })
            });
            if (!resp.ok) throw new Error('Error ' + resp.status);
            showToast('Versión restaurada correctamente', 'success');
            loadHistory(document.getElementById('historyTypeFilter')?.value || 'ALL');
        } catch (err) {
            showToast('Error al restaurar: ' + err.message, 'error');
        }
    };

    window.deleteVersion = async function (id) {
        if (!confirm('¿Eliminar esta versión del historial? Esta acción no se puede deshacer.')) return;
        try {
            await fetch('/history/api/' + id, { method: 'DELETE' });
            showToast('Versión eliminada', 'success');
            loadHistory(document.getElementById('historyTypeFilter')?.value || 'ALL');
        } catch (err) {
            showToast('Error al eliminar: ' + err.message, 'error');
        }
    };

    window.compareSelected = async function () {
        const checks = [...document.querySelectorAll('.compare-check:checked')];
        if (checks.length !== 2) {
            showToast('Selecciona exactamente 2 versiones para comparar.', 'error');
            return;
        }
        const [id1, id2] = checks.map(c => c.dataset.id);
        try {
            const resp = await fetch('/history/api/compare?v1=' + id1 + '&v2=' + id2);
            if (!resp.ok) throw new Error('Error ' + resp.status);
            const data = await resp.json();

            const modal = document.getElementById('historyCompareModal');
            document.getElementById('hcmTitle1').textContent = data.v1.title + ' (v' + data.v1.versionNumber + ')';
            document.getElementById('hcmTitle2').textContent = data.v2.title + ' (v' + data.v2.versionNumber + ')';
            document.getElementById('hcmDate1').textContent = new Date(data.v1.createdAt).toLocaleString('es-MX');
            document.getElementById('hcmDate2').textContent = new Date(data.v2.createdAt).toLocaleString('es-MX');

            const c1 = document.getElementById('hcmContent1');
            const c2 = document.getElementById('hcmContent2');

            if (data.identical) {
                c1.innerHTML = '<div style="padding:20px;text-align:center;color:var(--success);font-weight:600;">✓ Contenido idéntico</div>';
                c2.innerHTML = c1.innerHTML;
            } else {
                // HTML documents render as HTML, text documents as pre-wrapped text
                renderCompareContent(c1, data.content1, data.v1.format);
                renderCompareContent(c2, data.content2, data.v2.format);
            }

            modal.classList.add('open');
        } catch (err) {
            showToast('Error al comparar: ' + err.message, 'error');
        }
    };

    window.filterHistory = function () {
        const type = document.getElementById('historyTypeFilter')?.value || 'ALL';
        loadHistory(type);
    };

    window.searchHistory = function () {
        clearTimeout(window._histSearchTimer);
        window._histSearchTimer = setTimeout(() => filterHistory(), 300);
    };

    function renderCompareContent(el, content, format) {
        if (!content) { el.innerHTML = '<em style="color:var(--text-muted);">Sin contenido</em>'; return; }
        if (format === 'HTML') {
            // Render HTML content in a sandboxed styled container
            el.className = 'history-compare-content history-compare-html';
            el.innerHTML = content;
        } else {
            // Plain text (Telegram messages, etc.)
            el.className = 'history-compare-content';
            el.style.whiteSpace = 'pre-wrap';
            el.style.fontFamily = "'Cascadia Code', Consolas, monospace";
            el.textContent = content;
        }
    }

    function stripHtml(html) {
        const tmp = document.createElement('div');
        tmp.innerHTML = html;
        return tmp.textContent || tmp.innerText || '';
    }

    function esc(s) {
        if (!s) return '';
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
})();
