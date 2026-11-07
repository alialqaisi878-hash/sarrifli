package com.alsaqi.sarrifli;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/* Every ~15 minutes Android wakes this job (even when the app is closed). It reads the newest
   notices from Firestore's REST API (the notifs collection is public-read) and shows the new ones. */
public class NotifJob extends JobService {
    static final String PREFS = "notif";
    static final int JOB_ID = 1;

    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, NotifJob.class))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build());
    }

    @Override
    public boolean onStartJob(final JobParameters p) {
        new Thread(new Runnable() {
            public void run() {
                try { check(NotifJob.this); } catch (Exception e) { /* try again next round */ }
                jobFinished(p, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) {
        return true;
    }

    static void check(Context c) throws Exception {
        SharedPreferences sp = c.getSharedPreferences(PREFS, 0);
        String pid = sp.getString("pid", ""), key = sp.getString("key", "");
        if (pid.length() == 0) return;
        long last = sp.getLong("last", 0);
        boolean paid = sp.getBoolean("paid", false);
        boolean cust = "cust".equals(sp.getString("role", ""));
        URL u = new URL("https://firestore.googleapis.com/v1/projects/" + URLEncoder.encode(pid, "UTF-8")
                + "/databases/(default)/documents:runQuery" + (key.length() > 0 ? "?key=" + URLEncoder.encode(key, "UTF-8") : ""));
        HttpURLConnection h = (HttpURLConnection) u.openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(15000);
        h.setRequestMethod("POST");
        h.setDoOutput(true);
        h.setRequestProperty("Content-Type", "application/json");
        String q = "{\"structuredQuery\":{\"from\":[{\"collectionId\":\"notifs\"}],\"orderBy\":[{\"field\":{\"fieldPath\":\"at\"},\"direction\":\"DESCENDING\"}],\"limit\":5}}";
        OutputStream o = h.getOutputStream();
        o.write(q.getBytes("UTF-8"));
        o.close();
        if (h.getResponseCode() != 200) return;
        InputStream in = h.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        JSONArray arr = new JSONArray(bo.toString("UTF-8"));
        long top = last;
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject d = arr.getJSONObject(i).optJSONObject("document");
            if (d == null) continue;
            JSONObject f = d.optJSONObject("fields");
            if (f == null) continue;
            long at = num(f.optJSONObject("at"));
            if (at <= last) continue;
            top = Math.max(top, at);
            String target = str(f.optJSONObject("target"));
            if (cust ? !(target.length() == 0 || target.equals("all") || target.equals("cust"))
                     : (target.equals("cust") || (target.equals("paid") && !paid) || (target.equals("free") && paid))) continue;
            if (last == 0) continue; // first run: remember the newest, don't replay old notices
            show(c, str(f.optJSONObject("title")), str(f.optJSONObject("body")), (int) (at % 100000));
        }
        sp.edit().putLong("last", top).apply();
    }

    static String str(JSONObject v) {
        return v == null ? "" : v.optString("stringValue", "");
    }

    static long num(JSONObject v) {
        if (v == null) return 0;
        if (v.has("integerValue")) return Long.parseLong(v.optString("integerValue", "0"));
        return (long) v.optDouble("doubleValue", 0);
    }

    static void show(Context c, String title, String body, int id) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (title == null || title.length() == 0) title = "صرفلي";
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                Class<?> ch = Class.forName("android.app.NotificationChannel");
                Object chan = ch.getConstructor(String.class, CharSequence.class, int.class).newInstance("news", "إشعارات صرفلي", 4);
                NotificationManager.class.getMethod("createNotificationChannel", ch).invoke(nm, chan);
                b = Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(c, "news");
            } else {
                b = new Notification.Builder(c);
                b.setPriority(Notification.PRIORITY_HIGH);
                b.setDefaults(Notification.DEFAULT_ALL);
            }
            Intent open = new Intent(c, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 0, open, 0x04000000 /* FLAG_IMMUTABLE */ | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setColor(0xFFD2A957).setAutoCancel(true).setContentIntent(pi);
            nm.notify(id, b.build());
        } catch (Exception e) {
            /* notifications blocked or unavailable */
        }
    }
}
