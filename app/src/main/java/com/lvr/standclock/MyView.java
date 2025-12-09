package com.lvr.standclock;

import static androidx.core.content.ContextCompat.getSystemService;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.os.BatteryManager;
import android.os.Handler;
import android.text.TextPaint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

public class MyView extends View {

    private Typeface custom_font;
    private TextPaint textPaintTime = new TextPaint();
    private TextPaint textPaintInfo = new TextPaint();
    private Paint paint = new Paint();

    private Rect bounds = new Rect(), container = new Rect();

    private int batteryLevel;
    public void setBatteryLevel(int level) { this.batteryLevel = level;}

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
    private SunriseSunsetCalculation sunCalc  = new SunriseSunsetCalculation(19.457216, 51.759445);

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


    public MyView(Context context) {
        super(context);

        //load the digital font
        custom_font = Typeface.createFromAsset(context.getApplicationContext().getAssets(), "fonts/segments.ttf");

        // Load spider animation frames
        loadSpiderFrames(context);

        // Schedule first spider appearance (20-120 seconds from now)
        nextSpiderTime = System.currentTimeMillis() + 20000 + random.nextInt(100000);

/*
        //timer
        final Handler h = new Handler();
        if (true) {//everyminute update
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    invalidate();
                    h.postDelayed(this, 60000); // everyminute update
                }
            }, 60000 - (System.currentTimeMillis() % 60000)); //everyminute update
        } else { //everysecond update
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    invalidate();
                    h.postDelayed(this, 1000);  // everysecond update
                }
            }, 1000); // everysecond update
        }
        */
    }

    private void loadSpiderFrames(Context context) {
        // Load spider PNG files
        // Adjust the resource IDs to match your actual drawable names
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //anti aliasing
        paint.setFlags(Paint.ANTI_ALIAS_FLAG);

        //get date & time
        int[] date_time = getDateTime();
        String day_of_week = getDayOfWeek(date_time[3]);
        Log.d("TIME",Calendar.getInstance().getTime().toString());

        SunriseSunsetCalculation.DayResult sun = sunCalc.calculateOfficialForDate(date_time[2],date_time[1],date_time[0]);
        boolean isDay = Calendar.getInstance().getTime().after(sun.getOfficialSunrise()) && Calendar.getInstance().getTime().before(sun.getOfficialSunset());

        //draw background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isDay ? Color.WHITE : Color.BLACK);
        int frontColor = isDay ? Color.BLACK : Color.WHITE;
        canvas.drawPaint(paint);

        //get screen size & set widths, heights, others
        int x = getWidth();
        int y = getHeight();
        int center_y = y / 2;

        //show time
        textPaintTime.setTypeface(custom_font);
        textPaintTime.setColor(frontColor);
        String[] time_str = addLeadingZeros(date_time[4], date_time[5], date_time[6]);
        String middle = String.valueOf(time_str[0] + ":" + time_str[1]);
        container.set(0,0,x,y);
        float fontSize = calculateFontSize(textPaintTime, bounds, container, middle);
        textPaintTime.setTextSize(fontSize*0.98F);

        int x_pos = (x - bounds.width()) / 2;
        int y_pos = center_y + bounds.height() / 2;
        canvas.drawText(middle, x_pos, y_pos, textPaintTime);

        if (batteryLevel > 0) {
            textPaintInfo.setTextSize(y / 16F);
            textPaintInfo.setColor(frontColor);
            canvas.drawText(String.format("%d%%",batteryLevel), 0, y - (y / 16F), textPaintInfo);
        }

        long now = System.currentTimeMillis();

        // Spider animation logic
        if (spiderEnabled && !spiderVisible && now > nextSpiderTime)
        {
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
            if (spiderX < -2*SPIDER_SIZE-spiderVX || spiderX > getWidth()+SPIDER_SIZE+spiderVX  ||
                    spiderY < -2*SPIDER_SIZE-spiderVY || spiderY > getHeight()+SPIDER_SIZE+spiderVY ) {
                spiderVisible = false;
                // Schedule next appearance (1-3 mins)
                nextSpiderTime = now + 60000 + random.nextInt(120000);
            }
        }

        if (spiderVisible) {
            // Redraw for animation
            postInvalidateDelayed(50);
        } else
        {
            postInvalidateDelayed(1000);
            //postInvalidateDelayed(60100-100*date_time[5]);
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
                spiderY = -SPIDER_SIZE+random.nextInt(height+SPIDER_SIZE);
                break;

            case 1: // right edge
                spiderX = width;
                spiderY = -SPIDER_SIZE+random.nextInt(height+SPIDER_SIZE);
                break;

            case 2: // top edge
                spiderX = -SPIDER_SIZE+random.nextInt(width+SPIDER_SIZE);
                spiderY = -SPIDER_SIZE;
                break;

            case 3: // bottom edge
                spiderX = -SPIDER_SIZE+random.nextInt(width+SPIDER_SIZE);
                spiderY = height;
                break;
        }
        //dot within the screen for spider to crawl through
        int xcen = random.nextInt(width-SPIDER_SIZE);
        int ycen = random.nextInt(height-SPIDER_SIZE);

        //length of vector from starting to targed point for speed calculation
        int vlen = (int)Math.round(Math.sqrt( (spiderX-xcen)*(spiderX-xcen) + (spiderY-ycen)*(spiderY-ycen) ));

        spiderVX = 30 * (xcen - spiderX) / vlen;
        spiderVY = 30 * (ycen - spiderY) / vlen;

        spiderAngle = (float) Math.toDegrees(Math.atan2(spiderVY, spiderVX))+90f;
    }

