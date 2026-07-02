package cn.gov.xivpn2;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

//import cn.gov.xivpn2.Release;


public class FileDownloadWorker extends Worker {
    private static final String TAG = "FileDownloadWorker";

    public FileDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {    
        final int MAX_RETRIES = 3; // Максимальное количество повторных попыток
        int runAttemptCount = getRunAttemptCount();

        if (runAttemptCount > MAX_RETRIES) {
            // Если количество попыток превышено, прекращаем повторы.
            Log.e(TAG, "Maximum retry attempts (" + MAX_RETRIES + ") exceeded. Aborting task.");
            return Result.failure();
        }
        try {
            // Получаем пути к основной и временной папкам приложения
            File filesDir = getApplicationContext().getFilesDir();
            File tmpDir = new File(filesDir, "tmp");
            
            // Убедимся, что временная папка существует
            if (!tmpDir.exists()) {
                if (!tmpDir.mkdirs()) {
                    Log.e(TAG, "Failed to create temporary directory: " + tmpDir.getAbsolutePath());
                    return Result.failure(); // Критическая ошибка, если не удалось создать папку
                }
            }

            boolean allSuccess = true;

            // --- Шаг 1: Загружаем файлы во временную папку ---
            File tempDlc = new File(tmpDir, "dlc.dat");
            if (!downloadFile("https://github.com/Icewind1983/test/releases/download/latest/dlc.dat", tempDlc)) {
                allSuccess = false;
            }

            File tempGeosite = new File(tmpDir, "geosite.dat");
            if (!downloadFile("https://raw.githubusercontent.com/runetfreedom/russia-blocked-geosite/release/geosite.dat", tempGeosite)) {
                allSuccess = false;
            }

            File tempRules = new File(tmpDir, "rules.json");
            if (!downloadFile("https://github.com/Icewind1983/test/releases/download/latest/rules.json", tempRules)) {
                allSuccess = false;
            }

            File tempDlcHash = new File(tmpDir, "dlc.sha256sum");
            if (!downloadFile("https://github.com/Icewind1983/test/releases/download/latest/dlc.sha256sum", tempDlcHash)) {
                allSuccess = false;
            }

            File tempGeositeHash = new File(tmpDir, "geosite.dat.sha256sum");
            if (!downloadFile(
                    "https://raw.githubusercontent.com/runetfreedom/russia-blocked-geosite/release/geosite.dat.sha256sum",
                    tempGeositeHash)) {
                allSuccess = false;
            }

            // --- Шаг 2: Действуем в зависимости от результата загрузки ---
            if (allSuccess) {
                Log.d(TAG, "All files downloaded successfully to temporary folder.");

                if (!tempDlcHash.exists() || !verifySha256(tempDlc, tempDlcHash)) {
                    Log.e(TAG, "SHA-256 check failed for 'dlc.dat'. Integrity compromised.");
                    return Result.failure(); // Не продолжаем, если данные повреждены
                } else {
                    Log.d(TAG, "'dlc.dat' integrity verified.");
                }

                // Проверяем целостность geosite.dat
                if (!tempGeositeHash.exists() || !verifySha256(tempGeosite, tempGeositeHash)) {
                    Log.e(TAG, "SHA-256 check failed for 'geosite.dat'. Integrity compromised.");
                    return Result.failure(); // Не продолжаем, если данные повреждены
                } else {
                    Log.d(TAG, "'geosite.dat' integrity verified.");
                }


                // --- Шаг 2a: Перемещаем файлы из tmp в конечную папку ---
                boolean moveSuccess = true;
                moveSuccess &= moveFile(tempDlc, new File(filesDir, "dlc.dat"));
                moveSuccess &= moveFile(tempGeosite, new File(filesDir, "geosite.dat"));
                moveSuccess &= moveFile(tempRules, new File(filesDir, "rules.json"));

                if (moveSuccess) {
                    Log.d(TAG, "All files moved to the final destination.");

                    // Перезапустить VPN сервис после успешной загрузки и перемещения
                    VPNServiceRestarter.restartVPNService(getApplicationContext());

                    return Result.success();
                } else {
                    Log.w(TAG, "Failed to move some files from temporary folder.");
                    // Даже если загрузка прошла успешно, но перемещение нет — это ошибка.
                    // Можно либо вернуть failure, либо retry. Здесь выберем retry.
                    return Result.retry();
                }
            } else {
                Log.w(TAG, "Some files failed to download. Will retry later.");
                // --- Шаг 2b: При ошибке загрузки возвращаем результат RETRY ---
                // WorkManager автоматически повторит задачу через некоторое время.
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during file download or move operation.", e);
            return Result.retry();
        }
    }


    private boolean verifySha256(File fileToCheck, File sha256sumFile) {
        if (!fileToCheck.exists() || !sha256sumFile.exists()) {
            Log.e(TAG, "Cannot verify SHA-256: missing file or hash.");
            return false;
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            Log.e(TAG, "SHA-256 algorithm not found.", e);
            return false;
        }

        // Читаем ожидаемый хэш из файла .sha256sum
        String expectedHash = null;
        try (InputStream fis = new FileInputStream(sha256sumFile)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                String line = new String(buffer, 0, len).trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    // Хэш обычно является первым токеном в строке
                    expectedHash = line.split("\\s+")[0].toLowerCase(Locale.ROOT);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading SHA-256 sum file.", e);
            return false;
        }

        if (expectedHash == null || expectedHash.length() != 64) {
            Log.e(TAG, "Invalid or empty SHA-256 sum in file.");
            return false;
        }

        // Вычисляем хэш целевого файла
        String computedHash = null;
        try (FileInputStream fis = new FileInputStream(fileToCheck);
             FileChannel channel = fis.getChannel()) {
            ByteBuffer buf = ByteBuffer.allocateDirect(8192);
            while (channel.read(buf) > 0) {
                buf.flip();
                digest.update(buf);
                buf.clear();
            }
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            computedHash = hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error computing SHA-256 of file.", e);
            return false;
        }

        boolean isValid = expectedHash.equalsIgnoreCase(computedHash);
        Log.d(TAG, String.format("SHA-256 verification for '%s': %s (computed: %s, expected: %s)",
                fileToCheck.getName(), isValid ? "PASSED" : "FAILED", computedHash, expectedHash));
        return isValid;
    }
    /**
     * Вспомогательный метод для перемещения файла.
     */
    private boolean moveFile(File srcFile, File destFile) {
        if (srcFile == null || !srcFile.exists() || srcFile.isDirectory()) {
            Log.w(TAG, "Source file does not exist or is a directory for move operation.");
            return false;
        }

        // Если файл уже на месте, считаем операцию успешной
        if (srcFile.getAbsolutePath().equals(destFile.getAbsolutePath())) {
            Log.d(TAG, "Source and destination are the same: " + srcFile.getName());
            return true;
        }

        // Создаём родительские каталоги для конечного файла, если их нет
        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            Log.e(TAG, "Failed to create destination directory: " + parentDir.getAbsolutePath());
            return false;
        }

        // --- Попытка 1: Быстрый и атомарный перенос в пределах одной ФС ---
        if (srcFile.renameTo(destFile)) {
            Log.d(TAG, "Moved file (renameTo): " + srcFile.getName());
            return true;
        }

        // Если renameTo не сработал, переходим к копированию

        File tempDest = null;
        boolean success = false;

        try (
                FileChannel inChannel = new FileInputStream(srcFile).getChannel();
                FileChannel outChannel = new FileOutputStream(destFile).getChannel();
        ) {
            long size = inChannel.size();
            long position = 0;

            // --- Попытка 2: Копирование через FileChannel ---
            while (position < size) {
                position += inChannel.transferTo(position, size - position, outChannel);
            }
            success = true;
            Log.d(TAG, "Moved file (transferTo): " + srcFile.getName());

        } catch (Exception e) {
            Log.e(TAG, "Error moving file with FileChannel: " + srcFile.getName(), e);

            // --- Попытка 3: Классический способ через потоки как крайняя мера ---
            try (
                InputStream in = new FileInputStream(srcFile);
                FileOutputStream out = new FileOutputStream(destFile);
            ) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
                success = true;
                Log.d(TAG, "Moved file (fallback streams): " + srcFile.getName());
            } catch (Exception ex) {
                Log.e(TAG, "Error moving file with streams: " + srcFile.getName(), ex);
                success = false;
            }
        } finally {
            if (success) {
                // Удаляем исходный файл только после успешного копирования
                if (!srcFile.delete()) {
                    Log.w(TAG, "Downloaded source file could not be deleted: " + srcFile.getName());
                    // Операция перемещения всё равно считается успешной с точки зрения результата,
                    // так как данные в destFile корректны.
                }
            } else {
                // Если копирование не удалось, удаляем недозаписанный файл назначения
                if (destFile.exists() && !destFile.delete()) {
                    Log.w(TAG, "Failed to delete corrupted destination file: " + destFile.getName());
                }
            }
        }

