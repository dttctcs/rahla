package rahla.components.fradi;

import com.lmax.disruptor.ExceptionHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
class LogExceptionHandler implements ExceptionHandler<Object> {
  private final String name;

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
