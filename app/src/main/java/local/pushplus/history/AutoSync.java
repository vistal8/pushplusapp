package local.pushplus.history;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class AutoSync extends BroadcastReceiver {
    private static final long INTERVAL_MS = 3L * 60L * 60L * 1000L;
    private static final int REQUEST_CODE = 7001;

    @Override
    public void onReceive(final Context context, Intent intent) {
        final SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(MainActivity.KEY_AUTO_SYNC, false)) {
            cancel(context);
            return;
        }
        schedule(context);
        new Thread(new Runnable() {
            @Override public void run() {
                String token = prefs.getString(MainActivity.KEY_USER_TOKEN, "");
                String secret = prefs.getString(MainActivity.KEY_SECRET_KEY, "");
                if (token.length() == 0 || secret.length() == 0) return;
                try {
                    MainActivity.syncMessages(context, token, secret, false, null);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    static void schedule(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        long next = System.currentTimeMillis() + INTERVAL_MS;
        PendingIntent pi = pendingIntent(context);
        if (Build.VERSION.SDK_INT >= 23) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    static void cancel(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(pendingIntent(context));
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, AutoSync.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}
