/* ── Markdown Viewer — Preview MD before downloading ──────────────────────── */
(function() {
    'use strict';

    var _mdRawContent = '';
    var _mdFilename = '';
    var _mdCurrentTab = 'rendered';

    // Auto-intercept clicks on .md-preview-link elements
    document.addEventListener('click', function(e) {
        var link = e.target.closest('.md-preview-link');
        if (!link) return;
        e.preventDefault();
        var url = link.getAttribute('href');
        var title = link.dataset.title || 'Markdown';
        var filename = link.dataset.filename || 'documento.md';
        openMdViewer(url, title, filename);
    });

    function openMdViewer(url, title, filename) {
        _mdFilename = filename;
        _mdCurrentTab = 'rendered';
        var viewer = document.getElementById('mdViewer');
        var titleEl = document.getElementById('mdViewerTitle');
        var body = document.getElementById('mdViewerBody');
        if (!viewer || !body) { window.location.href = url; return; } // fallback: download
        if (titleEl) titleEl.textContent = title;
        body.innerHTML = '<div style="text-align:center;padding:40px;color:#94a3b8;"><span class="spinner"></span> Generando Markdown...</div>';
        viewer.classList.add('open');
        updateTabButtons('rendered');

        fetch(url)
            .then(function(r) { if (!r.ok) throw new Error('Error ' + r.status); return r.text(); })
            .then(function(text) { _mdRawContent = text; renderMdTab(); })
            .catch(function(err) { body.innerHTML = '<div style="text-align:center;padding:40px;color:#ef4444;">Error: ' + err.message + '</div>'; });
    }

    window.switchMdTab = function(tab) {
        _mdCurrentTab = tab;
        updateTabButtons(tab);
        if (_mdRawContent) renderMdTab();
    };

    window.closeMdViewer = function() {
        var viewer = document.getElementById('mdViewer');
        if (viewer) viewer.classList.remove('open');
        _mdRawContent = '';
    };

    window.downloadCurrentMd = function() {
        if (!_mdRawContent) return;
        var blob = new Blob([_mdRawContent], {type: 'text/markdown;charset=utf-8'});
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a'); a.href = url; a.download = _mdFilename;
        document.body.appendChild(a); a.click(); document.body.removeChild(a);
        URL.revokeObjectURL(url);
        if (typeof showToast === 'function') showToast('Markdown descargado', 'success');
    };

    window.copyMdContent = function() {
        if (!_mdRawContent) return;
        navigator.clipboard.writeText(_mdRawContent)
            .then(function() { if (typeof showToast === 'function') showToast('Markdown copiado', 'success'); })
            .catch(function() { if (typeof showToast === 'function') showToast('Error al copiar', 'error'); });
    };

    function updateTabButtons(active) {
        var r = document.getElementById('mdTabRendered');
        var s = document.getElementById('mdTabSource');
        if (r) r.style.opacity = active === 'rendered' ? '1' : '.5';
        if (s) s.style.opacity = active === 'source' ? '1' : '.5';
    }

    function renderMdTab() {
        var body = document.getElementById('mdViewerBody');
        if (!body) return;
        if (_mdCurrentTab === 'source') {
            var lines = _mdRawContent.split('\n').map(function(l) {
                return '<span class="md-line">' + escHtml(l) + '</span>';
            }).join('');
            body.innerHTML = '<div class="md-source">' + lines + '</div>';
        } else {
            body.innerHTML = '<div class="md-rendered">' + markdownToHtml(_mdRawContent) + '</div>';
        }
    }

    function markdownToHtml(md) {
        var h = md;
        // Headers
        h = h.replace(/^### (.+)$/gm, '<h3>$1</h3>');
        h = h.replace(/^## (.+)$/gm, '<h2>$1</h2>');
        h = h.replace(/^# (.+)$/gm, '<h1>$1</h1>');
        // HR
        h = h.replace(/^---+$/gm, '<hr>');
        // Bold + Italic
        h = h.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>');
        h = h.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        h = h.replace(/\*(.+?)\*/g, '<em>$1</em>');
        // Inline code
        h = h.replace(/`([^`]+)`/g, '<code>$1</code>');
        // Links
        h = h.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');
        // Blockquotes
        h = h.replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>');
        // Tables
        h = h.replace(/^(\|.+\|)\n\|[-| :]+\|\n((?:\|.+\|\n?)*)/gm, function(m, header, rows) {
            var ths = header.split('|').filter(function(c){return c.trim();}).map(function(c){return '<th>'+c.trim()+'</th>';}).join('');
            var trs = rows.trim().split('\n').map(function(row) {
                var tds = row.split('|').filter(function(c){return c.trim();}).map(function(c){return '<td>'+c.trim()+'</td>';}).join('');
                return '<tr>' + tds + '</tr>';
            }).join('');
            return '<table><thead><tr>' + ths + '</tr></thead><tbody>' + trs + '</tbody></table>';
        });
        // Unordered lists
        h = h.replace(/^(- .+(?:\n- .+)*)/gm, function(block) {
            var items = block.split('\n').map(function(l){return '<li>'+l.replace(/^- /,'')+'</li>';}).join('');
            return '<ul>' + items + '</ul>';
        });
        // Ordered lists
        h = h.replace(/^(\d+\. .+(?:\n\d+\. .+)*)/gm, function(block) {
            var items = block.split('\n').map(function(l){return '<li>'+l.replace(/^\d+\. /,'')+'</li>';}).join('');
            return '<ol>' + items + '</ol>';
        });
        // Paragraphs
        h = h.replace(/\n\n+/g, '</p><p>');
        h = '<p>' + h + '</p>';
        h = h.replace(/([^>])\n([^<])/g, '$1<br>$2');
        h = h.replace(/<p>\s*<\/p>/g, '');
        return h;
    }

    function escHtml(s) {
        return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    }
})();
