// This is used because download from native side won't have session changes.

function medianDownloadBlobUrl(url, id, filename) {

    // convert "null" string to actual null
    if (filename === "null") {
        filename = null;
    }

	var req = new XMLHttpRequest();
	req.open('GET', url, true);
	req.responseType = 'blob';

	req.onload = function(event) {
		var blob = req.response;
		saveBlob(blob, id, filename);
	};
    req.onerror = function(event) {
        sendError(event);
    };
	req.send();

	function sendMessage(message) {
	    if (window.webkit && window.webkit.messageHandlers &&
	        window.webkit.messageHandlers.fileWriterSharer) {
	        window.webkit.messageHandlers.fileWriterSharer.postMessage(message);
	    }
	    if (window.gonative_file_writer_sharer && window.gonative_file_writer_sharer.postMessage) {
			window.gonative_file_writer_sharer.postMessage(JSON.stringify(message));
	    }
	}

	function saveBlob(blob, id, filename) {
	    var chunkSize = 1024 * 1024; // 1mb
	    var index = 0;

	    function sendHeader() {
	        sendMessage({
	            event: 'fileStart',
	            id: id,
	            size: blob.size,
	            type: blob.type,
	            name: filename
	        });
	    }

	    function sendChunk() {
	        if (index >= blob.size) {
                sendMessage({
                    event: 'fileEnd',
                    id: id
                });
	            return;
	        }

	        var chunkToSend = blob.slice(index, index + chunkSize);
	        var reader = new FileReader();
	        reader.onloadend = function() {
	            sendMessage({
	                event: 'fileChunk',
	                id: id,
	                data: reader.result
	            });
	            index += chunkSize;
	            setTimeout(sendChunk);
	        };
            reader.onerror = function(event) {
                sendError(event);
            };
            reader.readAsDataURL(chunkToSend);
	    }
	    
	    sendHeader();
	    median_run_after_storage_permissions.push(sendChunk);
	}

	function sendError(event) {
        sendMessage({
            event: 'fileEnd',
            id: id,
            error: event?.target?.error?.message || 'Unknown error occurred'
        });
    }
}

median_run_after_storage_permissions = [];
function medianGotStoragePermissions() {
    while (median_run_after_storage_permissions.length > 0) {
        var run = median_run_after_storage_permissions.shift();
        run();
    }
}
