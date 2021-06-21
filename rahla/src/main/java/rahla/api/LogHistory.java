package rahla.api;

import java.util.List;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;

public interface LogHistory {
  List<PaxLoggingEvent> get(String logger);
}
