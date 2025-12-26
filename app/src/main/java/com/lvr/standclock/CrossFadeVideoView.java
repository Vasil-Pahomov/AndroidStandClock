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

public class CrossFadeVideoView extends FrameLayout {

    private static final String TAG = "CrossFadeVideoView";
    private static final int FADE_DURATION = 1000;
    private static final float VIDEO_BRIGHTNESS_DAY = 1.0f;
    private static final float VIDEO_BRIGHTNESS_NIGHT = 0.8f;
    private static final long PREPARE_ADVANCE_TIME = 2000;
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
    private boolean isDay = true;

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
    private long currentVideoStartTime = 0;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Handler mainHandler;

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

    public void updateDayNightMode(boolean isDayTime) {
        if (this.isDay != isDayTime) {
            Log.d(TAG, "Day/Night mode changed to: " + (isDayTime ? "DAY" : "NIGHT"));
            this.isDay = isDayTime;
            currentVideoIndex = -1;
        }
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
                currentVideoStartTime = System.currentTimeMillis();

                currentLayer.start();

                if (currentLayer.getAlpha() == 0f) {
                    fadeIn(currentLayer, currentVideoBrightness);
                }

                long delayUntilPreparation = duration - FADE_DURATION - PREPARE_ADVANCE_TIME;
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
        });
    }

    /**
     * NEW: Completely async preparation - everything on background thread
     */
    private void prepareNextVideoAsync() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                prepareNextVideoBackground();
            }
        });
    }

    /**
     * NEW: All video preparation happens on background thread
     */
    private void prepareNextVideoBackground() {
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

        // Update state on main thread
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

        // NEW: Load video entirely on background thread
        nextLayer.loadVideoAsync(videoPath, backgroundHandler, new VideoLayer.VideoCallback() {
            @Override
            public void onPrepared(final int duration) {
                // Callback comes from background thread
                Log.d(TAG, "Next video loaded on background thread");

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        nextVideoReady = true;
                        nextVideoDuration = duration;

                        long elapsed = System.currentTimeMillis() - currentVideoStartTime;
                        long remaining = currentVideoDuration - elapsed;

                        long timeUntilCrossFade = remaining - FADE_DURATION;
                        long timeUntilStart = timeUntilCrossFade - START_ADVANCE_TIME;

                        if (timeUntilStart > 0) {
                            scheduledStart = new Runnable() {
                                @Override
                                public void run() {
                                    startNextVideoAsync();
                                }
                            };
                            postDelayed(scheduledStart, timeUntilStart);
                        } else {
                            startNextVideoAsync();
                        }

                        if (timeUntilCrossFade > 0) {
                            scheduledCrossFade = new Runnable() {
                                @Override
                                public void run() {
                                    beginCrossFade();
                                }
                            };
                            postDelayed(scheduledCrossFade, timeUntilCrossFade);
                        } else {
                            if (!nextVideoStarted) {
                                startNextVideoAsync();
                            }
                            postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    beginCrossFade();
                                }
                            }, 50);
                        }
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
        });
    }

    private void startNextVideoAsync() {
        if (!nextVideoReady || nextVideoStarted) {
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    nextLayer.seekTo(0);
                    Thread.sleep(10);
                    nextLayer.start();

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
                currentVideoStartTime = System.currentTimeMillis();
                nextVideoReady = false;
                nextVideoStarted = false;

                backgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        fadingOut.stop();

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                long delayUntilPreparation = currentVideoDuration - FADE_DURATION - PREPARE_ADVANCE_TIME;

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
                        });
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

            if (nextVideoReady) {
                long elapsed = System.currentTimeMillis() - currentVideoStartTime;
                long remaining = currentVideoDuration - elapsed;

                long timeUntilCrossFade = remaining - FADE_DURATION;
                long timeUntilStart = timeUntilCrossFade - START_ADVANCE_TIME;

                if (!nextVideoStarted && timeUntilStart > 0) {
                    scheduledStart = new Runnable() {
                        @Override
                        public void run() {
                            startNextVideoAsync();
                        }
                    };
                    postDelayed(scheduledStart, timeUntilStart);
                } else if (!nextVideoStarted) {
                    startNextVideoAsync();
                }

                if (timeUntilCrossFade > 0) {
                    scheduledCrossFade = new Runnable() {
                        @Override
                        public void run() {
                            beginCrossFade();
                        }
                    };
                    postDelayed(scheduledCrossFade, timeUntilCrossFade);
                }
            }
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
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * VideoLayer with background loading support
     */
    private static class VideoLayer extends TextureView implements TextureView.SurfaceTextureListener {
        private static final String TAG = "VideoLayer";
        private MediaPlayer mediaPlayer;
        private Surface surface;
        private boolean surfaceReady = false;
        private final Object mediaPlayerLock = new Object();

        interface VideoCallback {
            void onPrepared(int duration);
            void onError();
        }

        public VideoLayer(Context context) {
            super(context);
            setOpaque(false);
            setSurfaceTextureListener(this);
        }

        public boolean isReady() {
            return surfaceReady;
        }

        /**
         * Standard loadVideo for first video (on main thread)
         */
        public void loadVideo(String videoPath, final VideoCallback callback) {
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
                            callback.onPrepared(mp.getDuration());
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
                Log.e(TAG, "Exception loading video: " + e.getMessage());
                e.printStackTrace();
                callback.onError();
            }
        }

        /**
         * NEW: Async loadVideo - all blocking operations on background thread
         */
        public void loadVideoAsync(final String videoPath, Handler backgroundHandler, final VideoCallback callback) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "Loading video on background thread: " + videoPath);

                        synchronized (mediaPlayerLock) {
                            // All these blocking operations now on background thread!
                            if (mediaPlayer != null) {
                                mediaPlayer.reset();  // 55ms - now on background!
                            } else {
                                mediaPlayer = new MediaPlayer();
                            }

                            mediaPlayer.setDataSource(videoPath);  // 179ms - now on background!
                            mediaPlayer.setSurface(surface);       // 3ms - now on background!
                            mediaPlayer.setVolume(0f, 0f);

                            // Prepare listener - will be called on background thread
                            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(MediaPlayer mp) {
                                    // This callback happens on background thread
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

                            mediaPlayer.prepareAsync();  // Still async, but initiated from background
                        }

                        Log.d(TAG, "Video loading initiated on background thread");

                    } catch (Exception e) {
                        Log.e(TAG, "Exception loading video async: " + e.getMessage());
                        e.printStackTrace();
                        callback.onError();
                    }
                }
            });
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