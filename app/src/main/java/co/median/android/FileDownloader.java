package co.median.android;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Objects;

import co.median.median_core.AppConfig;
import co.median.median_core.GNLog;
import co.median.median_core.GoNativeActivity;
import co.median.median_core.LeanUtils;
import co.median.median_core.dto.PermissionsConfig;

/**
 * Created by weiyin on 6/24/14.
 */
public class FileDownloader implements DownloadListener {
    public enum DownloadLocation {
        PUBLIC_DOWNLOADS, PRIVATE_INTERNAL
    }

    private static final String TAG = FileDownloader.class.getName();
    private final MainActivity context;
    private final DownloadLocation defaultDownloadLocation;
    private final ActivityResultLauncher<String[]> requestPermissionLauncher;
    private UrlNavigation urlNavigation;
    private String lastDownloadedUrl;
    private static Uri lastViewedUriToDelete = null;
    private DownloadService downloadService;
    private boolean isBound = false;
    private PreDownloadInfo preDownloadInfo;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) iBinder;
            downloadService = binder.getService();
            downloadService.setFileDownloader(FileDownloader.this);
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            downloadService = null;
            isBound = false;
        }
    };

    FileDownloader(MainActivity context) {
        this.context = context;

        PermissionsConfig permissions = AppConfig.getInstance(this.context).permissions;

        if (permissions.isDownloadToPublicStorage()) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                this.defaultDownloadLocation = DownloadLocation.PUBLIC_DOWNLOADS;
            } else {
                // For Android 9 and below
                if (permissions.isExtDirLegacyEnabled()) {
                    this.defaultDownloadLocation = DownloadLocation.PUBLIC_DOWNLOADS;
                } else {
                    this.defaultDownloadLocation = DownloadLocation.PRIVATE_INTERNAL;
                }
            }
        } else {
            this.defaultDownloadLocation = DownloadLocation.PRIVATE_INTERNAL;
        }

        Intent intent = new Intent(context, DownloadService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // initialize request permission launcher
        requestPermissionLauncher = context.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {

            if (isGranted.containsKey(Manifest.permission.WRITE_EXTERNAL_STORAGE) && Boolean.FALSE.equals(isGranted.get(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                Toast.makeText(context, "Unable to save download, storage permission denied", Toast.LENGTH_SHORT).show();
                if (preDownloadInfo != null) {
                    runErrorCallback(context, preDownloadInfo.callback, "Unable to save download, storage permission denied.");
                    preDownloadInfo = null;
                }
                return;
            }

            if (preDownloadInfo != null && isBound) {
                if (preDownloadInfo.isBlob) {
                    context.getFileWriterSharer().downloadBlobUrl(
                            preDownloadInfo.url,
                            preDownloadInfo.filename,
                            preDownloadInfo.open,
                            preDownloadInfo.callback
                    );
                } else {
                    startDownload(preDownloadInfo, preDownloadInfo.callback);
                }
                preDownloadInfo = null;
            }
        });
    }

    @Override
    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        if (urlNavigation != null) {
            urlNavigation.onDownloadStart();
        }

        if (context != null) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    context.showWebview();
                }
            });
        }

        // get filename from content disposition
        String guessFilename = null;
        if (!TextUtils.isEmpty(contentDisposition)) {
             guessFilename = LeanUtils.guessFileName(url, contentDisposition, mimetype);
        }

        if (url.startsWith("blob:") && context != null) {

            boolean openAfterDownload = defaultDownloadLocation == DownloadLocation.PRIVATE_INTERNAL;

            if (shouldRequestWritePermission(new PreDownloadInfo(url, guessFilename, true, openAfterDownload, ""))) {
                return;
            }

            context.getFileWriterSharer().downloadBlobUrl(url, guessFilename, openAfterDownload, "");
            return;
        }

        lastDownloadedUrl = url;

        // try to guess mimetype
        if (mimetype == null || mimetype.equalsIgnoreCase("application/force-download") ||
                mimetype.equalsIgnoreCase("application/octet-stream")) {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null && !extension.isEmpty()) {
                String guessedMimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
                if (guessedMimeType != null) {
                    mimetype = guessedMimeType;
                }
            }
        }

        verifyAndStartDownload(url, guessFilename,  mimetype, false, false, "");
    }

    public void downloadFile(String url, String filename, boolean shouldSaveToGallery, boolean open, String callback) {
        if (TextUtils.isEmpty(url)) {
            Log.d(TAG, "downloadFile: Url empty!");

            runErrorCallback(context, callback, "URL cannot be empty.");
            return;
        }

        if (url.startsWith("blob:") && context != null) {

            if (defaultDownloadLocation == DownloadLocation.PRIVATE_INTERNAL) {
                open = true;
            }

            if (shouldRequestWritePermission(new PreDownloadInfo(url, filename, true, open, callback))) {
                return;
            }
            context.getFileWriterSharer().downloadBlobUrl(url, filename, open, callback);
            return;
        }

        String mimetype = "*/*";
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null && !extension.isEmpty()) {
            String guessedMimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
            if (guessedMimeType != null) {
                mimetype = guessedMimeType;
            }
        }

        verifyAndStartDownload(url, filename, mimetype, shouldSaveToGallery, open, callback);
    }

    private void verifyAndStartDownload(String downloadUrl, String filename, String mimetype, boolean shouldSaveToGallery, boolean open, String callback) {
        if (!isBound) {
            GNLog.getInstance().logError(TAG, "verifyAndStartDownload: Unable to start download.", new Exception("DownloadService not bound."));
            return;
        }

        PreDownloadInfo preDownload = new PreDownloadInfo(downloadUrl, filename, mimetype, shouldSaveToGallery, open, false, callback);
        if (shouldRequestWritePermission(preDownload)) return;

        // no permission required, proceed to download
        startDownload(preDownload, callback);
    }

    private void startDownload(PreDownloadInfo preDownload, String callback) {
        downloadService.startDownload(preDownload, defaultDownloadLocation, new DownloadService.DownloadCallback() {
            @Override
            public void onSuccess() {
                runSuccessCallback(context, callback);
            }

            @Override
            public void onFailed(String error) {
                runErrorCallback(context, callback, error);
            }
        });
    }

    // Requests required permission depending on device's SDK version
    private boolean shouldRequestWritePermission(PreDownloadInfo preDownloadInfo) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return false;

        if (defaultDownloadLocation == DownloadLocation.PUBLIC_DOWNLOADS
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            this.preDownloadInfo = preDownloadInfo;
            requestPermissionLauncher.launch(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
            return true;
        }

        return false;
    }

    public String getLastDownloadedUrl() {
        return lastDownloadedUrl;
    }

    public void setUrlNavigation(UrlNavigation urlNavigation) {
        this.urlNavigation = urlNavigation;
    }

    public void unbindDownloadService() {
        if (isBound) {
            context.unbindService(serviceConnection);
            isBound = false;
        }
    }

    public void handleDownloadUri(Uri uri, String mimeType, boolean shouldSaveToGallery, boolean open, String filename) {
        if (uri == null) return;
        if (defaultDownloadLocation == FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS) {

            if (shouldSaveToGallery) {
                addFileToGallery(uri);
            }

            if (open) {
                viewFile(context, uri, mimeType, false);
            } else {
                context.runOnUiThread(() -> {
                    if (shouldSaveToGallery) {
                        Toast.makeText(context, R.string.file_download_finished_gallery, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, String.format(context.getString(R.string.file_download_finished_with_name), filename), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            if (open) {
                viewFile(context, uri, mimeType, true);
            } else {
                context.runOnUiThread(() -> Toast.makeText(context, String.format(context.getString(R.string.file_download_finished_with_name), filename), Toast.LENGTH_SHORT).show());
            }
        }
    }

    public static void viewFile(Activity context, Uri uri, String mimeType, boolean deleteAfterView) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            if (deleteAfterView) {
                lastViewedUriToDelete = uri;
            }
        } catch (ActivityNotFoundException e) {
            String message = context.getResources().getString(R.string.file_handler_not_found);
            context.runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        } catch (Exception ex) {
            GNLog.getInstance().logError(TAG, "viewFile: Exception:", ex);
        }
    }

    private void addFileToGallery(Uri uri) {
        Log.d(TAG, "addFileToGallery: Adding to Albums . . .");
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(uri);
        context.sendBroadcast(mediaScanIntent);
    }

    public void onAppResume() {
        if (lastViewedUriToDelete == null || lastViewedUriToDelete.getPath() == null) return;
        Log.d(TAG, "onAppResume: Deleting file after viewing: " + lastViewedUriToDelete.getPath());
        ContentResolver contentResolver = context.getContentResolver();
        contentResolver.delete(lastViewedUriToDelete, null, null);
    }

    public static Uri createExternalFileUri(ContentResolver contentResolver, String filename, String mimetype, String environment) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, environment);

        Uri baseExternalUri = getBaseExternalUriForEnvironment(environment);

        if (baseExternalUri == null) {
            return null;
        }

        return createFileUri(baseExternalUri, contentResolver, contentValues, filename, mimetype);
    }

    private static Uri createFileUri(Uri baseExternalUri, ContentResolver contentResolver, ContentValues contentValues, String filename, String mimetype) {
        try {
            // Let the system create the unique URI first. Some device models(e.g., Samsung) will throw IllegalStateException
            // https://stackoverflow.com/questions/61654022/java-lang-illegalstateexception-failed-to-build-unique-file-storage-emulated
            Uri uri = contentResolver.insert(baseExternalUri, contentValues);

            // On certain Android versions (e.g., Android 10), a null URI may be returned due to a System-thrown SQLiteConstraintException.
            // We handle this by forcibly creating one.
            if (uri == null) {
                uri = createUniqueFileUri(baseExternalUri, contentResolver, contentValues, filename, mimetype);
            }
            return uri;
        } catch (IllegalStateException ex) {
            return createUniqueFileUri(baseExternalUri, contentResolver, contentValues, filename, mimetype);
        }
    }

    private static Uri createUniqueFileUri(Uri baseExternalUri, ContentResolver contentResolver, ContentValues contentValues, String filename, String mimetype) {
        try {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype);
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, getUniqueExternalFileName(contentResolver, baseExternalUri, filename, extension));
            return contentResolver.insert(baseExternalUri, contentValues);
        } catch (IllegalStateException ex) {
            return createUniqueFileUriWithTimeStamp(baseExternalUri, contentResolver, contentValues, filename);
        }
    }

    private static Uri createUniqueFileUriWithTimeStamp(Uri baseExternalUri, ContentResolver contentResolver, ContentValues contentValues, String filename) {
        try {
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename + "_" + System.currentTimeMillis());
            return contentResolver.insert(baseExternalUri, contentValues);
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    public static  String getFileNameFromUri(Uri uri, ContentResolver contentResolver) {
        String fileName = null;

        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        Cursor cursor = contentResolver.query(uri, projection, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    fileName = cursor.getString(columnIndex);
                }
            } finally {
                cursor.close();
            }
        }

        return fileName;
    }

    private static Uri getBaseExternalUriForEnvironment(String environment) {
        if (Objects.equals(environment, Environment.DIRECTORY_PICTURES)) {
            return MediaStore.Images.Media.getContentUri("external");
        } else if (Objects.equals(environment, Environment.DIRECTORY_DOWNLOADS)) {
            return MediaStore.Files.getContentUri("external");
        }
        return null;
    }

    private static String getUniqueExternalFileName(ContentResolver contentResolver, Uri baseUri, String filename, String extension) {
        int suffix = 1;
        String newFilename = filename;

        while (externalFileExists(contentResolver, baseUri, newFilename + "." + extension)) {
            newFilename = filename + " (" + suffix + ")";
            suffix++;
        }

        return newFilename;
    }

    private static boolean externalFileExists(ContentResolver contentResolver, Uri baseUri, String filename) {
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] selectionArgs = {filename};

        try (Cursor cursor = contentResolver.query(baseUri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "externalFileExists: ", e);
        }

        return false;
    }

    public static File createOutputFile(File dir, String filename, String extension) {
        return new File(dir, FileDownloader.getUniqueFileName(filename + "." + extension, dir));
    }

    public static String getUniqueFileName(String fileName, File dir) {
        File file = new File(dir, fileName);

        if (!file.exists()) {
            return fileName;
        }

        int count = 1;
        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
        String ext = fileName.substring(fileName.lastIndexOf('.'));
        String newFileName = nameWithoutExt + "_" + count + ext;
        file = new File(dir, newFileName);

        while (file.exists()) {
            count++;
            newFileName = nameWithoutExt + "_" + count + ext;
            file = new File(dir, newFileName);
        }

        return file.getName();
    }

    public static String getFilenameExtension(String name) {
        int pos = name.lastIndexOf('.');
        if (pos == -1) {
            return null;
        } else if (pos == 0) {
            return name;
        } else {
            return name.substring(pos + 1);
        }
    }

    public static void runSuccessCallback(GoNativeActivity activity, String callback) {
        if (TextUtils.isEmpty(callback)) return;
        try {
            JSONObject data = new JSONObject();
            data.put("success", true);
            runCallback(activity, callback, data);
        } catch (JSONException e) {
            // ignore
        }
    }

    public static void runErrorCallback(GoNativeActivity activity, String callback, String error) {
        if (TextUtils.isEmpty(callback)) return;
        try {
            JSONObject data = new JSONObject();
            data.put("success", false);
            data.put("error", error);
            runCallback(activity, callback, data);
        } catch (JSONException e) {
            // ignore
        }
    }

    private static void runCallback(GoNativeActivity activity, String callback, JSONObject data) {
        ((Activity) activity).runOnUiThread(() -> activity.runJavascript(LeanUtils.createJsForCallback(callback, data)));
    }

    public static class PreDownloadInfo {
        String url;
        String filename;
        String mimetype;
        boolean shouldSaveToGallery;
        boolean open;
        boolean isBlob;
        String callback;

        public PreDownloadInfo(String url, String filename, String mimetype, boolean shouldSaveToGallery, boolean open, boolean isBlob, String callback) {
            this.url = url;
            this.filename = filename;
            this.mimetype = mimetype;
            this.shouldSaveToGallery = shouldSaveToGallery;
            this.open = open;
            this.isBlob = isBlob;
            this.callback = callback;
        }

        public PreDownloadInfo(String url, String filename, boolean isBlob, boolean open, String callback) {
            this.url = url;
            this.filename = filename;
            this.isBlob = isBlob;
            this.open = open;
            this.callback = callback;
        }
    }
}
