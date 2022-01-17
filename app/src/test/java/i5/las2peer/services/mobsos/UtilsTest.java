package i5.las2peer.services.mobsos;

import i5.las2peer.services.mobsos.surveys.Utils;
import org.junit.Test;

import static org.junit.Assert.*;

import java.text.ParseException;
import java.util.TimeZone;

public class UtilsTest {
    @Test
    public void convertTimeToUTC() throws ParseException {
        // Input is already UTC
        String s = Utils.convertTimeToUTC("2020-07-20T23:50:31Z", TimeZone.getTimeZone("Europe/Berlin"));
        assertEquals("2020-07-20T23:50:31Z", s);

        // Input is in CEST
        s = Utils.convertTimeToUTC("2020-07-20T02:30:31", TimeZone.getTimeZone("Europe/Berlin"));
        assertEquals("2020-07-20T00:30:31Z", s);

        // Input is in CET
        s = Utils.convertTimeToUTC("2020-01-20T02:30:31", TimeZone.getTimeZone("Europe/Berlin"));
        assertEquals("2020-01-20T01:30:31Z", s);
    }
}