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

        // Paste handler: images first, then clean HTML from Office/browsers
        editor.addEventListener('paste', function (e) {
            var items = e.clipboardData && e.clipboardData.items;
            // Check for image files first
            if (items) {
                for (var i = 0; i < items.length; i++) {
                    if (items[i].type.startsWith('image/')) {
                        e.preventDefault();
                        e.stopPropagation();
                        insertImageFile(items[i].getAsFile());
                        return;
                    }
                }
            }
            // Clean HTML paste from Word/Outlook/Teams/browsers
            var html = e.clipboardData ? e.clipboardData.getData('text/html') : '';
            if (html && html.length > 20) {
                e.preventDefault();
                var clean = cleanPastedHtml(html);
                document.execCommand('insertHTML', false, clean);
            }
            // If no HTML, let plain text paste through naturally
        });

        // Enter creates paragraphs
        editor.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                document.execCommand('insertParagraph', false);
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
            var MAX = 800;
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
        var editor = document.getElementById('uatRichEditor');
        if (!editor) return;
        editor.focus();
        var img = document.createElement('img');
        img.src = dataUrl;
        img.style.cssText = 'max-width:100%;height:auto;display:block;margin:10px 0;border-radius:6px;border:1px solid var(--border);';
        img.setAttribute('data-uat-image', 'true');
        var sel = window.getSelection();
        if (sel.rangeCount) {
            var range = sel.getRangeAt(0);
            range.deleteContents();
            range.insertNode(img);
            range.setStartAfter(img);
            range.collapse(true);
            sel.removeAllRanges();
            sel.addRange(range);
        } else {
            editor.appendChild(img);
        }
        var p = document.createElement('p');
        p.innerHTML = '<br>';
        img.after(p);
    }

    /**
     * Cleans HTML pasted from Word, Outlook, Teams, Excel, browsers.
     * Preserves: bold, italic, underline, lists, links, headings, tables, line breaks.
     * Removes: styles, classes, Office markup, fonts, colors, backgrounds.
     */
    function cleanPastedHtml(html) {
        // Remove everything before <body> if present
        var bodyMatch = html.match(/<body[^>]*>([\s\S]*)<\/body>/i);
        if (bodyMatch) html = bodyMatch[1];
        // Strip Office-specific elements
        html = html.replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '');
        html = html.replace(/<meta[^>]*>/gi, '');
        html = html.replace(/<link[^>]*>/gi, '');
        html = html.replace(/<title[^>]*>[\s\S]*?<\/title>/gi, '');
        html = html.replace(/<o:p>[\s\S]*?<\/o:p>/gi, '');
        html = html.replace(/<xml>[\s\S]*?<\/xml>/gi, '');
        html = html.replace(/<!--[\s\S]*?-->/g, '');
        // Strip class and style attributes (but keep href, src)
        html = html.replace(/\s+class="[^"]*"/gi, '');
        html = html.replace(/\s+class='[^']*'/gi, '');
        html = html.replace(/\s+style="[^"]*"/gi, '');
        html = html.replace(/\s+style='[^']*'/gi, '');
        html = html.replace(/\s+lang="[^"]*"/gi, '');
        html = html.replace(/\s+data-[a-z-]+="[^"]*"/gi, '');
        // Strip font/span wrappers but keep content
        html = html.replace(/<\/?font[^>]*>/gi, '');
        html = html.replace(/<\/?span[^>]*>/gi, '');
        // Keep structural tags: b, strong, i, em, u, ul, ol, li, a, br, p, div, h1-h6, table, tr, td, th, thead, tbody
        // Remove everything else (like <v:shape>, <w:WordDocument> etc.)
        html = html.replace(/<(?!\/?(b|strong|i|em|u|ul|ol|li|a|br|p|div|h[1-6]|table|tr|td|th|thead|tbody|blockquote|hr)[ >\/])[^>]+>/gi, '');
        // Clean up empty paragraphs
        html = html.replace(/<p>\s*<\/p>/gi, '');
        html = html.replace(/<p>\s*&nbsp;\s*<\/p>/gi, '');
        // Normalize whitespace
        html = html.trim();
        return html;
    }

    function focusEditor() {
        // Focus the last-active contenteditable editor
        var active = document.activeElement;
        if (active && active.getAttribute('contenteditable') === 'true') return;
        var editor = document.getElementById('uatRichEditor');
        if (editor) editor.focus();
    }

    function escHtml(s) {
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
})();