    private float calculateFontSize(Paint textPaint, Rect textBounds, Rect textContainer, String text) {
        int stage = 1;
        float textSize = 0;

        while(stage < 3) {
            if (stage == 1) textSize += 10;
            else
            if (stage == 2) textSize -= 1;

            textPaint.setTextSize(textSize);
            textPaint.getTextBounds(text, 0, text.length(), textBounds);
            float width = textPaint.measureText(text);
            textBounds.right = (int) (textBounds.left + width);

            textBounds.offsetTo(textContainer.left, textContainer.top);

            boolean fits = textContainer.contains(textBounds);
            if (stage == 1 && !fits) stage++;
            else
            if (stage == 2 &&  fits) stage++;
        }

        return textSize;
    }

    private String[] addLeadingZeros(int hours, int minutes, int seconds){
        String hours_str, minutes_str, seconds_str;

        if(hours <= 9)
            hours_str = " " + hours;
        else
            hours_str = String.valueOf(hours);

        if(minutes <= 9)
            minutes_str = "0" + minutes;
        else
            minutes_str = String.valueOf(minutes);

        if(seconds <= 9)
            seconds_str = "0" + seconds;
        else
            seconds_str = String.valueOf(seconds);

        String[] values = {hours_str, minutes_str, seconds_str};
        return values;
    }

    private Rect measureString(String text, TextPaint tp){
        Rect bounds = new Rect();
        tp.getTextBounds(text, 0, text.length(), bounds);
        return bounds;
    }

    private int[] getDateTime(){
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int day_of_week = calendar.get(Calendar.DAY_OF_WEEK);
        int ss = calendar.get(Calendar.SECOND);
        int mm = calendar.get(Calendar.MINUTE);
        int hh = calendar.get(Calendar.HOUR_OF_DAY);

        int[] values = {year,month,day,day_of_week, hh, mm, ss };
        return values;
    }

    private String getDayOfWeek(int day_of_week){
        switch(day_of_week){
            case Calendar.MONDAY:
                return "MON";
            case Calendar.TUESDAY:
                return "TUE";
            case Calendar.WEDNESDAY:
                return "WED";
            case Calendar.THURSDAY:
                return "THU";
            case Calendar.FRIDAY:
                return "FRI";
            case Calendar.SATURDAY:
                return "SAT";
            case Calendar.SUNDAY:
                return "SUN";
        }
        return null;
    }
}