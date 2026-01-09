package com.lvr.standclock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrossFadeVideoView extends FrameLayout implements IDisplayMode {

    private static final String TAG = "VideoView";
    private static final int FADE_DURATION = 1000;
    private static final float VIDEO_BRIGHTNESS_DAY = 1.0f;
    private static final float VIDEO_BRIGHTNESS_NIGHT = 0.8f;

    // Start preparing next video this early (relative to ACTUAL video end)
    private static final long PREPARE_BEFORE_END = 3000; // 3 seconds before video ends

    // Start next video playback this early
    private static final long START_ADVANCE_TIME = 300;

    private VideoLayer layer1;
    private VideoLayer layer2;
    private VideoLayer currentLayer;
    private VideoLayer nextLayer;

    private List<String> allVideoPaths = new ArrayList<>();
    private List<String> dayVideos = new ArrayList<>();
    private List<String> nightVideos = new ArrayList<>();
    private int currentVideoIndex = -1;
    private Random random = new Random();
    private ValueAnimator crossFadeAnimator;

    private float currentVideoBrightness = VIDEO_BRIGHTNESS_DAY;
    private float nextVideoBrightness = VIDEO_BRIGHTNESS_DAY;
    private String currentVideoPath = null;
    private String nextVideoPath = null;

    private boolean nextVideoReady = false;
    private boolean nextVideoStarted = false;
    private Runnable scheduledCrossFade = null;
    private Runnable scheduledStart = null;

    private int currentVideoDuration = 0;
    private int nextVideoDuration = 0;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Handler mainHandler;

    // Color background mode (when videos are not displayed)
    private boolean colorBackgroundMode = false;
    private int backgroundColor = android.graphics.Color.WHITE;

    public CrossFadeVideoView(Context context) {
        super(context);

        setLayerType(LAYER_TYPE_HARDWARE, null);

        layer1 = new VideoLayer(context);
        layer2 = new VideoLayer(context);

        layer1.setLayerType(LAYER_TYPE_HARDWARE, null);
        layer2.setLayerType(LAYER_TYPE_HARDWARE, null);

        addView(layer1, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        addView(layer2, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        currentLayer = layer1;
        nextLayer = layer2;
        currentLayer.setAlpha(0f);
        nextLayer.setAlpha(0f);

        backgroundThread = new HandlerThread("VideoBackground", Thread.MAX_PRIORITY);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "CrossFadeVideoView created");
    }

    public void setVideoPlaylist(List<String> videoPaths) {
        this.allVideoPaths = new ArrayList<>(videoPaths);

        dayVideos.clear();
        nightVideos.clear();

        for (String path : videoPaths) {
            String filename = new File(path).getName().toLowerCase();

            if (filename.startsWith("d_")) {
                dayVideos.add(path);
            }

            if (filename.startsWith("n_") || filename.startsWith("dn_")) {
                nightVideos.add(path);
            }
        }

        Log.d(TAG, "Playlist set - Day: " + dayVideos.size() + ", Night: " + nightVideos.size());

        // Check if we're in the video display period
        if (isInVideoPeriod()) {
            // Enable video mode
            this.colorBackgroundMode = false;
            setBackgroundColor(android.graphics.Color.TRANSPARENT);

            postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (layer1.isReady() && layer2.isReady()) {
                        playNextVideo();
                    } else {
                        postDelayed(this, 100);
                    }
                }
            }, 100);
        } else {
            // Outside video period - show color background
            Log.d(TAG, "Outside video period - using color background mode");
            setColorBackgroundMode(isDay);
        }
    }

    private void updateDayNightMode(boolean isDayTime) {
        Log.d(TAG, "Day/Night mode changed to: " + (isDayTime ? "DAY" : "NIGHT"));
        this.isDay = isDayTime;

        // Check if we should be in video mode or color background mode
        if (isInVideoPeriod()) {
            // In video period - ensure we're playing videos
            // start playing if we're switching from colorBackground to video OR if no video is playing
            if (colorBackgroundMode || (!layer1.isPlaying() && !layer2.isPlaying())) {
                // Switch from color mode to video mode
                colorBackgroundMode = false;
                setBackgroundColor(android.graphics.Color.TRANSPARENT);

                // Start playing videos if we have a playlist
                if (!allVideoPaths.isEmpty()) {
                    currentVideoIndex = -1;
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (layer1.isReady() && layer2.isReady()) {
                                playNextVideo();
                            } else {
                                postDelayed(this, 100);
                            }
                        }
                    }, 100);
                }
            }
        } else {
            // Outside video period - ensure we're in color background mode
            setColorBackgroundMode(isDayTime);
        }
    }

    /**
     * Set color background mode - display solid color instead of videos
     * @param isDayTime true for white background, false for black
     */
    public void setColorBackgroundMode(boolean isDayTime) {
        this.isDay = isDayTime;
        this.colorBackgroundMode = true;
        this.backgroundColor = isDayTime ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;

        // Stop any playing videos
        layer1.stop();
        layer2.stop();

        // Set layers to transparent
        layer1.setAlpha(0f);
        layer2.setAlpha(0f);

        // Set background color
        setBackgroundColor(backgroundColor);

        Log.d(TAG, "Color background mode enabled: " + (isDayTime ? "WHITE (DAY)" : "BLACK (NIGHT)"));
    }

    /**
     * Check if current date is within the video display period
     */
    private boolean isInVideoPeriod() {
        return this.calendarMode == IDisplayMode.CalendarMode.Christmas;

    }

    protected float calculateVideoBrightness(String videoPath, String filename, boolean isDayTime) {
        filename = filename.toLowerCase();

        if (filename.contains("b_")) {
            try {
                int startIdx = filename.indexOf("b_") + 2;
                int endIdx = startIdx + 2;
                if (endIdx <= filename.length()) {
                    String brightnessStr = filename.substring(startIdx, endIdx);
                    int brightness = Integer.parseInt(brightnessStr);
                    float result = brightness / 100.0f;
                    return Math.max(0.0f, Math.min(1.0f, result));
                }
            } catch (Exception e) {
                // ignore
            }
        }

        if (filename.startsWith("n_")) {
            return 1.0f;
        } else if (filename.startsWith("d_")) {
            return 1.0f;
        } else if (filename.contains("dn_")) {
            return 0.5f;
        }

        return isDayTime ? VIDEO_BRIGHTNESS_DAY : VIDEO_BRIGHTNESS_NIGHT;
    }

    private List<String> getCurrentPlaylist() {
        List<String> playlist = isDay ? dayVideos : nightVideos;

        if (playlist.isEmpty()) {
            return allVideoPaths;
        }

        return playlist;
    }

    private void playNextVideo() {
        // Don't play videos in color background mode
        if (colorBackgroundMode) {
            Log.d(TAG, "Color background mode active, not playing video");
            return;
        }

        List<String> playlist = getCurrentPlaylist();

        if (playlist == null || playlist.isEmpty()) {
            Log.w(TAG, "Playlist is empty");
            return;
        }

        currentVideoIndex = random.nextInt(playlist.size());

        final String videoPath = playlist.get(currentVideoIndex);
        currentVideoPath = videoPath;

        String filename = new File(videoPath).getName();
        currentVideoBrightness = calculateVideoBrightness(videoPath, filename, isDay);

        Log.d(TAG, "Playing video: " + filename + " (brightness: " + currentVideoBrightness + ")");

        currentLayer.loadVideo(videoPath, new VideoLayer.VideoCallback() {
            @Override
            public void onPrepared(int duration) {
                currentVideoDuration = duration;

                currentLayer.start();

                if (currentLayer.getAlpha() == 0f) {
                    fadeIn(currentLayer, currentVideoBrightness);
                }

                // NEW: Schedule preparation based on duration
                // But crossfade will be triggered by onNearingCompletion
                long delayUntilPreparation = duration - PREPARE_BEFORE_END;
                if (delayUntilPreparation > 0) {
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            prepareNextVideoAsync();
                        }
                    }, delayUntilPreparation);
                } else {
                    prepareNextVideoAsync();
                }
            }

            @Override
            public void onError() {
                Log.e(TAG, "Error playing video");
                playNextVideo();
            }

            @Override
            public void onNearingCompletion() {
                // NEW: Called FADE_DURATION before video actually ends
                // This triggers the crossfade at the right time
                Log.d(TAG + "Timing", "Video nearing completion, starting crossfade");

                if (nextVideoReady && !nextVideoStarted) {
                    // Start next video if not already started
                    startNextVideoAsync();
                }

                // Start crossfade
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        beginCrossFade();
                    }
                }, START_ADVANCE_TIME);
            }
        });
    }

    private void prepareNextVideoAsync() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                prepareNextVideoBackground();
            }
        });
    }

    private void prepareNextVideoBackground() {
        Log.d(TAG + "Timing", "Starting preparing next video");

        final List<String> playlist = getCurrentPlaylist();

        if (playlist.isEmpty()) {
            return;
        }

        int nextIndex = random.nextInt(playlist.size());
        final String videoPath = playlist.get(nextIndex);

        final File file = new File(videoPath);

        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "Cannot access video: " + videoPath);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    prepareNextVideoAsync();
                }
            });
            return;
        }

        final String filename = file.getName();
        final float brightness = calculateVideoBrightness(videoPath, filename, isDay);

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                nextVideoPath = videoPath;
                nextVideoBrightness = brightness;
                nextVideoReady = false;
                nextVideoStarted = false;

                Log.d(TAG, "Pre-loading next video: " + filename + " (brightness: " + nextVideoBrightness + ")");
            }
        });

        nextLayer.loadVideoAsync(videoPath, backgroundHandler, new VideoLayer.VideoCallback() {
            @Override
            public void onPrepared(final int duration) {
                Log.d(TAG + "Timing", "Next video loaded on background thread");

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        nextVideoReady = true;
                        nextVideoDuration = duration;

                        // Next video is ready, will be started when current video nears completion
                    }
                });
            }

            @Override
            public void onError() {
                Log.e(TAG, "Error preparing next video");
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        nextVideoReady = false;
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                prepareNextVideoAsync();
                            }
                        }, 500);
                    }
                });
            }

            @Override
            public void onNearingCompletion() {
                // Not used for next video
            }
        });
    }

    private void startNextVideoAsync() {
        if (!nextVideoReady || nextVideoStarted) {
            return;
        }

        Log.d(TAG + "Timing", "Starting next video");

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    nextLayer.seekTo(0);
                    Thread.sleep(10);
                    nextLayer.start();

                    Log.d(TAG + "Timing", "Video started");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            nextVideoStarted = true;
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error starting next video: " + e.getMessage());
                }
            }
        });
    }

    private void beginCrossFade() {
        if (!nextVideoReady) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    beginCrossFade();
                }
            }, 50);
            return;
        }

        if (!nextVideoStarted) {
            startNextVideoAsync();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    performCrossFade();
                }
            }, 100);
            return;
        }

        Log.d(TAG + "Timing", "Starting crossfade");
        performCrossFade();
    }

    private void performCrossFade() {
        if (crossFadeAnimator != null && crossFadeAnimator.isRunning()) {
            crossFadeAnimator.cancel();
        }

        final VideoLayer fadingOut = currentLayer;
        final VideoLayer fadingIn = nextLayer;

        final float startBrightness = currentVideoBrightness;
        final float endBrightness = nextVideoBrightness;

        crossFadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        crossFadeAnimator.setDuration(FADE_DURATION);
        crossFadeAnimator.setInterpolator(new LinearInterpolator());

        crossFadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = animation.getAnimatedFraction();
                fadingOut.setAlpha(startBrightness * (1f - progress));
                fadingIn.setAlpha(endBrightness * progress);
            }
        });

        crossFadeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                VideoLayer temp = currentLayer;
                currentLayer = nextLayer;
                nextLayer = temp;

                currentVideoBrightness = endBrightness;
                currentVideoPath = nextVideoPath;
                currentVideoDuration = nextVideoDuration;
                nextVideoReady = false;
                nextVideoStarted = false;

                Log.d(TAG + "Timing", "Stopped crossfade");

                // Setup completion monitoring for the new current video
                currentLayer.setupCompletionCallback(new VideoLayer.VideoCallback() {
                    @Override
                    public void onPrepared(int duration) {
                        // Not used
                    }

                    @Override
                    public void onError() {
                        // Not used
                    }

                    @Override
                    public void onNearingCompletion() {
                        Log.d(TAG + "Timing", "Video nearing completion, starting crossfade");

                        if (nextVideoReady && !nextVideoStarted) {
                            startNextVideoAsync();
                        }

                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                beginCrossFade();
                            }
                        }, START_ADVANCE_TIME);
                    }
                });

                // Schedule preparation of next video
                long delayUntilPreparation = currentVideoDuration - PREPARE_BEFORE_END;
                if (delayUntilPreparation > 0) {
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            prepareNextVideoAsync();
                        }
                    }, delayUntilPreparation);
                } else {
                    prepareNextVideoAsync();
                }

                backgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        fadingOut.stop();
                    }
                });
            }
        });

        crossFadeAnimator.start();
    }

    private void fadeIn(final VideoLayer layer, final float targetBrightness) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, targetBrightness);
        animator.setDuration(FADE_DURATION);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                layer.setAlpha((Float) animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    private void fadeOutAndPlayNext() {
        final VideoLayer fadingOut = currentLayer;
        ValueAnimator animator = ValueAnimator.ofFloat(currentVideoBrightness, 0f);
        animator.setDuration(FADE_DURATION);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                fadingOut.setAlpha((Float) animation.getAnimatedValue());
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                backgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        fadingOut.stop();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                playNextVideo();
                            }
                        });
                    }
                });
            }
        });
        animator.start();
    }

    public void pauseVideo() {
        if (scheduledCrossFade != null) {
            removeCallbacks(scheduledCrossFade);
        }
        if (scheduledStart != null) {
            removeCallbacks(scheduledStart);
        }

        if (currentLayer != null) {
            currentLayer.pause();
        }
        if (nextLayer != null && nextLayer.isPlaying()) {
            nextLayer.pause();
        }
    }

    public void resumeVideo() {
        if (currentLayer != null) {
            currentLayer.resume();
        }
        if (nextLayer != null && nextLayer.isPlaying()) {
            nextLayer.resume();
        }
    }

    public void cleanup() {
        if (crossFadeAnimator != null) {
            crossFadeAnimator.cancel();
        }
        if (scheduledCrossFade != null) {
            removeCallbacks(scheduledCrossFade);
        }
        if (scheduledStart != null) {
            removeCallbacks(scheduledStart);
        }
        layer1.cleanup();
        layer2.cleanup();

        if (backgroundThread != null) {
            backgroundThread.quit();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    protected boolean isDay;
    protected IDisplayMode.CalendarMode calendarMode;

    public void SetDisplayMode(boolean isDay, IDisplayMode.CalendarMode calendarMode) {
        this.calendarMode = calendarMode;
        updateDayNightMode(isDay);
    }


    private static class VideoLayer extends TextureView implements TextureView.SurfaceTextureListener {
        private static final String TAG = "VideoLayer";
        private MediaPlayer mediaPlayer;
        private Surface surface;
        private boolean surfaceReady = false;
        private final Object mediaPlayerLock = new Object();
        private Handler completionCheckHandler;
        private Runnable completionCheckRunnable;
        private VideoCallback currentCallback;

        interface VideoCallback {
            void onPrepared(int duration);
            void onError();
            void onNearingCompletion();  // NEW: Called FADE_DURATION before end
        }

        public VideoLayer(Context context) {
            super(context);
            setOpaque(false);
            setSurfaceTextureListener(this);
            completionCheckHandler = new Handler(Looper.getMainLooper());
        }

        public boolean isReady() {
            return surfaceReady;
        }

        public void loadVideo(String videoPath, final VideoCallback callback) {
            this.currentCallback = callback;

            try {
                synchronized (mediaPlayerLock) {
                    if (mediaPlayer != null) {
                        mediaPlayer.reset();
                    } else {
                        mediaPlayer = new MediaPlayer();
                    }

                    mediaPlayer.setDataSource(videoPath);
                    mediaPlayer.setSurface(surface);
                    mediaPlayer.setVolume(0f, 0f);

                    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            int duration = mp.getDuration();
                            callback.onPrepared(duration);

                            // NEW: Start monitoring for near-completion
                            startCompletionMonitoring(duration);
                        }
                    });

                    mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                            Log.e(TAG, "Player error: " + what + ", " + extra);
                            callback.onError();
                            return true;
                        }
                    });

                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            Log.d(TAG + "Timing", "Video ended");
                            stopCompletionMonitoring();
                        }
                    });

                    mediaPlayer.prepareAsync();
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception loading video: " + e.getMessage());
                e.printStackTrace();
                callback.onError();
            }
        }

        public void loadVideoAsync(final String videoPath, Handler backgroundHandler, final VideoCallback callback) {
            this.currentCallback = callback;

            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (mediaPlayerLock) {
                            if (mediaPlayer != null) {
                                mediaPlayer.reset();
                            } else {
                                mediaPlayer = new MediaPlayer();
                            }

                            mediaPlayer.setDataSource(videoPath);
                            mediaPlayer.setSurface(surface);
                            mediaPlayer.setVolume(0f, 0f);
                            mediaPlayer.setLooping(true);  // Loop so video doesn't end during crossfade

                            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(MediaPlayer mp) {
                                    int duration = mp.getDuration();
                                    callback.onPrepared(duration);
                                }
                            });

                            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                                @Override
                                public boolean onError(MediaPlayer mp, int what, int extra) {
                                    Log.e(TAG, "Player error: " + what + ", " + extra);
                                    callback.onError();
                                    return true;
                                }
                            });

                            mediaPlayer.prepareAsync();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Exception loading video async: " + e.getMessage());
                        e.printStackTrace();
                        callback.onError();
                    }
                }
            });
        }

        /**
         * NEW: Monitor video position and trigger callback when nearing completion
         */
        private void startCompletionMonitoring(final int duration) {
            stopCompletionMonitoring();

            completionCheckRunnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (mediaPlayerLock) {
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            try {
                                int currentPosition = mediaPlayer.getCurrentPosition();
                                int remaining = duration - currentPosition;

                                // Trigger callback when remaining time ≤ FADE_DURATION + buffer
                                // The +200ms buffer ensures video is still playing when crossfade ends
                                if (remaining <= 1200 && remaining > 0) {  // FADE_DURATION + 200ms buffer
                                    if (currentCallback != null) {
                                        currentCallback.onNearingCompletion();
                                        currentCallback = null;  // Only call once
                                    }
                                    return;  // Stop checking
                                }

                                // Check again in 100ms
                                completionCheckHandler.postDelayed(this, 100);
                            } catch (Exception e) {
                                // Video might have ended
                            }
                        }
                    }
                }
            };

            completionCheckHandler.postDelayed(completionCheckRunnable, 100);
        }

        private void stopCompletionMonitoring() {
            if (completionCheckRunnable != null) {
                completionCheckHandler.removeCallbacks(completionCheckRunnable);
                completionCheckRunnable = null;
            }
        }

        /**
         * NEW: Setup completion callback for already-playing video
         * Used when video becomes current layer after crossfade
         */
        public void setupCompletionCallback(VideoCallback callback) {
            this.currentCallback = callback;

            // Start monitoring with the known duration
            int duration = getDuration();
            if (duration > 0 && isPlaying()) {
                startCompletionMonitoring(duration);
            }
        }

        public void start() {
            synchronized (mediaPlayerLock) {
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.start();
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting: " + e.getMessage());
                    }
                }
            }
        }

        public void seekTo(int msec) {
            synchronized (mediaPlayerLock) {
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.seekTo(msec);
                    } catch (Exception e) {
                        Log.e(TAG, "Error seeking: " + e.getMessage());
                    }
                }
            }
        }

        public void stop() {
            stopCompletionMonitoring();
            synchronized (mediaPlayerLock) {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.stop();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error stopping: " + e.getMessage());
                    }
                }
            }
        }

        public void pause() {
            synchronized (mediaPlayerLock) {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error pausing: " + e.getMessage());
                    }
                }
            }
        }

        public void resume() {
            synchronized (mediaPlayerLock) {
                if (mediaPlayer != null) {
                    try {
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error resuming: " + e.getMessage());
                    }
                }
            }
        }

        public boolean isPlaying() {
            synchronized (mediaPlayerLock) {
                if (mediaPlayer != null) {
                    try {
                        return mediaPlayer.isPlaying();
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            }
        }

        public int getDuration() {
            synchronized (mediaPlayerLock) {
                if (mediaPlayer != null) {
                    try {
                        return mediaPlayer.getDuration();
                    } catch (Exception e) {
                        return 0;
                    }
                }
                return 0;
            }
        }

        public int getCurrentPosition() {
            synchronized (mediaPlayerLock) {
                if (mediaPlayer != null) {
                    try {
                        return mediaPlayer.getCurrentPosition();
                    } catch (Exception e) {
                        return 0;
                    }
                }
                return 0;
            }
        }

        public void cleanup() {
            stopCompletionMonitoring();
            synchronized (mediaPlayerLock) {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.stop();
                        }
                        mediaPlayer.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error cleaning up: " + e.getMessage());
                    }
                    mediaPlayer = null;
                }
            }
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            surface = new Surface(surfaceTexture);
            surfaceReady = true;
            Log.d(TAG, "Surface available: " + width + "x" + height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            surfaceReady = false;
            cleanup();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    }
}