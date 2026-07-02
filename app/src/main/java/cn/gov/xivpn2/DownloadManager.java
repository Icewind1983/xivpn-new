package cn.gov.xivpn2;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingWorkPolicy;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final String DAILY_DOWNLOAD_WORK_TAG = "daily_file_download_at_10_00"; // Тег обновлен

    /**
     * Запланировать ежедневную загрузку файлов в 10:00
     */
	public static void scheduleDailyDownloadAt1000(Context context) {
    // 1. Определяем ограничения (например, нужен интернет)
    	Constraints constraints = new Constraints.Builder()
            	.setRequiredNetworkType(NetworkType.CONNECTED)
            	.build();

    // 2. Создаем PeriodicWorkRequest, который будет повторяться каждые 24 часа.
    // Период должен быть не менее 15 минут согласно документации.
    	PeriodicWorkRequest dailyDownloadRequest = new PeriodicWorkRequest.Builder(
            	FileDownloadWorker.class,
            	24, TimeUnit.HOURS) // Периодичность
            	.setConstraints(constraints)
            	.setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS) // Задержка до первого запуска
            	.addTag(DAILY_DOWNLOAD_WORK_TAG)
            	.build();

    // 3. Ставим задачу в очередь с политикой REPLACE (заменить старую, если есть)
    	WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            	DAILY_DOWNLOAD_WORK_TAG,
            	ExistingPeriodicWorkPolicy.REPLACE,
            	dailyDownloadRequest);

    	Log.d(TAG, "Ежедневная загрузка успешно запланирована.");
	}

// Вспомогательный метод для расчета задержки до 10:00 следующего дня
	private static long calculateInitialDelay() {
    	Calendar now = Calendar.getInstance();
    	Calendar nextRun = Calendar.getInstance();
    	nextRun.set(Calendar.HOUR_OF_DAY, 10);
    	nextRun.set(Calendar.MINUTE, 0);
    	nextRun.set(Calendar.SECOND, 0);
    	nextRun.set(Calendar.MILLISECOND, 0);

    // Если время уже прошло, планируем на завтра
    	if (nextRun.before(now) || nextRun.equals(now)) {
        	nextRun.add(Calendar.DAY_OF_MONTH, 1);
    	}

    	return nextRun.getTimeInMillis() - now.getTimeInMillis();
	}
}
