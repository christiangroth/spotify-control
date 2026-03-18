function fadeUpdate(elementId, url, callback) {
    var el = document.getElementById(elementId);
    if (!el) return;
    el.style.transition = 'opacity 0.5s ease';
    el.style.opacity = '0';
    fetch(url)
        .then(function (r) { return r.text(); })
        .then(function (html) {
            el.innerHTML = html;
            el.style.opacity = '1';
            if (callback) callback();
        })
        .catch(function () {
            el.style.opacity = '1';
        });
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
