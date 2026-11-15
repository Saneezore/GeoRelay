package app.georelay;

import java.time.*;
import java.time.format.*;
import java.util.Locale;

final class CaptureTime {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    static long parse(String date, String subsecond, String offset, ZoneId fallback) {
        if (date == null) throw new IllegalArgumentException("This image has no EXIF date taken. The source app must preserve DateTimeOriginal.");
        try {
            LocalDateTime time = LocalDateTime.parse(date.trim(), FORMAT);
            if (subsecond != null && !subsecond.trim().isEmpty()) {
                String digits = subsecond.trim();
                if (!digits.matches("[0-9]+")) throw new DateTimeException("Invalid subsecond");
                time = time.withNano(Integer.parseInt((digits + "000000000").substring(0, 9)));
            }
            if (offset != null && !offset.trim().isEmpty()) return time.toInstant(ZoneOffset.of(offset.trim())).toEpochMilli();
            // Ambiguous local times must not silently assign the wrong location.
            var offsets = fallback.getRules().getValidOffsets(time);
            if (offsets.size() != 1) throw new DateTimeException("Ambiguous local time");
            return time.toInstant(offsets.get(0)).toEpochMilli();
        } catch (DateTimeException | NumberFormatException e) {
            throw new IllegalArgumentException("The capture time or timezone is invalid or ambiguous.", e);
        }
    }
}
