package com.lvr.standclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

public class ClockView extends View {

    private Typeface custom_font;
    private TextPaint textPaintTime = new TextPaint();
    private TextPaint textPaintTimeStroke = new TextPaint();
    private TextPaint textPaintInfo = new TextPaint();
    private Paint paint = new Paint();

    private Rect bounds = new Rect(), container = new Rect();

    private int batteryLevel;
    private SunriseSunsetCalculation sunCalc = new SunriseSunsetCalculation(19.457216, 51.759445);
    private CrossFadeVideoView videoView;

    public ClockView(Context context) {
        super(context);
        init(context);
    }

    public ClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ClockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Make this view transparent so video shows through
        setBackgroundColor(Color.TRANSPARENT);

        // Load the digital font
        custom_font = Typeface.createFromAsset(context.getApplicationContext().getAssets(), "fonts/segments.ttf");
    }

    public void setBatteryLevel(int level) {
        this.batteryLevel = level;
        invalidate();
    }

    public void setVideoView(CrossFadeVideoView videoView) {
        this.videoView = videoView;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Clear canvas with transparency
        canvas.drawColor(Color.TRANSPARENT);

        // Anti aliasing
        paint.setFlags(Paint.ANTI_ALIAS_FLAG);

        // Get date & time
        int[] date_time = getDateTime();
        String day_of_week = getDayOfWeek(date_time[3]);
        Log.d("TIME", Calendar.getInstance().getTime().toString());

        //TODO: don't calculate times on every update - it's better to do it once per day
        SunriseSunsetCalculation.DayResult sun = sunCalc.calculateOfficialForDate(date_time[2], date_time[1], date_time[0]);
        boolean isDay = Calendar.getInstance().getTime().after(sun.getOfficialSunrise()) &&
                Calendar.getInstance().getTime().before(sun.getOfficialSunset());

        int frontColor = isDay ? Color.BLACK : Color.WHITE;

        if (videoView != null) {
            videoView.updateDayNightMode(isDay);
        }

        // Get screen size & set widths, heights, others
        int x = getWidth();
        int y = getHeight();
        int center_y = y / 2;

        // Show time
        textPaintTime.setTypeface(custom_font);
        textPaintTime.setColor(frontColor);
        textPaintTime.setStyle(Paint.Style.FILL);

        // Setup stroke (outline)
        textPaintTimeStroke.setTypeface(custom_font);
        textPaintTimeStroke.setColor(isDay ? Color.WHITE : Color.BLACK); // Opposite color for contrast
        textPaintTimeStroke.setStyle(Paint.Style.STROKE);
        textPaintTimeStroke.setStrokeWidth(8); // Adjust thickness as needed
        textPaintTimeStroke.setAntiAlias(true);

        String[] time_str = addLeadingZeros(date_time[4], date_time[5], date_time[6]);
        String middle = String.valueOf(time_str[0] + ":" + time_str[1]);
        container.set(0, 0, x, y);
        float fontSize = calculateFontSize(textPaintTime, bounds, container, middle);
        textPaintTime.setTextSize(fontSize * 0.98F);
        textPaintTimeStroke.setTextSize(fontSize * 0.98F);

        int x_pos = (x - bounds.width()) / 2;
        int y_pos = center_y + bounds.height() / 2;

        // Draw outline first, then fill on top
        canvas.drawText(middle, x_pos, y_pos, textPaintTimeStroke);
        canvas.drawText(middle, x_pos, y_pos, textPaintTime);

        if (batteryLevel > 0) {
            textPaintInfo.setTextSize(y / 16F);
            textPaintInfo.setColor(frontColor);
            canvas.drawText(String.format("%d%%", batteryLevel), 0, y - (y / 16F), textPaintInfo);
        }

        // Redraw every second
        postInvalidateDelayed(1000);
    }

    private float calculateFontSize(Paint textPaint, Rect textBounds, Rect textContainer, String text) {
        int stage = 1;
        float textSize = 0;

        while (stage < 3) {
            if (stage == 1) textSize += 10;
            else if (stage == 2) textSize -= 1;

            textPaint.setTextSize(textSize);
            textPaint.getTextBounds(text, 0, text.length(), textBounds);
            float width = textPaint.measureText(text);
            textBounds.right = (int) (textBounds.left + width);

            textBounds.offsetTo(textContainer.left, textContainer.top);

            boolean fits = textContainer.contains(textBounds);
            if (stage == 1 && !fits) stage++;
            else if (stage == 2 && fits) stage++;
        }

        return textSize;
    }

    private String[] addLeadingZeros(int hours, int minutes, int seconds) {
        String hours_str, minutes_str, seconds_str;

        if (hours <= 9)
            hours_str = " " + hours;
        else
            hours_str = String.valueOf(hours);

        if (minutes <= 9)
            minutes_str = "0" + minutes;
        else
            minutes_str = String.valueOf(minutes);

        if (seconds <= 9)
            seconds_str = "0" + seconds;
        else
            seconds_str = String.valueOf(seconds);

        String[] values = {hours_str, minutes_str, seconds_str};
        return values;
    }

    private int[] getDateTime() {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int day_of_week = calendar.get(Calendar.DAY_OF_WEEK);
        int ss = calendar.get(Calendar.SECOND);
        int mm = calendar.get(Calendar.MINUTE);
        int hh = calendar.get(Calendar.HOUR_OF_DAY);

        int[] values = {year, month, day, day_of_week, hh, mm, ss};
        return values;
    }

    private String getDayOfWeek(int day_of_week) {
        switch (day_of_week) {
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