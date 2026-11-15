package app.georelay;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
public class CaptureTimeTest {
    @Test public void explicitOffsetAndSubseconds() {
        assertEquals(Instant.parse("2026-11-15T05:30:00.125Z").toEpochMilli(), CaptureTime.parse("2026:11:15 13:30:00", "125", "+08:00", ZoneId.of("UTC")));
    }
    @Test public void fallbackZone() {
        assertEquals(Instant.parse("2026-11-15T05:30:00Z").toEpochMilli(), CaptureTime.parse("2026:11:15 13:30:00", null, null, ZoneId.of("Asia/Shanghai")));
    }
    @Test(expected=IllegalArgumentException.class) public void missingTime() { CaptureTime.parse(null, null, null, ZoneId.of("UTC")); }
    @Test(expected=IllegalArgumentException.class) public void invalidDate() { CaptureTime.parse("2026:02:30 12:00:00", null, null, ZoneId.of("UTC")); }
    @Test(expected=IllegalArgumentException.class) public void ambiguousDst() { CaptureTime.parse("2026:11:01 01:30:00", null, null, ZoneId.of("America/New_York")); }
    @Test(expected=IllegalArgumentException.class) public void nonexistentDst() { CaptureTime.parse("2026:03:08 02:30:00", null, null, ZoneId.of("America/New_York")); }
}