        return success;
    }
    //private String downloadJson(String urlString) throws Exception {
    //    File tempFile = null;
    //    InputStream inputStream = null;
    //    try {
            // 1. Создаем временный файл
    //        tempFile = File.createTempFile("json_response", ".tmp", getApplicationContext().getCacheDir());

            // 2. Скачиваем JSON в этот файл
      //      if (!downloadFile(urlString, tempFile)) {
      //          Log.e(TAG, "Failed to download JSON from " + urlString);
      //          return null;
      //      }

            // 3. Читаем файл в строку
      //      StringBuilder text = new StringBuilder();
      //      inputStream = new FileInputStream(tempFile);
      //      byte[] buffer = new byte[1024];
      //      int len;
      //      while ((len = inputStream.read(buffer)) != -1) {
      //          text.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
      //      }
      //      return text.toString();
      //  } finally {
            // 4. Закрываем потоки и удаляем временный файл
      //      if (inputStream != null) {
      //          inputStream.close();
      //      }
      //      if (tempFile != null && tempFile.exists()) {
      //          tempFile.delete();
      //      }
      //  }
    //}
    private boolean downloadFile(String urlString, File outputFile) {
        HttpURLConnection httpConnection = null;
        ReadableByteChannel rbc = null;
        FileChannel fileChannel = null;

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
                httpConnection = (HttpURLConnection) connection;
                httpConnection.setConnectTimeout(15000);
                httpConnection.setReadTimeout(15000);
                httpConnection.setInstanceFollowRedirects(true);
                httpConnection.setRequestProperty("User-Agent", "Mozilla/5.0");

                int responseCode = httpConnection.getResponseCode();
                // Проверяем на успешный ответ или редирект
                if (responseCode == HttpURLConnection.HTTP_OK ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {

                    // Открываем канал для чтения из сети и канал для записи в файл
                    rbc = Channels.newChannel(httpConnection.getInputStream());
                    fileChannel = new FileOutputStream(outputFile).getChannel();

                    // Переносим данные напрямую. transferFrom() эффективнее, чем цикл с буфером.
                    fileChannel.transferFrom(rbc, 0, Long.MAX_VALUE);

                    Log.d(TAG, "Downloaded: " + outputFile.getName() + " (HTTP " + responseCode + ")");
                    return true;

                } else {
                    Log.w(TAG, "Failed to download " + outputFile.getName() +
                              ": HTTP " + responseCode);
                    return false;
                }
            } else {
                Log.w(TAG, "Not an HTTP connection: " + urlString);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error downloading " + outputFile.getName(), e);
            return false;
        } finally {
            // Безопасное закрытие ресурсов
            try { if (fileChannel != null) fileChannel.close(); } catch (Exception ignored) {}
            try { if (rbc != null) rbc.close(); } catch (Exception ignored) {}
            if (httpConnection != null) {
                httpConnection.disconnect();
            }
        }
    }
    
    /**
     * Проверяет наличие обновления на GitHub и устанавливает его, если оно есть.
     * @return true, если обновление было найдено и запущена установка.
     */
    //private boolean checkAndInstallUpdate() throws Exception {
    //    String currentVersion = BuildConfig.VERSION_NAME; // Текущая версия из build.gradle

        // Запрашиваем последний релиз с GitHub API
    //    String jsonResponse = downloadJson(GITHUB_RELEASES_API);
    //    if (jsonResponse == null) {
    //        return false;
    //    }

        // Парсим JSON с помощью Gson
    //    Gson gson = new Gson();
    //    Release latestRelease = gson.fromJson(jsonResponse, Release.class);

        // Сравниваем версии. Убираем префикс 'v', если он есть.
    //    String remoteVersion = latestRelease.getTagName().startsWith("v")
    //            ? latestRelease.getTagName().substring(1)
    //            : latestRelease.getTagName();

    //    Log.d(TAG, "Current version: " + currentVersion + ", Latest version: " + remoteVersion);

        // Простое сравнение строк версий (работает для v1.0, v1.1, v2.0)
    //    if (isNewerVersion(remoteVersion, currentVersion)) {
    //        Log.d(TAG, "New version found! Starting download...");

            // Находим ссылку на APK-файл в активах релиза
    //        String apkDownloadUrl = null;
    //        for (Asset asset : latestRelease.getAssets()) {
    //            if (asset.getName().endsWith(".apk")) {
    //                apkDownloadUrl = asset.getDownloadUrl();
    //                break;
    //            }
    //        }

    //        if (apkDownloadUrl != null) {
                // Загружаем APK в файлы приложения
    //            File apkFile = new File(getApplicationContext().getFilesDir(), APK_FILE_NAME);
    //            if (downloadFile(apkDownloadUrl, apkFile)) {
                    // Запускаем установку скачанного APK
    //                installApk(apkFile);
    //                return true; // Обновление найдено и запущено
    //            }
    //        }
    //    }
    //    return false; // Обновление не найдено или не удалось его скачать
    //}

    //private boolean isNewerVersion(String remote, String current) {
        // Простой алгоритм сравнения версий (разделенных точкой)
    //    String[] remoteParts = remote.split("\\.");
    //    String[] currentParts = current.split("\\.");

    //    int length = Math.max(remoteParts.length, currentParts.length);
    //    for (int i = 0; i < length; i++) {
    //        int remotePart = i < remoteParts.length ? Integer.parseInt(remoteParts[i]) : 0;
    //        int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;

    //        if (remotePart > currentPart) return true;
    //        if (remotePart < currentPart) return false;
    //    }
    //    return false; // Версии равны
    //}

    //private void installApk(File apkFile) {
    //    Intent intent = new Intent(Intent.ACTION_VIEW);
    //    Uri apkUri = FileProvider.getUriForFile(getApplicationContext(),
    //            getApplicationContext().getPackageName() + ".fileprovider", apkFile);

    //    intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
    //    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Проверяем, что есть активность, способная обработать этот интент
    //    if (intent.resolveActivity(getApplicationContext().getPackageManager()) != null) {
    //        getApplicationContext().startActivity(intent);
    //    } else {
    //        Log.e(TAG, "No activity found to install APK");
    //    }
    //}
}
