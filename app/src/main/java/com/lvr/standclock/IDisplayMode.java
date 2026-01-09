package com.lvr.standclock;

public interface IDisplayMode  {

    enum CalendarMode {
        Neutral,
        Halloween,
        Christmas
    }

    public void SetDisplayMode(boolean isDay, CalendarMode calendarMode);
}
