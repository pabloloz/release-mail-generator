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

    let _currentPage = 1;
    let _totalPages = 1;
    let _total = 0;

    window.loadHistory = async function (typeFilter, page) {
        const container = document.getElementById('historyTableBody');
        const wrapper = document.querySelector('.history-table-wrapper');
        const statsEl = document.getElementById('historyStats');
        if (!container) return;

        if (!page) page = _currentPage || 1;
        const size = parseInt((document.getElementById('historyPageSize') || {}).value) || 10;
        const sort = (document.getElementById('historySortBy') || {}).value || 'newest';
        const q = (document.getElementById('historySearch') || {}).value || '';

        // Fade out current content
        if (wrapper) wrapper.classList.add('fading');
        await new Promise(function (r) { setTimeout(r, 150); });

        try {
            const params = ['page=' + page, 'size=' + size, 'sort=' + sort];
            if (typeFilter && typeFilter !== 'ALL') params.push('type=' + typeFilter);
            if (q.trim()) params.push('q=' + encodeURIComponent(q.trim()));
            const url = '/history/api?' + params.join('&');

            const resp = await fetch(url);
            if (!resp.ok) throw new Error('Error ' + resp.status);
            const data = await resp.json();

            const items = data.items || [];
            _currentPage = data.page || 1;
            _totalPages = data.totalPages || 1;
            _total = data.total || 0;

            if (statsEl) statsEl.textContent = _total + ' documento' + (_total !== 1 ? 's' : '');

            if (items.length === 0) {
                container.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:48px;color:var(--text-muted);">'
                    + '<div style="font-size:2.5rem;opacity:.3;margin-bottom:12px;">📄</div>'
                    + '<div style="font-size:1rem;font-weight:600;margin-bottom:6px;">Aún no has generado ningún documento</div>'
                    + '<div style="font-size:.84rem;">Los documentos que generes aparecerán aquí automáticamente.</div>'
                    + '</td></tr>';
                renderPagination(0, 0, 0, size);
                if (wrapper) wrapper.classList.remove('fading');
                return;
            }

            container.innerHTML = items.map(function (v, idx) {
                var date = new Date(v.createdAt);
                var dateStr = date.toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric' });
                var timeStr = date.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
                var isFav = typeof window.isFavorite === 'function' && window.isFavorite(v.documentType, v.id);
                var delay = Math.min(idx * 25, 200); // stagger animation, cap at 200ms

                return '<tr class="history-row anim-in" data-id="' + v.id + '" onclick="onHistoryRowClick(event,\'' + v.id + '\')" style="cursor:pointer;animation-delay:' + delay + 'ms;">'
                    + '<td><input type="checkbox" class="compare-check" data-id="' + v.id + '" title="Seleccionar para comparar"></td>'
                    + '<td><span class="history-type-badge history-type-' + v.documentType + '">' + (TYPE_LABELS[v.documentType] || v.documentType) + '</span></td>'
                    + '<td><div class="history-title">' + esc(v.title || v.documentRef) + '</div>'
                    + '<div class="history-ref">' + esc(v.documentRef) + ' · v' + v.versionNumber + '</div></td>'
                    + '<td><span class="history-action history-action-' + v.action + '">' + (ACTION_LABELS[v.action] || v.action) + '</span></td>'
                    + '<td class="history-author">' + esc(v.author || '—') + '</td>'
                    + '<td><div class="history-date">' + dateStr + '</div><div class="history-time">' + timeStr + '</div></td>'
                    + '<td class="history-actions-cell">'
                    + '<button class="btn-fav' + (isFav ? ' is-fav' : '') + '" onclick="toggleHistoryFav(this,\'' + v.documentType + '\',\'' + v.id + '\',\'' + esc(v.title || v.documentRef).replace(/'/g, "\\'") + '\',\'' + (TYPE_LABELS[v.documentType] || '📄').charAt(0) + '\')" title="Favorito">' + (isFav ? '★' : '☆') + '</button>'
                    + '<button class="hbtn hbtn-restore" onclick="restoreVersion(\'' + v.id + '\')" title="Restaurar">↩</button>'
                    + '<button class="hbtn hbtn-delete" onclick="deleteVersion(\'' + v.id + '\')" title="Eliminar">✕</button>'
                    + '</td></tr>';
            }).join('');

            renderPagination(_currentPage, _totalPages, _total, size);

        } catch (err) {
            container.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:24px;color:var(--danger);">Error: ' + esc(err.message) + '</td></tr>';
        }

        // Fade in new content
        if (wrapper) wrapper.classList.remove('fading');
    };

    function renderPagination(page, totalPages, total, size) {
        var pag = document.getElementById('historyPagination');
        if (!pag) return;
        if (total <= size && page <= 1) { pag.style.display = 'none'; return; }
        pag.style.display = '';

        var info = document.getElementById('historyPaginationInfo');
        var from = (page - 1) * size + 1;
        var to = Math.min(page * size, total);
        if (info) info.textContent = 'Mostrando ' + from + '–' + to + ' de ' + total;

        var btns = document.getElementById('historyPaginationBtns');
        if (!btns) return;
        var html = '';

        // First + Prev
        html += '<button class="hist-pg-btn" ' + (page <= 1 ? 'disabled' : 'onclick="goHistoryPage(1)"') + ' title="Primera">⏮</button>';
        html += '<button class="hist-pg-btn" ' + (page <= 1 ? 'disabled' : 'onclick="goHistoryPage(' + (page - 1) + ')"') + '>◀ Anterior</button>';

        // Page numbers
        var pages = buildPageNumbers(page, totalPages);
        for (var i = 0; i < pages.length; i++) {
            var p = pages[i];
            if (p === '...') {
                html += '<span class="hist-pg-ellipsis">…</span>';
            } else {
                html += '<button class="hist-pg-btn' + (p === page ? ' active' : '') + '" onclick="goHistoryPage(' + p + ')">' + p + '</button>';
            }
        }

        // Next + Last
        html += '<button class="hist-pg-btn" ' + (page >= totalPages ? 'disabled' : 'onclick="goHistoryPage(' + (page + 1) + ')"') + '>Siguiente ▶</button>';
        html += '<button class="hist-pg-btn" ' + (page >= totalPages ? 'disabled' : 'onclick="goHistoryPage(' + totalPages + ')"') + ' title="Última">⏭</button>';

        btns.innerHTML = html;
    }

    function buildPageNumbers(current, total) {
        if (total <= 7) {
            var arr = [];
            for (var i = 1; i <= total; i++) arr.push(i);
            return arr;
        }
        var pages = [1];
        if (current > 3) pages.push('...');
        for (var j = Math.max(2, current - 1); j <= Math.min(total - 1, current + 1); j++) pages.push(j);
        if (current < total - 2) pages.push('...');
        pages.push(total);
        return pages;
    }

    window.goHistoryPage = function (page) {
        _currentPage = page;
        var type = (document.getElementById('historyTypeFilter') || {}).value || 'ALL';
        loadHistory(type, page);
        // Scroll table to top
        var wrapper = document.querySelector('.history-table-wrapper');
        if (wrapper) wrapper.scrollTo({ top: 0, behavior: 'smooth' });
    };

    window.changePageSize = function () {
        _currentPage = 1;
        filterHistory();
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

    window.onHistoryRowClick = function (e, id) {
        // Don't open preview if clicking on buttons, checkboxes, or action cells
        var target = e.target;
        if (target.closest('.history-actions-cell') || target.closest('input[type="checkbox"]') || target.tagName === 'BUTTON') return;
        viewVersion(id);
    };

    window.toggleHistoryFav = function (btn, type, id, title, icon) {
        toggleFavorite(type, id, title, '', icon);
        var fav = isFavorite(type, id);
        btn.textContent = fav ? '★' : '☆';
        btn.classList.toggle('is-fav', fav);
    };

    window.closeHistoryModal = function () {
        var modals = [document.getElementById('historyViewModal'), document.getElementById('historyCompareModal')];
        modals.forEach(function(m) {
            if (m && m.classList.contains('open')) {
                m.style.opacity = '0';
                setTimeout(function() { m.classList.remove('open'); m.style.opacity = ''; }, 200);
            }
        });
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
            filterHistory();
        } catch (err) {
            showToast('Error al restaurar: ' + err.message, 'error');
        }
    };

    window.deleteVersion = async function (id) {
        if (!confirm('¿Eliminar esta versión del historial? Esta acción no se puede deshacer.')) return;
        try {
            await fetch('/history/api/' + id, { method: 'DELETE' });
            showToast('Versión eliminada', 'success');
            filterHistory();
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
        _currentPage = 1;
        const type = document.getElementById('historyTypeFilter')?.value || 'ALL';
        loadHistory(type, 1);
    };

    window.searchHistory = function () {
        clearTimeout(window._histSearchTimer);
        window._histSearchTimer = setTimeout(() => { _currentPage = 1; filterHistory(); }, 300);
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

    function esc(s) {
        if (!s) return '';
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
})();
