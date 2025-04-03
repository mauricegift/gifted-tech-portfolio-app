package co.median.android;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import co.median.median_core.AppConfig;
import co.median.median_core.GNLog;
import co.median.median_core.LeanUtils;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";
    private static final String EXTRA_DOWNLOAD_ID = "download_id";
    private static final String ACTION_CANCEL_DOWNLOAD = "action_cancel_download";
    private static final int BUFFER_SIZE = 4096;
    private static final int timeout = 5; // in seconds
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FileDownloader fileDownloader;
    private final Map<Integer, DownloadTask> downloadTasks = new HashMap<>();
    private int downloadId = 0;
    private String userAgent;

    @Override
    public void onCreate() {
        super.onCreate();
        AppConfig appConfig = AppConfig.getInstance(this);
        this.userAgent = appConfig.userAgent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(ACTION_CANCEL_DOWNLOAD)) {
            int id = intent.getIntExtra(EXTRA_DOWNLOAD_ID, 0);
            cancelDownload(id);
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new DownloadBinder();
    }

    public class DownloadBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }

    public void setFileDownloader(FileDownloader fileDownloader) {
        this.fileDownloader = fileDownloader;
    }

    public void startDownload(FileDownloader.PreDownloadInfo preDownloadInfo, FileDownloader.DownloadLocation location, DownloadCallback callback) {
        startDownload(
                preDownloadInfo.url,
                preDownloadInfo.filename,
                preDownloadInfo.mimetype,
                preDownloadInfo.shouldSaveToGallery,
                preDownloadInfo.open,
                location,
                callback
        );
    }

    public void startDownload(String url, String filename, String mimetype, boolean shouldSaveToGallery, boolean open, FileDownloader.DownloadLocation location, DownloadCallback callback) {
        DownloadTask downloadTask = new DownloadTask(url, filename, mimetype, shouldSaveToGallery, open, location, callback);
        downloadTasks.put(downloadTask.getId(), downloadTask);
        downloadTask.startDownload();
    }

    public void cancelDownload(int downloadId) {
        DownloadTask downloadTask = downloadTasks.get(downloadId);
        if (downloadTask != null && downloadTask.isDownloading()) {
            downloadTask.cancelDownload();
        }
    }

    public interface DownloadCallback {
        void onSuccess();
        void onFailed(String error);
    }

    private class DownloadTask {
        private final int id;
        private final String url;
        private boolean isDownloading;
        private HttpURLConnection connection;
        private InputStream inputStream;
        private FileOutputStream outputStream;
        private File outputFile = null;
        private Uri downloadUri;
        private String filename;
        private String extension;
        private String mimetype;
        private boolean saveToGallery;
        private boolean openOnFinish;
        private final FileDownloader.DownloadLocation location;
        private final DownloadCallback callback;
        AtomicReference<String> finalFilename;
        private boolean isDownloadSuccessful = false;

        public DownloadTask(String url, String filename, String mimetype, boolean saveToGallery, boolean open, FileDownloader.DownloadLocation location, DownloadCallback callback) {
            this.id = downloadId++;
            this.url = url;
            this.filename = filename;
            this.mimetype = mimetype;
            this.isDownloading = false;
            this.saveToGallery = saveToGallery;
            this.openOnFinish = open;
            this.location = location;
            this.callback = callback;
            this.finalFilename = new AtomicReference<>(filename);
        }

        public int getId() {
            return id;
        }

        public boolean isDownloading() {
            return isDownloading;
        }

        public void startDownload() {
            Log.d(TAG, "startDownload: Starting download");
            isDownloading = true;

            new Thread(() -> {
                Log.d(TAG, "startDownload: Thread started");
                try {
                    Uri uri = Uri.parse(url);
                    switch (Objects.requireNonNull(uri.getScheme())) {
                        case "http", "https":
                            downloadAsHttpUri();
                            break;
                        case "data":
                            downloadAsDataUri();
                            break;
                        default:
                            callback.onFailed("Unsupported URI scheme: " + uri.getScheme());
                            // should not continue
                            return;
                    }

                    // download was successful
                    this.isDownloadSuccessful = true;
                    isDownloading = false;

                    if (downloadUri == null && outputFile != null) {
                        downloadUri = FileProvider.getUriForFile(DownloadService.this, DownloadService.this.getApplicationContext().getPackageName() + ".fileprovider", outputFile);
                    }

                    if (fileDownloader != null) {
                        fileDownloader.handleDownloadUri(downloadUri, mimetype, saveToGallery, openOnFinish, finalFilename.get());
                    }

                    callback.onSuccess();

                } catch (FileNotFoundException fileNotFoundException) {
                    callback.onFailed("Failed to create download file. filename = " + filename + ", mimetype = " + mimetype + ".");
                } catch (Exception e) {
                    callback.onFailed("Unexpected error occurred: " + e.getLocalizedMessage());
                } finally {

                    // delete file if the download failed
                    if (!isDownloadSuccessful && outputFile != null) {
                        outputFile.delete();
                    }

                    // reset
                    isDownloading = false;
                    outputFile = null;
                    isDownloadSuccessful = false;
                    closeConnections();
                }
            }).start();
        }

        private void downloadAsHttpUri() throws IOException {
            URL downloadUrl = new URL(url);
            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setConnectTimeout(timeout * 1000);
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                GNLog.getInstance().logError(TAG, "Server returned HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage());
                isDownloading = false;

                callback.onFailed("Response code: " + connection.getResponseCode() + ". " + connection.getResponseMessage());
                return;
            }

            double fileSizeInMB = connection.getContentLength() / 1048576.0;
            Log.d(TAG, "startDownload: File size in MB: " + fileSizeInMB);

            if (connection.getHeaderField("Content-Type") != null)
                mimetype = connection.getHeaderField("Content-Type");

            if (!TextUtils.isEmpty(filename)) {
                extension = FileDownloader.getFilenameExtension(filename);
                if (TextUtils.isEmpty(extension)) {
                    extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype);
                } else if (Objects.equals(filename, extension)) {
                    filename = "download";
                } else {
                    filename = filename.substring(0, filename.length() - (extension.length() + 1));
                    mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                }
            } else {
                // guess file name and extension
                String guessedName = LeanUtils.guessFileName(url,
                        connection.getHeaderField("Content-Disposition"),
                        mimetype);
                int pos = guessedName.lastIndexOf('.');

                if (pos == -1) {
                    filename = guessedName;
                    extension = "";
                } else if (pos == 0) {
                    filename = "download";
                    extension = guessedName.substring(1);
                } else {
                    filename = guessedName.substring(0, pos);
                    extension = guessedName.substring(pos + 1);
                }

                if (!TextUtils.isEmpty(extension)) {
                    // Update mimetype based on final filename extension
                    mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                }
            }

            generateFileStream(); // this throws exception if fails

            int fileLength = connection.getContentLength();
            inputStream = connection.getInputStream();

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            int bytesDownloaded = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1 && isDownloading) {
                outputStream.write(buffer, 0, bytesRead);
                bytesDownloaded += bytesRead;
                int progress = bytesDownloaded * 100 / fileLength;
                Log.d(TAG, "startDownload: Download progress: " + progress);
            }
        }

        private void downloadAsDataUri() throws IOException {

            Uri uri = Uri.parse(url);
            String schemeSpecificPart = uri.getSchemeSpecificPart();

            mimetype = schemeSpecificPart.substring(0, schemeSpecificPart.indexOf(";"));
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype);

            if (TextUtils.isEmpty(filename)) {
                // Extract filename from data uri if present
                int filenameIndex = schemeSpecificPart.indexOf("filename=");
                if (filenameIndex != -1) {
                    int start = filenameIndex + "filename=".length();
                    int end = schemeSpecificPart.indexOf(";", start);
                    String encodedFilename = end != -1 ? schemeSpecificPart.substring(start, end) : schemeSpecificPart.substring(start);
                    filename = Uri.decode(encodedFilename);
                }

                // Use default filename
                if (TextUtils.isEmpty(filename)) {
                    filename = "download";
                }
            }

            if (!Objects.equals(filename, "download")) {
                String filenameExtension = FileDownloader.getFilenameExtension(filename);
                if (!TextUtils.isEmpty(filenameExtension)) {
                    extension = filenameExtension;
                    if (!Objects.equals(filename, extension)) {
                        filename = filename.substring(0, filename.length() - (filenameExtension.length() + 1));
                        mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    }
                }
            }

            generateFileStream(); // this throws exception if fails

            boolean isBase64 = schemeSpecificPart.contains(";base64");

            if (isBase64) {
                String data = schemeSpecificPart.substring(schemeSpecificPart.indexOf(";base64,") + 8);
                byte[] decodedBytes = Base64.decode(data, Base64.DEFAULT);
                outputStream.write(decodedBytes);
            } else {
                String data = schemeSpecificPart.substring(schemeSpecificPart.indexOf(",") + 1);
                outputStream.write(data.getBytes(StandardCharsets.UTF_8));
            }
        }

        private void generateFileStream() throws FileNotFoundException {
            if (location == FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    ContentResolver contentResolver = getApplicationContext().getContentResolver();
                    if (saveToGallery && mimetype.contains("image")) {
                        downloadUri = FileDownloader.createExternalFileUri(contentResolver, filename, mimetype, Environment.DIRECTORY_PICTURES);
                    } else {
                        downloadUri = FileDownloader.createExternalFileUri(contentResolver, filename, mimetype, Environment.DIRECTORY_DOWNLOADS);
                        saveToGallery = false;
                    }
                    if (downloadUri != null) {
                        finalFilename.set(FileDownloader.getFileNameFromUri(downloadUri, contentResolver));
                        outputStream = (FileOutputStream) contentResolver.openOutputStream(downloadUri);
                    } else {
                        isDownloading = false;
                        handler.post(() -> {
                            Toast.makeText(DownloadService.this, getString(R.string.file_download_error), Toast.LENGTH_SHORT).show();
                        });
                        GNLog.getInstance().logError(TAG, "Error creating file - " +
                                "filename: " + filename + ", " +
                                "mimetype: " + mimetype);
                    }
                } else {
                    if (saveToGallery) {
                        outputFile = FileDownloader.createOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename, extension);
                    } else {
                        outputFile = FileDownloader.createOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename, extension);
                    }
                    finalFilename.set(outputFile.getName());
                    outputStream = new FileOutputStream(outputFile);
                }
            } else {
                this.openOnFinish = true;
                outputFile = FileDownloader.createOutputFile(getFilesDir(), filename, extension);
                finalFilename.set(outputFile.getName());
                outputStream = new FileOutputStream(outputFile);
            }
        }

        public void closeConnections () {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
                if (connection != null) connection.disconnect();
            } catch (IOException e) {
                GNLog.getInstance().logError(TAG, "startDownload: ", e);
            }
        }

        public void cancelDownload() {
            isDownloading = false;
            Toast.makeText(DownloadService.this, getString(R.string.download_canceled) + " " + filename, Toast.LENGTH_SHORT).show();
        }
    }
}