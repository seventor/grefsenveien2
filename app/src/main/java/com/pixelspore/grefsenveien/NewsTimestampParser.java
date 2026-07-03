package com.pixelspore.grefsenveien;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NewsTimestampParser {

    private static final long RECENT_WINDOW_MS = 30L * 60L * 1000L;
    private static final Locale NB = new Locale("nb", "NO");
    private static final Pattern RELATIVE_HOURS_PATTERN =
            Pattern.compile("^(\\d+)\\s*t(?:imer)?\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_MINUTES_PATTERN =
            Pattern.compile("^(\\d+)\\s*min(?:utter)?\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TODAY_TIME_PATTERN =
            Pattern.compile("(?i)^i dag(?:\\s+kl\\.?)?\\s+(\\d{1,2}):(\\d{2})$");
    private static final Pattern CLOCK_TIME_PATTERN =
            Pattern.compile("^(\\d{1,2}):(\\d{2})$");

    private NewsTimestampParser() {}

    static boolean isPublishedWithinLast30Minutes(long publishedMs) {
        return publishedMs > 0L && System.currentTimeMillis() - publishedMs <= RECENT_WINDOW_MS;
    }

    @NonNull
    static String formatClockTime(long publishedMs, @Nullable String fallbackTimestamp) {
        long ms = publishedMs;
        if (ms <= 0L && fallbackTimestamp != null && !fallbackTimestamp.isEmpty()) {
            ms = parseDisplayTimestamp(fallbackTimestamp.trim());
        }
        if (ms > 0L) {
            SimpleDateFormat format = new SimpleDateFormat("HH:mm", NB);
            return format.format(new Date(ms));
        }
        return fallbackTimestamp != null ? fallbackTimestamp : "";
    }

    static long parsePublishedMs(@Nullable String displayTimestamp, @Nullable String isoDateTime) {
        if (isoDateTime != null && !isoDateTime.isEmpty()) {
            long isoMs = parseIsoDateTime(isoDateTime);
            if (isoMs > 0L) {
                return isoMs;
            }
        }
        if (displayTimestamp == null || displayTimestamp.isEmpty()) {
            return 0L;
        }
        return parseDisplayTimestamp(displayTimestamp.trim());
    }

    private static long parseIsoDateTime(String isoDateTime) {
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) {
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date date = format.parse(isoDateTime);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return 0L;
    }

    private static long parseDisplayTimestamp(String text) {
        Matcher hoursMatcher = RELATIVE_HOURS_PATTERN.matcher(text);
        if (hoursMatcher.matches()) {
            int hours = Integer.parseInt(hoursMatcher.group(1));
            return System.currentTimeMillis() - hours * 3_600_000L;
        }

        Matcher minutesMatcher = RELATIVE_MINUTES_PATTERN.matcher(text);
        if (minutesMatcher.matches()) {
            int minutes = Integer.parseInt(minutesMatcher.group(1));
            return System.currentTimeMillis() - minutes * 60_000L;
        }

        Matcher todayMatcher = TODAY_TIME_PATTERN.matcher(text);
        if (todayMatcher.matches()) {
            return todayAt(Integer.parseInt(todayMatcher.group(1)), Integer.parseInt(todayMatcher.group(2)));
        }

        Matcher clockMatcher = CLOCK_TIME_PATTERN.matcher(text);
        if (clockMatcher.matches()) {
            return todayAt(Integer.parseInt(clockMatcher.group(1)), Integer.parseInt(clockMatcher.group(2)));
        }

        long parsedDate = parseNorwegianDateTime(text, "EEEE d. MMMM 'kl.' HH:mm");
        if (parsedDate > 0L) {
            return parsedDate;
        }
        return parseNorwegianDateTime(text, "d. MMMM 'kl.' HH:mm");
    }

    private static long parseNorwegianDateTime(String text, String pattern) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern, NB);
            format.setLenient(false);
            Date date = format.parse(text);
            if (date == null) {
                return 0L;
            }
            Calendar parsed = Calendar.getInstance();
            parsed.setTime(date);
            Calendar result = Calendar.getInstance();
            result.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR));
            result.set(Calendar.MONTH, parsed.get(Calendar.MONTH));
            result.set(Calendar.DAY_OF_MONTH, parsed.get(Calendar.DAY_OF_MONTH));
            result.set(Calendar.HOUR_OF_DAY, parsed.get(Calendar.HOUR_OF_DAY));
            result.set(Calendar.MINUTE, parsed.get(Calendar.MINUTE));
            result.set(Calendar.SECOND, 0);
            result.set(Calendar.MILLISECOND, 0);
            return result.getTimeInMillis();
        } catch (ParseException e) {
            return 0L;
        }
    }

    private static long todayAt(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
