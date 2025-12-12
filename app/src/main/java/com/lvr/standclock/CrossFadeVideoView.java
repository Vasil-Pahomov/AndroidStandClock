package com.lvr.standclock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CrossFadeVideoView extends FrameLayout {

    private static final String TAG = "CrossFadeVideoView";
    private static final int FADE_DURATION = 1000; // 1 second cross-fade
    private static final float VIDEO_BRIGHTNESS_DAY = 1.0f;
    private static final float VIDEO_BRIGHTNESS_NIGHT = 0.5f;

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
    private float currentBrightness = VIDEO_BRIGHTNESS_DAY;

    public CrossFadeVideoView(Context context) {
        super(context);

        // Create two video layers
        layer1 = new VideoLayer(context);
        layer2 = new VideoLayer(context);

        // Add both layers to the FrameLayout
        addView(layer1, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        addView(layer2, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Start with layer1 as current, layer2 invisible
        currentLayer = layer1;
        nextLayer = layer2;
        currentLayer.setAlpha(0f);
        nextLayer.setAlpha(0f);

        Log.d(TAG, "CrossFadeVideoView created");
    }

    public void setVideoPlaylist(List<String> videoPaths) {
        this.allVideoPaths = new ArrayList<>(videoPaths);

        // Separate videos into day and night lists
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

        Log.d(TAG, "Playlist set - Day videos: " + dayVideos.size() + ", Night videos: " + nightVideos.size());

        // Wait for surfaces to be ready
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
            this.currentBrightness = isDayTime ? VIDEO_BRIGHTNESS_DAY : VIDEO_BRIGHTNESS_NIGHT;

            // Reset video index to force new selection from appropriate list
            currentVideoIndex = -1;
        }
    }

    private List<String> getCurrentPlaylist() {
        List<String> playlist = isDay ? dayVideos : nightVideos;

        if (playlist.isEmpty()) {
            Log.w(TAG, "No videos for current time period, using all videos");
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

        // Pick a random video from the current playlist
        currentVideoIndex = random.nextInt(playlist.size());

        String videoPath = playlist.get(currentVideoIndex);
        Log.d(TAG, "Playing " + (isDay ? "DAY" : "NIGHT") + " video (" +
                (currentVideoIndex + 1) + "/" + playlist.size() + "): " + new File(videoPath).getName());

        currentLayer.loadVideo(videoPath, new VideoLayer.VideoCallback() {
            @Override
            public void onPrepared(int duration) {
                Log.d(TAG, "Video prepared, duration: " + duration + "ms");
                currentLayer.start();

                // Fade in first video or keep brightness for subsequent
                if (currentLayer.getAlpha() == 0f) {
                    fadeIn(currentLayer);
                }

                // Schedule cross-fade before video ends
                long delayUntilCrossFade = duration - FADE_DURATION;
                if (delayUntilCrossFade > 0) {
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            prepareAndCrossFade();
                        }
                    }, delayUntilCrossFade);
                } else {
                    Log.w(TAG, "Video too short for cross-fade");
                }
            }

            @Override
            public void onError() {
                Log.e(TAG, "Error playing video, trying next");
                playNextVideo();
            }
        });
    }

    private void prepareAndCrossFade() {
        List<String> playlist = getCurrentPlaylist();

        if (playlist.isEmpty()) {
            Log.w(TAG, "No videos available for cross-fade");
            fadeOutAndPlayNext();
            return;
        }

        // Pick a random video from current playlist
        int nextIndex = random.nextInt(playlist.size());
        String nextVideoPath = playlist.get(nextIndex);

        Log.d(TAG, "Preparing next " + (isDay ? "DAY" : "NIGHT") + " video for cross-fade: " +
                new File(nextVideoPath).getName());

        nextLayer.loadVideo(nextVideoPath, new VideoLayer.VideoCallback() {
            @Override
            public void onPrepared(int duration) {
                Log.d(TAG, "Next video prepared, starting cross-fade");
                nextLayer.start();
                performCrossFade();
            }

            @Override
            public void onError() {
                Log.e(TAG, "Error preparing next video, using simple fade");
                fadeOutAndPlayNext();
            }
        });
    }

    private void performCrossFade() {
        if (crossFadeAnimator != null && crossFadeAnimator.isRunning()) {
            crossFadeAnimator.cancel();
        }

        final VideoLayer fadingOut = currentLayer;
        final VideoLayer fadingIn = nextLayer;

        crossFadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        crossFadeAnimator.setDuration(FADE_DURATION);
        crossFadeAnimator.setInterpolator(new LinearInterpolator());

        crossFadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = animation.getAnimatedFraction();
                fadingOut.setAlpha(currentBrightness * (1f - progress));
                fadingIn.setAlpha(currentBrightness * progress);
            }
        });

        crossFadeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // Stop and release the old video
                fadingOut.stop();

                // Swap layers
                VideoLayer temp = currentLayer;
                currentLayer = nextLayer;
                nextLayer = temp;

                Log.d(TAG, "Cross-fade complete, layers swapped");

                // Schedule next transition
                scheduleNextTransition();
            }
        });

        crossFadeAnimator.start();
        Log.d(TAG, "Cross-fade started");
    }

    private void scheduleNextTransition() {
        try {
            int duration = currentLayer.getDuration();
            int currentPos = currentLayer.getCurrentPosition();
            int remaining = duration - currentPos;
            long delay = remaining - FADE_DURATION;

            if (delay > 0) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        prepareAndCrossFade();
                    }
                }, delay);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling next transition: " + e.getMessage());
        }
    }

    private void fadeIn(final VideoLayer layer) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, currentBrightness);
        animator.setDuration(FADE_DURATION);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                layer.setAlpha((Float) animation.getAnimatedValue());
            }
        });
        animator.start();
        Log.d(TAG, "Fading in first video to brightness: " + currentBrightness);
    }

    private void fadeOutAndPlayNext() {
        final VideoLayer fadingOut = currentLayer;
        ValueAnimator animator = ValueAnimator.ofFloat(currentBrightness, 0f);
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
                fadingOut.stop();
                playNextVideo();
            }
        });
        animator.start();
    }

    public void pauseVideo() {
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
        layer1.cleanup();
        layer2.cleanup();
    }

    // Inner class for individual video layer
    private static class VideoLayer extends TextureView implements TextureView.SurfaceTextureListener {
        private static final String TAG = "VideoLayer";
        private MediaPlayer mediaPlayer;
        private Surface surface;
        private boolean surfaceReady = false;

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

        public void loadVideo(String videoPath, final VideoCallback callback) {
            File file = new File(videoPath);
            if (!file.exists() || !file.canRead()) {
                Log.e(TAG, "Cannot access video: " + videoPath);
                callback.onError();
                return;
            }

            try {
                if (mediaPlayer != null) {
                    mediaPlayer.reset();
                } else {
                    mediaPlayer = new MediaPlayer();
                }

                FileInputStream fis = new FileInputStream(file);
                try {
                    mediaPlayer.setDataSource(fis.getFD());
                } finally {
                    fis.close();
                }

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

            } catch (Exception e) {
                Log.e(TAG, "Exception loading video: " + e.getMessage());
                e.printStackTrace();
                callback.onError();
            }
        }

        public void start() {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.start();
                } catch (Exception e) {
                    Log.e(TAG, "Error starting: " + e.getMessage());
                }
            }
        }

        public void stop() {
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

        public void pause() {
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

        public void resume() {
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

        public boolean isPlaying() {
            if (mediaPlayer != null) {
                try {
                    return mediaPlayer.isPlaying();
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        }

        public int getDuration() {
            if (mediaPlayer != null) {
                try {
                    return mediaPlayer.getDuration();
                } catch (Exception e) {
                    return 0;
                }
            }
            return 0;
        }

        public int getCurrentPosition() {
            if (mediaPlayer != null) {
                try {
                    return mediaPlayer.getCurrentPosition();
                } catch (Exception e) {
                    return 0;
                }
            }
            return 0;
        }

        public void cleanup() {
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