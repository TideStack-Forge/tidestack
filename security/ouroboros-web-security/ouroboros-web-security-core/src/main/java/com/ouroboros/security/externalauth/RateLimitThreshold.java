package com.ouroboros.security.externalauth;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed rate-limit threshold from grammar: {@code <positive-int>/<positive-int>m}.
 * Bounds: count in [1, 1000], windowMinutes in [1, 1440].
 */
public class RateLimitThreshold {

    private static final Pattern THRESHOLD_PATTERN = Pattern.compile("^([1-9][0-9]*)/([1-9][0-9]*)m$");

    private final int count;
    private final int windowMinutes;

    RateLimitThreshold(int count, int windowMinutes) {
        this.count = count;
        this.windowMinutes = windowMinutes;
    }

    public static RateLimitThreshold parse(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Rate limit threshold must not be null or empty");
        }
        Matcher matcher = THRESHOLD_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid rate limit threshold format: '" + value + "'. Expected: <positive-int>/<positive-int>m");
        }
        int count;
        int windowMinutes;
        try {
            count = Integer.parseInt(matcher.group(1));
            windowMinutes = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Rate limit threshold numeric overflow: '" + value + "'");
        }
        if (count < 1 || count > 1000) {
            throw new IllegalArgumentException(
                "Rate limit count must be in [1, 1000], got: " + count);
        }
        if (windowMinutes < 1 || windowMinutes > 1440) {
            throw new IllegalArgumentException(
                "Rate limit windowMinutes must be in [1, 1440], got: " + windowMinutes);
        }
        return new RateLimitThreshold(count, windowMinutes);
    }

    public int getCount() { return count; }
    public int getWindowMinutes() { return windowMinutes; }
    public long getWindowMillis() { return windowMinutes * 60_000L; }
}
