package co.median.android;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import co.median.median_core.AppConfig;
import co.median.median_core.GNLog;
import co.median.median_core.LeanUtils;

public class FileWriterSharer {
    private static final String TAG = FileWriterSharer.class.getSimpleName();
    private static final long MAX_SIZE = 1024 * 1024 * 1024; // 1 gigabyte
    private static final String BASE64TAG = ";base64,";
    private final FileDownloader.DownloadLocation defaultDownloadLocation;
    private String callback;

    private static class FileInfo{
        public String id;
        public String name;
        public long size;
        public String mimetype;
        public String extension;
        public File savedFile;
        public Uri savedUri;
        public OutputStream fileOutputStream;
        public long bytesWritten;
        public String callback;
        public boolean open;
    }

    private class JavascriptBridge {
        @JavascriptInterface
        public void postMessage(String jsonMessage) {
            Log.d(TAG, "got message " + jsonMessage);
            try {
                JSONObject json = new JSONObject(jsonMessage);
                String event = LeanUtils.optString(json, "event");
                if ("fileStart".equals(event)) {
                    onFileStart(json);
                } else if ("fileChunk".equals(event)) {
                    onFileChunk(json);
                } else if ("fileEnd".equals(event)) {
                    onFileEnd(json);
                } else {
                    GNLog.getInstance().logError(TAG, "Invalid event " + event);
                }
            } catch (JSONException e) {
                GNLog.getInstance().logError(TAG, "Error parsing message as json", e);
            } catch (IOException e) {
                GNLog.getInstance().logError(TAG, "IO Error", e);
            }
        }
    }

    private final JavascriptBridge javascriptBridge;
    private final MainActivity context;
    private final Map<String, FileInfo> idToFileInfo;

    public FileWriterSharer(MainActivity context) {
        this.javascriptBridge = new JavascriptBridge();
        this.context = context;
        this.idToFileInfo = new HashMap<>();

        AppConfig appConfig = AppConfig.getInstance(this.context);
        if (appConfig.permissions.isDownloadToPublicStorage()) {
            this.defaultDownloadLocation = FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS;
        } else {
            this.defaultDownloadLocation = FileDownloader.DownloadLocation.PRIVATE_INTERNAL;
        }
    }

    public JavascriptBridge getJavascriptBridge() {
        return javascriptBridge;
    }

