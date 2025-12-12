package com.lvr.standclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import android.app.Activity;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private BatteryStatusReceiver batteryReceiver;

    private MyView myView;

    private static final String TAG = "MainActivity";
    private static final String VIDEO_FOLDER = "Movies";
    private CrossFadeVideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // https://stackoverflow.com/questions/7549182/android-paint-measuretext-vs-gettextbounds
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;

        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(flags);

        if(currentApiVersion >= android.os.Build.VERSION_CODES.KITKAT)
        {

            getWindow().getDecorView().setSystemUiVisibility(flags);

            // Code below is to handle presses of Volume up or Volume down.
            // Without this, after pressing volume buttons, the navigation bar will
            // show up and won't hide
            final View decorView = getWindow().getDecorView();
            decorView
                    .setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener()
                    {

                        @Override
                        public void onSystemUiVisibilityChange(int visibility)
                        {
                            if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                            {
                                decorView.setSystemUiVisibility(flags);
                            }
                        }
                    });
        }
// Create a FrameLayout to hold both views
        FrameLayout container = new FrameLayout(this);


        // Create and add video background view (bottom layer)
        videoView = new CrossFadeVideoView(this);
        container.addView(videoView);

// Create and add clock view (top layer)
        myView = new MyView(this);
        container.addView(myView);

        myView.setVideoView(videoView);

        setContentView(container);

// Load video from external storage
        loadVideo();

        batteryReceiver = new BatteryStatusReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(batteryReceiver, filter);

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.cleanup();
        }
        unregisterReceiver(batteryReceiver);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Trigger spider animation
            myView.triggerSpider();
            // Return true to consume the event and prevent volume change
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateView() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        // By passing null as the BroadcastReceiver, we get the last sticky broadcast
        Intent batteryStatus = registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryLevel = (level != -1 && scale != -1) ? 100 * level / scale : 0;

            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            // Update the custom view
            if (myView != null) {
                myView.setBatteryLevel(isCharging ? -1 : batteryLevel);

                myView.postInvalidate();
            }
        }
    }

    private void loadVideo() {
        File videoDir = null;

        // Try external storage first (sdcard/StandClockVideos/)
        File externalDir = new File(Environment.getExternalStorageDirectory(), VIDEO_FOLDER);
        Log.d(TAG, "Checking: " + externalDir.getAbsolutePath());
        if (externalDir.exists() && externalDir.isDirectory()) {
            videoDir = externalDir;
            Log.d(TAG, "Found video directory: " + externalDir.getAbsolutePath());
        } else {
            // Try app-specific external storage
            File appExternalDir = new File(getExternalFilesDir(null), VIDEO_FOLDER);
            Log.d(TAG, "Checking: " + appExternalDir.getAbsolutePath());
            if (appExternalDir.exists() && appExternalDir.isDirectory()) {
                videoDir = appExternalDir;
                Log.d(TAG, "Found video directory: " + appExternalDir.getAbsolutePath());
            }
        }

        if (videoDir != null) {
            File[] videoFiles = videoDir.listFiles();
            Log.d(TAG, "Files in directory: " + (videoFiles != null ? videoFiles.length : 0));

            if (videoFiles != null && videoFiles.length > 0) {
                List<String> videoPaths = new ArrayList<>();

                for (File file : videoFiles) {
                    Log.d(TAG, "Found file: " + file.getName() + " (" + file.length() + " bytes)");
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".mov") || name.endsWith(".mp4") ||
                            name.endsWith(".3gp") || name.endsWith(".mkv")) {
                        videoPaths.add(file.getAbsolutePath());
                        Log.d(TAG, "Added to playlist: " + file.getName());
                    }
                }

                if (!videoPaths.isEmpty()) {
                    Log.d(TAG, "Setting playlist with " + videoPaths.size() + " videos");
                    videoView.setVideoPlaylist(videoPaths);
                    return;
                }
            }
        }

        Log.w(TAG, "No video files found in: " + VIDEO_FOLDER);
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            videoView.pauseVideo();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null) {
            videoView.resumeVideo();
        }
    }


    private class BatteryStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                updateView();
            }
        }
    }
}
