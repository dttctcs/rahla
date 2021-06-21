package rahla.provider;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.ops4j.pax.logging.spi.PaxAppender;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.osgi.service.component.annotations.Component;
import rahla.api.LogHistory;

@Component(
    property = {"org.ops4j.pax.logging.appender.name=Rahla"},
    immediate = true)
public class LogHistoryImpl implements PaxAppender, LogHistory {

  private final CircularFifoQueue<PaxLoggingEvent> events;

  public LogHistoryImpl() {
    events = new CircularFifoQueue<>(512);
  }

  @Override
  public void doAppend(PaxLoggingEvent event) {
    events.add(event);
  }

  @Override
  public List<PaxLoggingEvent> get(String logger) {
    return events.stream()
        .filter(paxLoggingEvent -> paxLoggingEvent.getLoggerName().equals(logger))
        .collect(Collectors.toList());
  }
}
