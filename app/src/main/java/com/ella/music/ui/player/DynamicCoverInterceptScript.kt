package com.ella.music.ui.player

/** JavaScript injected into the page to detect video elements and intercept clicks. */
internal const val VIDEO_INTERCEPT_JS = """
(function() {
    // Watch for click events on or near video elements
    document.addEventListener('click', function(e) {
        var target = e.target;
        // Walk up to find video or its container
        for (var i = 0; i < 5; i++) {
            if (!target) break;
            if (target.tagName === 'VIDEO') {
                var src = target.src || target.currentSrc;
                if (src && src.length > 0) {
                    e.preventDefault();
                    e.stopPropagation();
                    AndroidBridge.postMessage(src);
                    return;
                }
                // Check child source elements
                var sources = target.querySelectorAll('source');
                for (var j = 0; j < sources.length; j++) {
                    var s = sources[j].src;
                    if (s && s.length > 0) {
                        e.preventDefault();
                        e.stopPropagation();
                        AndroidBridge.postMessage(s);
                        return;
                    }
                }
            }
            // Check for download links with video URLs
            if (target.tagName === 'A') {
                var href = target.href || '';
                if (href.match(/\.mp4/i)) {
                    e.preventDefault();
                    e.stopPropagation();
                    AndroidBridge.postMessage(href);
                    return;
                }
            }
            target = target.parentElement;
        }
    }, true);

    // Also intercept XMLHttpRequests and fetch for blob URLs
    var origCreateObjectURL = URL.createObjectURL;
    URL.createObjectURL = function(blob) {
        var url = origCreateObjectURL.call(URL, blob);
        if (blob && blob.type && blob.type.indexOf('video') >= 0) {
            var reader = new FileReader();
            reader.onloadend = function() {
                AndroidBridge.postMessage(reader.result);
            };
            reader.readAsDataURL(blob);
        }
        return url;
    };
})();
"""
