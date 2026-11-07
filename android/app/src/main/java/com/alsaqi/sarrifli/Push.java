package com.alsaqi.sarrifli;

import android.content.SharedPreferences;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

/* Instant notices from the admin panel (FCM), shown even when the app is closed. */
public class Push extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage m) {
        Map<String, String> d = m.getData();
        String title = d.get("title"), body = d.get("body");
        if (title == null && m.getNotification() != null) {
            title = m.getNotification().getTitle();
            body = m.getNotification().getBody();
        }
        long at = 0;
        try {
            at = Long.parseLong(d.get("at"));
        } catch (Exception ignored) {
        }
        SharedPreferences sp = getSharedPreferences(NotifJob.PREFS, 0);
        /* The 15-minute check skips anything at or before "last", so this notice is not shown twice. */
        if (at > 0) {
            if (at <= sp.getLong("last", 0)) return;
            sp.edit().putLong("last", at).apply();
        }
        NotifJob.show(this, title == null ? "صرفلي" : title, body == null ? "" : body, (int) (System.currentTimeMillis() % 100000));
    }

    @Override
    public void onNewToken(String t) {
        getSharedPreferences(NotifJob.PREFS, 0).edit().putString("fcm", t).apply();
    }
}
