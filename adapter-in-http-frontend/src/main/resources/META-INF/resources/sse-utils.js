function sseFlash(el) {
    if (!el || el.nodeType !== Node.ELEMENT_NODE) return;
    el.classList.remove('sse-flash');
    void el.offsetWidth;
    el.classList.add('sse-flash');
}

function sseMorphAttributes(oldEl, newEl) {
    Array.prototype.slice.call(oldEl.attributes).forEach(function (attr) {
        if (!newEl.hasAttribute(attr.name)) oldEl.removeAttribute(attr.name);
    });
    Array.prototype.slice.call(newEl.attributes).forEach(function (attr) {
        if (oldEl.getAttribute(attr.name) !== attr.value) oldEl.setAttribute(attr.name, attr.value);
    });
}

function sseMorphNode(parent, oldNode, newNode) {
    if (!newNode) {
        if (oldNode) parent.removeChild(oldNode);
        return;
    }
    if (!oldNode) {
        var added = newNode.cloneNode(true);
        parent.appendChild(added);
        sseFlash(added);
        return;
    }
    if (oldNode.nodeType !== newNode.nodeType || oldNode.nodeName !== newNode.nodeName) {
        var replacement = newNode.cloneNode(true);
        parent.replaceChild(replacement, oldNode);
        sseFlash(replacement);
        return;
    }
    if (oldNode.nodeType === Node.TEXT_NODE) {
        if (oldNode.nodeValue !== newNode.nodeValue) {
            oldNode.nodeValue = newNode.nodeValue;
            sseFlash(parent);
        }
        return;
    }
    if (oldNode.nodeType === Node.ELEMENT_NODE) {
        sseMorphAttributes(oldNode, newNode);
        sseMorphChildren(oldNode, newNode);
    }
}

function sseMorphChildren(oldParent, newParent) {
    var oldNodes = Array.prototype.slice.call(oldParent.childNodes);
    var newNodes = Array.prototype.slice.call(newParent.childNodes);
    var max = Math.max(oldNodes.length, newNodes.length);
    for (var i = 0; i < max; i++) {
        sseMorphNode(oldParent, oldNodes[i], newNodes[i]);
    }
}

function patchUpdate(elementId, url, callback) {
    var el = document.getElementById(elementId);
    if (!el) return;
    fetch(url)
        .then(function (r) { return r.text(); })
        .then(function (html) {
            var parsed = document.createElement('div');
            parsed.innerHTML = html;
            sseMorphChildren(el, parsed);
            if (callback) callback();
        })
        .catch(function () {});
}

function connectSse(url, onMessage, onOpen) {
    var source;
    function connect() {
        source = new EventSource(url);
        source.onopen = function () {
            if (onOpen) onOpen();
        };
        source.onmessage = onMessage;
        source.onerror = function () {
            source.close();
        };
    }
    connect();
    setInterval(function () {
        if (!source || source.readyState === EventSource.CLOSED) {
            connect();
        }
    }, 60000);
}

var TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000;

function formatCountdown(ms) {
    if (ms <= 0) return 'now';
    var totalSeconds = Math.floor(ms / 1000);
    var hours = Math.floor(totalSeconds / 3600);
    var minutes = Math.floor((totalSeconds % 3600) / 60);
    var seconds = totalSeconds % 60;
    return String(hours).padStart(2, '0') + ':'
        + String(minutes).padStart(2, '0') + ':'
        + String(seconds).padStart(2, '0');
}

function formatBlockedUntil(epochMs) {
    var d = new Date(epochMs);
    var hours = String(d.getHours()).padStart(2, '0');
    var minutes = String(d.getMinutes()).padStart(2, '0');
    var remaining = epochMs - Date.now();
    if (remaining < TWENTY_FOUR_HOURS_MS) {
        return hours + ':' + minutes;
    }
    var day = String(d.getDate()).padStart(2, '0');
    var month = String(d.getMonth() + 1).padStart(2, '0');
    var year = d.getFullYear();
    return day + '.' + month + '.' + year + ' ' + hours + ':' + minutes;
}
