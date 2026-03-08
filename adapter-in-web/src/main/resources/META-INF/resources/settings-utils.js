function postWithButton(btn, url, successMsg, errorPrefix) {
    btn.disabled = true;
    fetch(url, { method: 'POST' })
    .then(function(response) {
        return response.json().then(function(data) {
            return { ok: response.ok, data: data };
        });
    })
    .then(function(result) {
        if (result.ok) {
            showBanner(successMsg, 'success');
        } else {
            showBanner(errorPrefix + ': ' + (result.data.error || 'Unknown error'), 'danger');
        }
    })
    .catch(function(err) {
        showBanner('Request failed: ' + err.message, 'danger');
    })
    .finally(function() {
        btn.disabled = false;
    });
}

function showBanner(message, type) {
    var banner = document.getElementById('status-banner');
    banner.textContent = message;
    banner.className = 'alert alert-' + type + ' mb-3';
    banner.classList.remove('d-none');
    clearTimeout(banner._fadeTimer);
    banner._fadeTimer = setTimeout(function() {
        banner.classList.add('d-none');
    }, 5000);
}
