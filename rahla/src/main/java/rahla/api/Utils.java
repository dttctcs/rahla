package rahla.api;


import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

public enum Utils {
  ;
  static final TimeZone tz = TimeZone.getTimeZone("UTC");
  static final Locale lcl = Locale.getDefault();
//  static final List<FastDateFormat> fdf = new LinkedList<>();
  static final String[] possibleDateFormats = {
    "yyyy.MM.dd G 'at' HH:mm:ss z",
    "EEE, MMM d, ''yy",
    "h:mm a",
    "hh 'o''clock' a, zzzz",
    "K:mm a, z",
    "yyyyy.MMMMM.dd GGG hh:mm aaa",
    "EEE, d MMM yyyy HH:mm:ss Z",
    "yyMMddHHmmssZ",
    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "YYYY-'W'ww-u",
    "EEE, dd MMM yyyy HH:mm:ss z",
    "EEE, dd MMM yyyy HH:mm zzzz",
    "yyyy-MM-dd'T'HH:mm:ssZ",
    "yyyy-MM-dd'T'HH:mm:ss.SSSzzzz",
    "yyyy-MM-dd'T'HH:mm:sszzzz",
    "yyyy-MM-dd'T'HH:mm:ss z",
    "yyyy-MM-dd'T'HH:mm:ssz",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd'T'HHmmss.SSSz",
    "yyyy-MM-dd",
    "yyyyMMdd",
    "dd/MM/yy",
    "dd/MM/yyyy",
    "dd-MMM-yy",
    "dd-MMM-yy",
    "dd-MMM-yy HH:mm:ss",
  };

  public static final Pattern typePattern = Pattern.compile("^[a-z0-9_-]*$");

  static {
    for (String possibleDateFormat : possibleDateFormats) {
//      fdf.add(FastDateFormat.getInstance(possibleDateFormat, tz, lcl));
    }
  }

  public static Date parseDate(String inputDate) {

//    if (NumberUtils.isParsable(inputDate)) {
//      return new Date(Long.parseLong(inputDate));
//    }
//
//    for (final FastDateFormat fdf : fdf) {
//      try {
//        return fdf.parse(inputDate);
//      } catch (final IllegalArgumentException | ParseException ignore) {
//      }
//    }
//    throw new RuntimeException("Cant parse: " + inputDate);
    return null;
  }
}
