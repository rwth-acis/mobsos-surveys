package i5.las2peer.services.mobsos.surveys;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;



public class Utils {
    public static String convertTimeToUTC(String lexicalXSDDateTime, TimeZone timeZone) throws ParseException {
        if (lexicalXSDDateTime.endsWith("Z"))
            return lexicalXSDDateTime;

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        df.setTimeZone(timeZone);
        Date date = df.parse(lexicalXSDDateTime);
        df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date);
    }
}
