package com.lvr.standclock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.animation.LinearInterpolator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class VideoBackgroundView extends TextureView implements TextureView.SurfaceTextureListener {

    private static final String TAG = "VideoBackgroundView";
    private static final int FADE_DURATION = 1000; // 1 second fade

    private MediaPlayer mediaPlayer;
    private Surface surface;
    private List<String> videoPlaylist = new ArrayList<>();
    private int currentVideoIndex = -1;
    private Random random = new Random();
    private boolean isFading = false;
    private ValueAnimator fadeAnimator;

    public VideoBackgroundView(Context context) {
        super(context);
        setOpaque(false);
        setSurfaceTextureListener(this);
        Log.d(TAG, "VideoBackgroundView created");
    }

    public void setVideoPlaylist(List<String> videoPaths) {
        this.videoPlaylist = new ArrayList<>(videoPaths);
        // Shuffle the playlist for random order
        Collections.shuffle(this.videoPlaylist, random);
        Log.d(TAG, "Playlist set with " + videoPlaylist.size() + " videos");
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        Log.d(TAG, "Surface available - width: " + width + ", height: " + height);
        surface = new Surface(surfaceTexture);

        if (videoPlaylist != null && !videoPlaylist.isEmpty()) {
            playNextVideo();
        } else {
            Log.w(TAG, "No videos in playlist");
        }
    }

    private void playNextVideo() {
        if (videoPlaylist == null || videoPlaylist.isEmpty()) {
            Log.w(TAG, "Playlist is empty");
            return;
        }

        // Move to next video in shuffled playlist
        currentVideoIndex = (currentVideoIndex + 1) % videoPlaylist.size();

        // If we've played all videos, reshuffle for next round
        if (currentVideoIndex == 0 && videoPlaylist.size() > 1) {
            Log.d(TAG, "Reshuffling playlist");
            Collections.shuffle(videoPlaylist, random);
        }

        String videoPath = videoPlaylist.get(currentVideoIndex);
        Log.d(TAG, "Playing video " + (currentVideoIndex + 1) + "/" + videoPlaylist.size() + ": " + videoPath);

        loadAndPlayVideo(videoPath);
    }

    private void loadAndPlayVideo(String videoPath) {
        File file = new File(videoPath);
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "Cannot access video file: " + videoPath);
            playNextVideo(); // Try next video
            return;
        }

        try {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.reset();
                } catch (Exception e) {
                    Log.e(TAG, "Error resetting player: " + e.getMessage());
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            }

            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
            }

            // Start with alpha 0 for fade in
            setAlpha(0f);
            isFading = true;

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
                    Log.d(TAG, "Video prepared - Duration: " + mp.getDuration() + "ms, " +
                            mp.getVideoWidth() + "x" + mp.getVideoHeight());

                    int videoDuration = mp.getDuration();
                    long delayUntilFadeOut = videoDuration - FADE_DURATION;

                    mp.start();
                    fadeIn();

                    if (delayUntilFadeOut > 0) {
                        // Post a runnable to start the fade-out FADE_DURATION before the end
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Check if the current video is still playing before fading out
                                if (mediaPlayer == mp) {
                                    // We don't want onCompletion to trigger playNextVideo directly anymore
                                    mp.setOnCompletionListener(null);
                                    fadeOutAndPlayNext();
                                }
                            }
                        }, delayUntilFadeOut);
                    } else {
                        // Video is too short for a full fade; just let it complete normally
                        Log.w(TAG, "Video too short for a " + FADE_DURATION + "ms fade-out, playing next on completion.");
                        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                Log.d(TAG, "Short video completed, transitioning to next");
                                playNextVideo(); // Directly play next without fade
                            }
                        });
                    }
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer error - what: " + what + ", extra: " + extra);
                    logMediaError(what, extra);
                    // Try next video on error
                    post(new Runnable() {
                        @Override
                        public void run() {
                            playNextVideo();
                        }
                    });
                    return true;
                }
            });

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            e.printStackTrace();
            playNextVideo();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception: " + e.getMessage());
            e.printStackTrace();
            playNextVideo();
        }
    }

    private void fadeIn() {
        if (isFading && fadeAnimator != null) {
            fadeAnimator.cancel();
        }

        isFading = true;
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        fadeAnimator.setDuration(FADE_DURATION);
        fadeAnimator.setInterpolator(new LinearInterpolator());
        fadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float alpha = (Float) animation.getAnimatedValue();
                setAlpha(alpha);
            }
        });
        fadeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isFading = false;
            }
        });
        fadeAnimator.start();
        Log.d(TAG, "Fading in");
    }

    private void fadeOutAndPlayNext() {
        if (isFading && fadeAnimator != null) {
            fadeAnimator.cancel();
        }

        isFading = true;
        fadeAnimator = ValueAnimator.ofFloat(1f, 0f);
        fadeAnimator.setDuration(FADE_DURATION);
        fadeAnimator.setInterpolator(new LinearInterpolator());
        fadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float alpha = (Float) animation.getAnimatedValue();
                setAlpha(alpha);
            }
        });
        fadeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isFading = false;
                playNextVideo();
            }
        });
        fadeAnimator.start();
        Log.d(TAG, "Fading out");
    }

    private void logMediaError(int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.e(TAG, "Unknown media error");
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.e(TAG, "Media server died");
                break;
        }
        switch (extra) {
            case MediaPlayer.MEDIA_ERROR_IO:
                Log.e(TAG, "IO error");
                break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                Log.e(TAG, "Malformed media");
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                Log.e(TAG, "Unsupported format");
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                Log.e(TAG, "Timeout");
                break;
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "Surface size changed: " + width + "x" + height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "Surface texture destroyed");
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
        }
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing media player: " + e.getMessage());
            }
            mediaPlayer = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Called frequently, don't log
    }

    public void pauseVideo() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    Log.d(TAG, "Video paused");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error pausing video: " + e.getMessage());
            }
        }
    }

    public void resumeVideo() {
        if (mediaPlayer != null) {
            try {
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                    Log.d(TAG, "Video resumed");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error resuming video: " + e.getMessage());
            }
        }
    }

    public void cleanup() {
    }

}