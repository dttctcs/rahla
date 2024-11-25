package rahla.components.fradi;

import com.lmax.disruptor.ExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class LogExceptionHandler implements ExceptionHandler<Object> {
  private final String name;
  private static final Logger log = LogManager.getLogger();
  public LogExceptionHandler(String name) {
    this.name = name;
  }

  @Override
  public void handleEventException(Throwable throwable, long l, Object o) {
    log.error("app={}, reason=fradi event exception, event={}", name, o, throwable);
  }

  @Override
  public void handleOnStartException(Throwable throwable) {
    log.error("app={}, reason=fradi event exception, event=shutdown", name, throwable);
  }

  @Override
  public void handleOnShutdownException(Throwable throwable) {
    log.error("app={}, reason=fradi event exception, event=shutdown", name, throwable);
  }
}
