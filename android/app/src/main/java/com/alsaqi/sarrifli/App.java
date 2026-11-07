package com.alsaqi.sarrifli;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/* Starts Firebase from the values below (no google-services.json plugin needed). */
public class App extends Application {
    /* From Firebase Console: Project settings > Your apps > Android app (com.alsaqi.sarrifli). */
    static final String APP_ID = "1:452057270832:android:37b03032816bd405c93bf5";
    static final String API_KEY = "AIzaSyCz-2tys1jMG5wmmztmG84mdtc1PaNRnGc";
    static final String PROJECT = "sarrifli-a37aa";
    static final String SENDER = "452057270832";

    static boolean ready() {
        return APP_ID.startsWith("1:");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!ready() || !FirebaseApp.getApps(this).isEmpty()) return;
        try {
            FirebaseApp.initializeApp(this, new FirebaseOptions.Builder()
                    .setApplicationId(APP_ID).setApiKey(API_KEY).setProjectId(PROJECT).setGcmSenderId(SENDER)
                    .setStorageBucket(PROJECT + ".firebasestorage.app").build());
        } catch (Exception ignored) {
        }
    }
}
