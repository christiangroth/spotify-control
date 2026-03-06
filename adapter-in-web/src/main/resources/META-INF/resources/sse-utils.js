function fadeUpdate(elementId, url, callback) {
    var el = document.getElementById(elementId);
    if (!el) return;
    el.style.transition = 'opacity 3.0s ease';
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
