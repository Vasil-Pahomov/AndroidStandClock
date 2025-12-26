package com.lvr.standclock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class SpiderView extends View {

    private Paint paint = new Paint();

    // Spider animation variables
    private int SPIDER_SIZE = 512;
    private Bitmap[] spiderFrames = new Bitmap[12];
    private float spiderX, spiderY;
    private float spiderVX, spiderVY;
    private float spiderAngle;
    private boolean spiderEnabled = false;
    private boolean spiderVisible = false;
    private long nextSpiderTime = 0;
    private long lastFrameTime = 0;
    private int currentFrame = 0;
    private final Random random = new Random();
    private Matrix spiderMatrix = new Matrix();

    public SpiderView(Context context) {
        super(context);
        init(context);
    }

    public SpiderView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SpiderView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Make this view transparent
        setBackgroundColor(Color.TRANSPARENT);

        // Load spider animation frames
        loadSpiderFrames(context);

        // Schedule first spider appearance (20-120 seconds from now)
        nextSpiderTime = System.currentTimeMillis() + 20000 + random.nextInt(100000);
    }

    private void loadSpiderFrames(Context context) {
        spiderFrames[0] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider0);
        spiderFrames[1] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider2);
        spiderFrames[2] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider3);
        spiderFrames[3] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider2);
        spiderFrames[4] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider1);
        spiderFrames[5] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider4_0m);
        spiderFrames[6] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider5_1m);
        spiderFrames[7] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider6_2m);
        spiderFrames[8] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider7_3m);
        spiderFrames[9] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider6_2m);
        spiderFrames[10] = BitmapFactory.decodeResource(context.getResources(), R.drawable.spider5_1m);
    }

    public void triggerSpider() {
        if (!spiderVisible) {
            if (spiderEnabled) {
                spiderEnabled = false;
                return;
            }
            spiderEnabled = true;
            startSpiderAnimation();
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Clear canvas with transparency
        canvas.drawColor(Color.TRANSPARENT);

        // Anti aliasing
        paint.setFlags(Paint.ANTI_ALIAS_FLAG);

        long now = System.currentTimeMillis();

        // Spider animation logic
        if (spiderEnabled && !spiderVisible && now > nextSpiderTime) {
            startSpiderAnimation();
            invalidate();
        }

        if (spiderVisible) {
            // Update position
            spiderX += spiderVX;
            spiderY += spiderVY;

            currentFrame = (currentFrame + 1) % 11;
            lastFrameTime = now;

            // Draw the spider with rotation
            if (spiderFrames[currentFrame] != null) {
                drawRotatedSpider(canvas, spiderFrames[currentFrame], spiderX, spiderY, spiderVX, spiderVY);
            }

            // Check if spider is off screen
            if (spiderX < -2 * SPIDER_SIZE - spiderVX || spiderX > getWidth() + SPIDER_SIZE + spiderVX ||
                    spiderY < -2 * SPIDER_SIZE - spiderVY || spiderY > getHeight() + SPIDER_SIZE + spiderVY) {
                spiderVisible = false;
                // Schedule next appearance (1-3 mins)
                nextSpiderTime = now + 60000 + random.nextInt(120000);
            }
        }

        if (spiderVisible) {
            // Redraw for animation
            postInvalidateDelayed(50);
        } else {
            postInvalidateDelayed(1000);
        }
    }

    private void drawRotatedSpider(Canvas canvas, Bitmap bitmap, float x, float y, float vx, float vy) {
        // Set up transformation matrix
        spiderMatrix.reset();
        spiderMatrix.postRotate(spiderAngle, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        spiderMatrix.postTranslate(x, y);

        // Draw with rotation
        canvas.drawBitmap(bitmap, spiderMatrix, paint);
    }

    private void startSpiderAnimation() {
        spiderVisible = true;
        currentFrame = 0;
        lastFrameTime = System.currentTimeMillis();

        int width = getWidth();
        int height = getHeight();

        // Random starting edge
        int edge = random.nextInt(4);

        switch (edge) {
            case 0: // left edge
                spiderX = -SPIDER_SIZE;
                spiderY = -SPIDER_SIZE + random.nextInt(height + SPIDER_SIZE);
                break;

            case 1: // right edge
                spiderX = width;
                spiderY = -SPIDER_SIZE + random.nextInt(height + SPIDER_SIZE);
                break;

            case 2: // top edge
                spiderX = -SPIDER_SIZE + random.nextInt(width + SPIDER_SIZE);
                spiderY = -SPIDER_SIZE;
                break;

            case 3: // bottom edge
                spiderX = -SPIDER_SIZE + random.nextInt(width + SPIDER_SIZE);
                spiderY = height;
                break;
        }

        // Dot within the screen for spider to crawl through
        int xcen = random.nextInt(width - SPIDER_SIZE);
        int ycen = random.nextInt(height - SPIDER_SIZE);

        // Length of vector from starting to target point for speed calculation
        int vlen = (int) Math.round(Math.sqrt((spiderX - xcen) * (spiderX - xcen) + (spiderY - ycen) * (spiderY - ycen)));

        spiderVX = 30 * (xcen - spiderX) / vlen;
        spiderVY = 30 * (ycen - spiderY) / vlen;

        spiderAngle = (float) Math.toDegrees(Math.atan2(spiderVY, spiderVX)) + 90f;
    }
}