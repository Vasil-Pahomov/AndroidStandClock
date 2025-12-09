package com.lvr.standclock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class VideoBackgroundView extends TextureView implements TextureView.SurfaceTextureListener {

    private static final String TAG = "VideoBackgroundView";
    private MediaPlayer mediaPlayer;
    private String videoPath;
    private Surface surface;

    public VideoBackgroundView(Context context) {
        super(context);
        setOpaque(false); // Allow transparency if needed
        setSurfaceTextureListener(this);
        setBackgroundColor(0xFFFF0000);
        Log.d(TAG, "VideoBackgroundView created");
    }

    public void setVideoPath(String path) {
        this.videoPath = path;
        Log.d(TAG, "Video path set to: " + path);

        // Validate file
        File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG, "Video file does not exist: " + path);
            return;
        }
        if (!file.canRead()) {
            Log.e(TAG, "Cannot read video file (permission issue?): " + path);
            return;
        }
        Log.d(TAG, "Video file validated. Size: " + file.length() + " bytes");
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        surface = new Surface(surfaceTexture);
        Log.d(TAG, "Surface available - width: " + width + ", height: " + height);
        if (videoPath != null) {
            File file = new File(videoPath);
            if (file.exists() && file.canRead()) {
                try {
                    Log.d(TAG, "Attempting to load video: " + videoPath);

                    mediaPlayer = new MediaPlayer();

                    // Try using FileInputStream for better compatibility with external files
                    FileInputStream fis = new FileInputStream(file);
                    try {
                        mediaPlayer.setDataSource(fis.getFD());
                    } finally {
                        fis.close();
                    }

                    mediaPlayer.setSurface(surface);
                    mediaPlayer.setLooping(true);
                    mediaPlayer.setVolume(0f, 0f);

                    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            Log.d(TAG, "Video prepared successfully");
                            Log.d(TAG, "Video duration: " + mp.getDuration() + "ms");
                            Log.d(TAG, "Video width: " + mp.getVideoWidth() + ", height: " + mp.getVideoHeight());
                            mp.start();
                            Log.d(TAG, "Video playback started");
                        }
                    });

                    mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                            Log.e(TAG, "MediaPlayer error - what: " + what + ", extra: " + extra);
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
                                    Log.e(TAG, "IO error - file may be corrupted or unsupported format");
                                    break;
                                case MediaPlayer.MEDIA_ERROR_MALFORMED:
                                    Log.e(TAG, "Malformed media - unsupported format");
                                    break;
                                case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                                    Log.e(TAG, "Unsupported media format");
                                    break;
                                case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                                    Log.e(TAG, "Media timeout");
                                    break;
                            }
                            return false;
                        }
                    });

                    mediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                        @Override
                        public boolean onInfo(MediaPlayer mp, int what, int extra) {
                            Log.d(TAG, "MediaPlayer info - what: " + what + ", extra: " + extra);
                            return false;
                        }
                    });

                    Log.d(TAG, "Starting async prepare...");
                    mediaPlayer.prepareAsync();

                } catch (IOException e) {
                    Log.e(TAG, "IOException setting up video: " + e.getMessage());
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "IllegalArgumentException: " + e.getMessage());
                    e.printStackTrace();
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException: " + e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected exception: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                Log.e(TAG, "Video file not found or not readable: " + videoPath);
            }
        } else {
            Log.w(TAG, "No video path set");
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "Surface size changed: " + width + "x" + height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "Surface texture destroyed");
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
        // Called frequently, don't log here
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

    public void changeVideo(String newVideoPath) {
        Log.d(TAG, "Changing video to: " + newVideoPath);
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.reset();

                File file = new File(newVideoPath);
                FileInputStream fis = new FileInputStream(file);
                try {
                    mediaPlayer.setDataSource(fis.getFD());
                } finally {
                    fis.close();
                }

                mediaPlayer.prepareAsync();
                this.videoPath = newVideoPath;
            } catch (IOException e) {
                Log.e(TAG, "Error changing video: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}