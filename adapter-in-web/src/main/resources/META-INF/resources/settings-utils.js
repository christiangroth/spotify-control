// htmx afterRequest handler: shows banner and handles optional page reload for
// htmx-powered buttons decorated with data-success-msg and data-error-prefix.
document.addEventListener('htmx:afterRequest', function(event) {
    var el = event.detail.elt;
    var successMsg = el.dataset.successMsg;
    var errorPrefix = el.dataset.errorPrefix;
    if (!successMsg && !errorPrefix) return;
    if (event.detail.successful) {
        if (successMsg) showBanner(successMsg, 'success');
        if (el.dataset.onSuccess === 'reload') {
            setTimeout(function() { window.location.reload(); }, 1000);
        }
    } else {
        var errMsg = 'Unknown error';
        try {
            var parsed = JSON.parse(event.detail.xhr.responseText);
            errMsg = parsed.error || errMsg;
        } catch (e) {}
        showBanner((errorPrefix || 'Request failed') + ': ' + errMsg, 'danger');
    }
});

function showBanner(message, type) {
    var banner = document.getElementById('status-banner');
    if (!banner) return;
    banner.textContent = message;
    banner.className = 'alert alert-' + type + ' mb-3';
    banner.classList.remove('d-none');
    clearTimeout(banner._fadeTimer);
    banner._fadeTimer = setTimeout(function() {
        banner.classList.add('d-none');
    }, 5000);
}
