package cn.gov.xivpn2;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class RulesDownloadWorker extends Worker {
    private static final String TAG = "RulesDownloadWorker";
    private static final String RULES_URL = "https://github.com/Icewind1983/test/releases/download/latest/rules.json";

    public RulesDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            File filesDir = getApplicationContext().getFilesDir();
            File rulesFile = new File(filesDir, "rules.json");

            if (downloadFile(RULES_URL, rulesFile)) {
                Log.d(TAG, "Rules file downloaded successfully");
                
                // Перезапустить VPN сервис после успешной загрузки
                VPNServiceRestarter.restartVPNService(getApplicationContext());
                
                return Result.success();
            } else {
                Log.w(TAG, "Failed to download rules file");
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error downloading rules file", e);
            return Result.retry();
        }
    }

    private boolean downloadFile(String urlString, File outputFile) {
        try {
            URL url = new URL(urlString);
            URLConnection connection;

            // Используем VPN DNS если доступен
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ConnectivityManager connectivityManager = (ConnectivityManager) 
                    getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                
                if (connectivityManager != null) {
                    Network network = connectivityManager.getActiveNetwork();
                    if (network != null) {
                        connection = network.openConnection(url);
                    } else {
                        connection = url.openConnection();
                    }
                } else {
                    connection = url.openConnection();
                }
            } else {
                connection = url.openConnection();
            }

            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                httpConnection.setConnectTimeout(15000);
                httpConnection.setReadTimeout(15000);
                httpConnection.setInstanceFollowRedirects(true);
                httpConnection.setRequestProperty("User-Agent", "Mozilla/5.0");

                try {
                    int responseCode = httpConnection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_MOVED_TEMP 
                        || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                        
                        try (InputStream input = httpConnection.getInputStream();
                             FileOutputStream output = new FileOutputStream(outputFile)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = input.read(buffer)) != -1) {
                                output.write(buffer, 0, bytesRead);
                            }
                            Log.d(TAG, "Downloaded: " + outputFile.getAbsolutePath() + " (HTTP " + responseCode + ")");
                            return true;
                        }
                    } else {
                        Log.w(TAG, "Failed to download rules: HTTP " + responseCode);
                        return false;
                    }
                } finally {
                    httpConnection.disconnect();
                }
            } else {
                Log.w(TAG, "Not an HTTP connection: " + urlString);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error downloading rules file", e);
            return false;
        }
    }
}