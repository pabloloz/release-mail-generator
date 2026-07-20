/* ── Rich Text Editor — Toolbar commands & enhancements ──────────────────── */
(function () {
    'use strict';

    // ── Basic execCommand wrapper ─────────────────────────
    window.reCmd = function (cmd, value) {
        document.execCommand(cmd, false, value || null);
        focusEditor();
    };

    // ── Font size ─────────────────────────────────────────
    window.reFontSize = function (size) {
        if (!size) return;
        document.execCommand('fontSize', false, size);
        focusEditor();
    };

    // ── Text color ────────────────────────────────────────
    window.reTextColor = function (color) {
        document.execCommand('foreColor', false, color);
        focusEditor();
    };

    // ── Highlight / background color ──────────────────────
    window.reHighlight = function (color) {
        document.execCommand('hiliteColor', false, color);
        focusEditor();
    };

    // ── Insert link ───────────────────────────────────────
    window.reInsertLink = function () {
        var sel = window.getSelection();
        var existing = '';
        if (sel.rangeCount > 0) {
            var node = sel.anchorNode;
            while (node && node.nodeName !== 'A') node = node.parentNode;
            if (node && node.nodeName === 'A') existing = node.href;
        }
        var url = prompt('URL del enlace:', existing || 'https://');
        if (!url) return;
        if (url && !url.match(/^https?:\/\//)) url = 'https://' + url;
        document.execCommand('createLink', false, url);
        focusEditor();
    };

    // ── Insert table ──────────────────────────────────────
    window.reInsertTable = function () {
        var input = prompt('Filas x Columnas (ej: 3x4):', '3x3');
        if (!input) return;
        var parts = input.split(/[x×,]/i);
        var rows = parseInt(parts[0]) || 3;
        var cols = parseInt(parts[1]) || 3;
        rows = Math.min(rows, 20);
        cols = Math.min(cols, 10);

        var html = '<table><thead><tr>';
        for (var c = 0; c < cols; c++) html += '<th>Columna ' + (c + 1) + '</th>';
        html += '</tr></thead><tbody>';
        for (var r = 0; r < rows - 1; r++) {
            html += '<tr>';
            for (var c2 = 0; c2 < cols; c2++) html += '<td>&nbsp;</td>';
            html += '</tr>';
        }
        html += '</tbody></table><p><br></p>';
        document.execCommand('insertHTML', false, html);
        focusEditor();
    };

    // ── Insert code block ─────────────────────────────────
    window.reInsertCode = function () {
        var sel = window.getSelection();
        var text = sel.toString();
        if (text && !text.includes('\n')) {
            // Inline code
            document.execCommand('insertHTML', false, '<code>' + escHtml(text) + '</code>');
        } else {
            // Code block
            var code = text || 'código aquí';
            document.execCommand('insertHTML', false, '<pre>' + escHtml(code) + '</pre><p><br></p>');
        }
        focusEditor();
    };

    // ── Keyboard shortcuts ────────────────────────────────
    document.addEventListener('keydown', function (e) {
        var editor = document.getElementById('uatRichEditor');
        if (!editor || !editor.contains(document.activeElement) && document.activeElement !== editor) return;

        if (e.ctrlKey || e.metaKey) {
            switch (e.key.toLowerCase()) {
                case 'b': e.preventDefault(); reCmd('bold'); break;
                case 'i': e.preventDefault(); reCmd('italic'); break;
                case 'u': e.preventDefault(); reCmd('underline'); break;
                case 'k': e.preventDefault(); reInsertLink(); break;
            }
        }
    });

    // ── Drag & Drop ───────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        var editor = document.getElementById('uatRichEditor');
        if (!editor) return;

        editor.addEventListener('dragover', function (e) {
            e.preventDefault();
            editor.classList.add('dragover');
        });
        editor.addEventListener('dragleave', function () {
            editor.classList.remove('dragover');
        });
        editor.addEventListener('drop', function (e) {
            e.preventDefault();
            editor.classList.remove('dragover');
            var files = e.dataTransfer.files;
            for (var i = 0; i < files.length; i++) {
                if (files[i].type.startsWith('image/')) {
                    insertImageFile(files[i]);
                }
            }
        });

        // Paste images
        editor.addEventListener('paste', function (e) {
            var items = e.clipboardData && e.clipboardData.items;
            if (!items) return;
            for (var i = 0; i < items.length; i++) {
                if (items[i].type.startsWith('image/')) {
                    e.preventDefault();
                    insertImageFile(items[i].getAsFile());
                    return;
                }
            }
        });
    });

    // ── Image insertion with compression ──────────────────
    function insertImageFile(file) {
        if (!file || !file.type.startsWith('image/')) return;
        if (file.size > 5 * 1024 * 1024) {
            if (typeof showToast === 'function') showToast('La imagen excede 5 MB.', 'error');
            return;
        }

        if (file.type === 'image/gif') {
            var reader = new FileReader();
            reader.onload = function (ev) { insertImageHtml(ev.target.result); };
            reader.readAsDataURL(file);
            return;
        }

        var img = new Image();
        var url = URL.createObjectURL(file);
        img.onload = function () {
            URL.revokeObjectURL(url);
            var w = img.naturalWidth, h = img.naturalHeight;
            var MAX = 1200;
            if (w > MAX) { h = Math.round(h * MAX / w); w = MAX; }
            var canvas = document.createElement('canvas');
            canvas.width = w; canvas.height = h;
            canvas.getContext('2d').drawImage(img, 0, 0, w, h);
            var mime = file.type === 'image/png' ? 'image/png' : 'image/jpeg';
            var dataUrl = canvas.toDataURL(mime, 0.75);
            insertImageHtml(dataUrl);
        };
        img.onerror = function () { URL.revokeObjectURL(url); };
        img.src = url;
    }

    function insertImageHtml(dataUrl) {
        var html = '<img src="' + dataUrl + '" style="max-width:100%;height:auto;"><br>';
        focusEditor();
        document.execCommand('insertHTML', false, html);
    }

    function focusEditor() {
        var editor = document.getElementById('uatRichEditor');
        if (editor) editor.focus();
    }

    function escHtml(s) {
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
})();