    public void downloadBlobUrl(String url, String filename, boolean open, String callback) {
        if (url == null || !url.startsWith("blob:")) {
            return;
        }

        this.callback = callback;

        FileInfo fileInfo = new FileInfo();
        fileInfo.id = UUID.randomUUID().toString();
        fileInfo.name = filename;
        fileInfo.callback = callback;
        fileInfo.open = open;

        this.idToFileInfo.put(fileInfo.id, fileInfo);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedInputStream is = new BufferedInputStream(context.getAssets().open("BlobDownloader.js"));
            IOUtils.copy(is, baos);
            String js = baos.toString();
            context.runJavascript(js);
            js = String.format("medianDownloadBlobUrl(%s, '%s', '%s')", LeanUtils.jsWrapString(url), fileInfo.id, fileInfo.name);
            context.runJavascript(js);
        } catch (IOException e) {
            GNLog.getInstance().logError(TAG, e.getMessage(), e);
            FileDownloader.runErrorCallback(context, callback, "IO Error - " + e.getMessage());
        }
    }

    private void onFileStart(JSONObject message) throws IOException {
        String identifier = LeanUtils.optString(message, "id");
        if (TextUtils.isEmpty(identifier)) {
            GNLog.getInstance().logError(TAG, "Invalid file id");
            FileDownloader.runErrorCallback(context, callback, "Unable to retrieve download info on file start.");
            return;
        }

        FileInfo fileInfo = this.idToFileInfo.get(identifier);

        if (fileInfo == null) {
            FileDownloader.runErrorCallback(context, callback, "Unable to retrieve download info on file start.");
            return;
        }


        if (!TextUtils.isEmpty(fileInfo.name)) {
            fileInfo.extension = FileDownloader.getFilenameExtension(fileInfo.name);
            if (!TextUtils.isEmpty(fileInfo.extension)) {
                if (Objects.equals(fileInfo.extension, fileInfo.name)) {
                    fileInfo.name = "download";
                } else {
                    fileInfo.name = fileInfo.name.substring(0, fileInfo.name.length() - (fileInfo.extension.length() + 1));
                }
                fileInfo.mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileInfo.extension);
            }
        } else {
            fileInfo.name = LeanUtils.optString(message, "name");
            if (TextUtils.isEmpty(fileInfo.name)) {
                fileInfo.name = "download";
            }
        }

        long fileSize = message.optLong("size", -1);
        if (fileSize <= 0 || fileSize > MAX_SIZE) {
            GNLog.getInstance().logError(TAG, "Invalid file size");
            FileDownloader.runErrorCallback(context, fileInfo.callback, "Invalid file size.");
            return;
        }

        fileInfo.size = fileSize;

        if (TextUtils.isEmpty(fileInfo.mimetype)) {
            fileInfo.mimetype = LeanUtils.optString(message, "type");
            if (TextUtils.isEmpty(fileInfo.mimetype)) {
                GNLog.getInstance().logError(TAG, "Invalid file type");
                FileDownloader.runErrorCallback(context, fileInfo.callback, "Invalid file type.");
                return;
            }
        }

        if (TextUtils.isEmpty(fileInfo.extension)) {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            fileInfo.extension = mimeTypeMap.getExtensionFromMimeType(fileInfo.mimetype);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && defaultDownloadLocation == FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS) {
            // request permissions
            context.getPermission(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, (permissions, grantResults) -> {
                try {
                    onFileStartAfterPermission(fileInfo, grantResults[0] == PackageManager.PERMISSION_GRANTED);
                    final String js = "medianGotStoragePermissions()";
                    context.runOnUiThread(() -> context.runJavascript(js));
                } catch (IOException e) {
                    GNLog.getInstance().logError(TAG, "IO Error", e);
                    FileDownloader.runErrorCallback(context, fileInfo.callback, "IO Error - " + e.getMessage());
                }
            });
        } else {
            onFileStartAfterPermission(fileInfo, true);
            final String js = "medianGotStoragePermissions()";
            context.runOnUiThread(() -> context.runJavascript(js));
        }
    }

    private void onFileStartAfterPermission(FileInfo info, boolean granted) throws IOException {
        if (granted && defaultDownloadLocation == FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                ContentResolver contentResolver = context.getApplicationContext().getContentResolver();
                Uri uri = FileDownloader.createExternalFileUri(contentResolver, info.name, info.mimetype, Environment.DIRECTORY_DOWNLOADS);
                if (uri != null) {
                    info.fileOutputStream = contentResolver.openOutputStream(uri);
                    info.savedUri = uri;
                }
            } else {
                info.savedFile = FileDownloader.createOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), info.name, info.extension);
                info.fileOutputStream = new BufferedOutputStream(new FileOutputStream(info.savedFile));
            }
        } else {
            info.savedFile = FileDownloader.createOutputFile(context.getFilesDir(), info.name, info.extension);
            info.fileOutputStream = new BufferedOutputStream(new FileOutputStream(info.savedFile));
        }
        info.bytesWritten = 0;
        this.idToFileInfo.put(info.id, info);
    }

    private void onFileChunk(JSONObject message) throws IOException {
        String identifier = LeanUtils.optString(message, "id");
        if (TextUtils.isEmpty(identifier)) {
            return;
        }

        FileInfo fileInfo = this.idToFileInfo.get(identifier);
        if (fileInfo == null) {
            return;
        }

        String data = LeanUtils.optString(message, "data");
        if (data == null) {
            return;
        }

        int idx = data.indexOf(BASE64TAG);
        if (idx == -1) {
            return;
        }

        idx += BASE64TAG.length();
        byte[] chunk = Base64.decode(data.substring(idx), Base64.DEFAULT);

        if (fileInfo.bytesWritten + chunk.length > fileInfo.size) {
            try {
                fileInfo.fileOutputStream.close();
                fileInfo.savedFile.delete();
                this.idToFileInfo.remove(identifier);
            } catch (Exception ignored) {

            }
            GNLog.getInstance().logError(TAG, "Received too many bytes. Expected " + fileInfo.size);
            FileDownloader.runErrorCallback(context, fileInfo.callback, "Received too many bytes. Expected " + fileInfo.size);
            return;
        }

        fileInfo.fileOutputStream.write(chunk);
        fileInfo.bytesWritten += chunk.length;
    }

    private void onFileEnd(JSONObject message) throws IOException {
        String identifier = LeanUtils.optString(message, "id");
        if (TextUtils.isEmpty(identifier)) {
            GNLog.getInstance().logError(TAG, "Invalid identifier " + identifier + " for fileEnd");
            FileDownloader.runErrorCallback(context, callback, "Unable to retrieve download info on file end.");
            return;
        }

        final FileInfo fileInfo = this.idToFileInfo.get(identifier);
        if (fileInfo == null) {
            GNLog.getInstance().logError(TAG, "Invalid identifier " + identifier + " for fileEnd");
            FileDownloader.runErrorCallback(context, callback, "Unable to retrieve download info on file end.");
            return;
        }

        if (fileInfo.fileOutputStream != null) {
            fileInfo.fileOutputStream.close();
        }

        String error = message.optString("error");
        if (!TextUtils.isEmpty(error)) {
            FileDownloader.runErrorCallback(context, fileInfo.callback, error);
            return;
        }

        if (fileInfo.open) {
            context.runOnUiThread(() -> {
                if (fileInfo.savedUri == null && fileInfo.savedFile != null) {
                    fileInfo.savedUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".fileprovider", fileInfo.savedFile);
                }
                if (fileInfo.savedUri == null) return;

                FileDownloader.viewFile(context, fileInfo.savedUri, fileInfo.mimetype, (defaultDownloadLocation == FileDownloader.DownloadLocation.PRIVATE_INTERNAL));
            });
        } else {
            String downloadCompleteMessage = fileInfo.name != null && !fileInfo.name.isEmpty()
                    ? String.format(context.getString(R.string.file_download_finished_with_name), fileInfo.name + '.' + fileInfo.extension)
                    : context.getString(R.string.file_download_finished);
            Toast.makeText(context, downloadCompleteMessage, Toast.LENGTH_SHORT).show();
        }

        FileDownloader.runSuccessCallback(context, fileInfo.callback);
    }
}
