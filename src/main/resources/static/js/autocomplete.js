/**
 * Поле з підказками під ним (docs/Контрагенти.md): «Банк» шукає у списку на сторінці, «Населений
 * пункт» — запитом до /api/address/settlements. Стрілки ↑/↓ — вибір, Enter — підтвердити, Esc — закрити.
 *
 *   ProtaxoAutocomplete.attach(input, {
 *       source: function (query, done) { done([...items]); },   // items: { label, hint?, ... }
 *       onSelect: function (item) { ... },
 *       minLength: 1, delay: 0
 *   });
 */
(function () {
    var style = document.createElement('style');
    style.textContent =
        '.ac-wrap{position:relative}' +
        '.ac-list{position:absolute;left:0;right:0;top:100%;z-index:50;margin:2px 0 0;padding:4px 0;list-style:none;' +
        'background:var(--surface,#fff);border:1px solid var(--border,#e2e4e9);border-radius:8px;' +
        'box-shadow:0 8px 20px rgba(28,43,49,.12);max-height:280px;overflow-y:auto}' +
        '.ac-item{padding:6px 10px;cursor:pointer;line-height:1.3}' +
        '.ac-item .ac-hint{display:block;font-size:.8rem;color:var(--muted,#6b7280)}' +
        '.ac-item.active,.ac-item:hover{background:var(--bg,#f4f5f7)}' +
        '.ac-empty{padding:6px 10px;color:var(--muted,#6b7280);font-size:.9rem}';
    document.head.appendChild(style);

    function attach(input, options) {
        var minLength = options.minLength == null ? 1 : options.minLength;
        var delay = options.delay || 0;
        var wrap = document.createElement('div');
        wrap.className = 'ac-wrap';
        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);
        input.setAttribute('autocomplete', 'off');

        var list = document.createElement('ul');
        list.className = 'ac-list';
        list.hidden = true;
        wrap.appendChild(list);

        var items = [];
        var active = -1;
        var timer = null;
        var requestId = 0;

        function close() {
            list.hidden = true;
            active = -1;
        }

        function render() {
            list.innerHTML = '';
            if (!items.length) {
                if (options.emptyText) {
                    var empty = document.createElement('li');
                    empty.className = 'ac-empty';
                    empty.textContent = options.emptyText;
                    list.appendChild(empty);
                    list.hidden = false;
                } else {
                    close();
                }
                return;
            }
            items.forEach(function (item, i) {
                var li = document.createElement('li');
                li.className = 'ac-item' + (i === active ? ' active' : '');
                li.textContent = item.label;
                if (item.hint) {
                    var hint = document.createElement('span');
                    hint.className = 'ac-hint';
                    hint.textContent = item.hint;
                    li.appendChild(hint);
                }
                // mousedown, а не click: інакше blur поля закриє список раніше за вибір
                li.addEventListener('mousedown', function (e) {
                    e.preventDefault();
                    choose(i);
                });
                list.appendChild(li);
            });
            list.hidden = false;
        }

        function choose(i) {
            var item = items[i];
            if (!item) return;
            close();
            options.onSelect(item);
        }

        function query() {
            var q = input.value.trim();
            if (q.length < minLength) {
                items = [];
                close();
                return;
            }
            var id = ++requestId;
            options.source(q, function (result) {
                if (id !== requestId) return; // застаріла відповідь
                items = result || [];
                active = items.length ? 0 : -1;
                render();
            });
        }

        input.addEventListener('input', function () {
            clearTimeout(timer);
            timer = setTimeout(query, delay);
        });
        input.addEventListener('focus', function () {
            if (input.value.trim().length >= minLength && options.openOnFocus) query();
        });
        input.addEventListener('keydown', function (e) {
            if (list.hidden) return;
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                e.preventDefault();
                if (!items.length) return;
                active = (active + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
                render();
                var el = list.children[active];
                if (el && el.scrollIntoView) el.scrollIntoView({ block: 'nearest' });
            } else if (e.key === 'Enter') {
                if (active >= 0) {
                    e.preventDefault(); // не відправляти форму
                    choose(active);
                }
            } else if (e.key === 'Escape') {
                close();
            }
        });
        input.addEventListener('blur', function () { setTimeout(close, 150); });

        return { close: close, refresh: query };
    }

    window.ProtaxoAutocomplete = { attach: attach };
})();
