package com.lvr.standclock;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import android.location.Location;

public class SunriseSunsetCalculation {

    public static class SunriseSunsetCalcResult {
        Date time;
        double hourAngle;
    }

    public static class DayResult {
        public SunriseSunsetCalcResult officialSunrise;
        public SunriseSunsetCalcResult officialSunset;
        public SunriseSunsetCalcResult civilSunrise;
        public SunriseSunsetCalcResult civilSunset;
        public SunriseSunsetCalcResult nauticalSunrise;
        public SunriseSunsetCalcResult nauticalSunset;
        public SunriseSunsetCalcResult astroSunrise;
        public SunriseSunsetCalcResult astroSunset;

        public Date getOfficialSunrise() { return officialSunrise.time; }
        public Date getOfficialSunset() { return officialSunset.time; }
        public Date getCivilSunrise() { return civilSunrise.time; }
        public Date getCivilSunset() { return civilSunset.time; }
        public Date getNauticalSunrise() { return nauticalSunrise.time; }
        public Date getNauticalSunset() { return nauticalSunset.time; }
        public Date getAstroSunrise() { return astroSunrise.time; }
        public Date getAstroSunset() { return astroSunset.time; }

        public boolean doesSunRise() { return officialSunrise.hourAngle <= 1; }
        public boolean doesSunSet() { return officialSunset.hourAngle >= -1; }
        public boolean doesSunDawn() { return civilSunrise.hourAngle <= 1; }
        public boolean doesSunDusk() { return civilSunset.hourAngle >= -1; }
        public boolean doesSunNauticalDawn() { return nauticalSunrise.hourAngle <= 1; }
        public boolean doesSunNauticalDusk() { return nauticalSunset.hourAngle >= -1; }
        public boolean doesSunAstroDawn() { return astroSunrise.hourAngle <= 1; }
        public boolean doesSunAstroDusk() { return astroSunset.hourAngle >= -1; }
    }

    private final double longitude;
    private final double latitude;

    public SunriseSunsetCalculation(final Location location) {
        this.longitude = location.getLongitude();
        this.latitude = location.getLatitude();
    }

    public SunriseSunsetCalculation(final double longitude, final double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    /**
     * Calculate all sunrise/sunset times for a specific date
     */
    public DayResult calculateForDate(int day, int month, int year) {
        DayResult result = new DayResult();

        result.officialSunrise = calculateSunriseSunset(day, month, year, 90 + 5.0/6.0)[0];
        result.officialSunset = calculateSunriseSunset(day, month, year, 90 + 5.0/6.0)[1];
        result.civilSunrise = calculateSunriseSunset(day, month, year, 96)[0];
        result.civilSunset = calculateSunriseSunset(day, month, year, 96)[1];
        result.nauticalSunrise = calculateSunriseSunset(day, month, year, 102)[0];
        result.nauticalSunset = calculateSunriseSunset(day, month, year, 102)[1];
        result.astroSunrise = calculateSunriseSunset(day, month, year, 108)[0];
        result.astroSunset = calculateSunriseSunset(day, month, year, 108)[1];

        return result;
    }

    /**
     * Calculate official sunrise/sunset for a specific date
     */
    public DayResult calculateOfficialForDate(int day, int month, int year) {
        DayResult result = new DayResult();
        SunriseSunsetCalcResult[] sunriseSunset = calculateSunriseSunset(day, month, year, 90 + 5.0/6.0);
        result.officialSunrise = sunriseSunset[0];
        result.officialSunset = sunriseSunset[1];
        return result;
    }

    /**
     * Calculate civil sunrise/sunset for a specific date
     */
    public DayResult calculateCivilForDate(int day, int month, int year) {
        DayResult result = new DayResult();
        SunriseSunsetCalcResult[] sunriseSunset = calculateSunriseSunset(day, month, year, 96);
        result.civilSunrise = sunriseSunset[0];
        result.civilSunset = sunriseSunset[1];
        return result;
    }

    private double mysin(double degrees) {
        return Math.sin((degrees * Math.PI) / 180);
    }

    private double mycos(double degrees) {
        return Math.cos((degrees * Math.PI) / 180);
    }

    private double mytan(double degrees) {
        return Math.tan((degrees * Math.PI) / 180);
    }

    private double myasin(double x) {
        return Math.asin(x) * 180 / Math.PI;
    }

    private double myacos(double x) {
        return Math.acos(x) * 180 / Math.PI;
    }

    private double myatan(double x) {
        return Math.atan(x) * 180 / Math.PI;
    }

    private double getRightAscension(double t) {
        double M = (0.9856 * t) - 3.289;
        double L = M + (1.916 * mysin(M))
                + (0.020 * mysin(2 * M)) + 282.634;

        double RA = myatan(0.91764 * mytan(L));
        double Lquadrant = (Math.floor(L / 90)) * 90;
        double RAquadrant = (Math.floor(RA / 90)) * 90;
        RA = RA + (Lquadrant - RAquadrant);

        RA /= 15;
        return RA;
    }

    private double getHourAngle(double t, double zenith) {
        double M = (0.9856 * t) - 3.289;
        double L = M + (1.916 * mysin(M))
                + (0.020 * mysin(2 * M)) + 282.634;

        double sinDec = 0.39782 * mysin(L);
        double cosDec = mycos(myasin(sinDec));
        return (mycos(zenith) - (sinDec * mysin(latitude)))
                / (cosDec * mycos(latitude));
    }

    private SunriseSunsetCalcResult[] calculateSunriseSunset(int day, int month, int year, double zenith) {
        Date sunrise;
        Date sunset;

        double N1 = Math.floor(275 * (month + 1) / 9.0);
        double N2 = Math.floor(((month + 1) + 9) / 12.0);
        double N3 = (1 + Math.floor((year - 4 * Math.floor(year / 4.0) + 2) / 3.0));
        double n = N1 - (N2 * N3) + day - 30;

        double lngHour = longitude / 15;
        double rise = n + ((6 - lngHour) / 24);
        double set = n + ((18 - lngHour) / 24);

        double cosHrise = getHourAngle(rise, zenith);
        double cosHset = getHourAngle(set, zenith);

        double Hrise = 360 - myacos(cosHrise);
        double Hset = myacos(cosHset);

        Hrise /= 15;
        Hset /= 15;

        double RArise = getRightAscension(rise);
        double RAset = getRightAscension(set);

        double Trise = Hrise + RArise - (0.06571 * rise) - 6.622;
        double Tset = Hset + RAset - (0.06571 * set) - 6.622;

        double utrise = Trise - lngHour;
        double utset = Tset - lngHour;

        utrise %= 24;
        utset %= 24;

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
        calendar.set(year, month, day, (int)utrise, (int)Math.round((utrise - Math.floor(utrise)) * 60), 0);
        sunrise = calendar.getTime();

        calendar.set(year, month, day, (int)utset, (int)Math.round((utset - Math.floor(utset)) * 60), 0);
        sunset = calendar.getTime();

        SunriseSunsetCalcResult[] sunriseSunset = new SunriseSunsetCalcResult[2];
        sunriseSunset[0] = new SunriseSunsetCalcResult();
        sunriseSunset[1] = new SunriseSunsetCalcResult();
        sunriseSunset[0].time = sunrise;
        sunriseSunset[0].hourAngle = cosHrise;
        sunriseSunset[1].time = sunset;
        sunriseSunset[1].hourAngle = cosHset;
        return sunriseSunset;
    }
}