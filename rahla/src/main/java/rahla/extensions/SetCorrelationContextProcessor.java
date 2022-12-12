package rahla.extensions;

import lombok.extern.log4j.Log4j2;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.opentelemetry.OpenTelemetrySpanAdapter;
import org.apache.camel.tracing.ActiveSpanManager;
import org.apache.camel.util.ObjectHelper;

@Log4j2
public class SetCorrelationContextProcessor implements Processor {
  private final String baggageName;
  private final String baggageFromHeader;

  public SetCorrelationContextProcessor() {
    baggageName = "baggage";
    baggageFromHeader = "baggage";
  }

  public SetCorrelationContextProcessor(String baggageNameHeader, String baggageFromHeader) {
    this.baggageName = baggageNameHeader;
    this.baggageFromHeader = baggageFromHeader;
    ObjectHelper.notNull(baggageNameHeader, "baggageName");
    ObjectHelper.notNull(baggageFromHeader, "baggageFromHeader");
  }

  @Override
  public void process(Exchange exchange) {
    try {
      OpenTelemetrySpanAdapter camelSpan =
              (OpenTelemetrySpanAdapter) ActiveSpanManager.getSpan(exchange);

      if (camelSpan != null) {
        Object baggageValue = exchange.getMessage().getHeader(baggageFromHeader);
        ObjectHelper.notNull(baggageValue, baggageFromHeader);
        camelSpan.setCorrelationContextItem(baggageName, baggageValue.toString());
      } else {
        log.warn("OpenTelemetry: could not find managed span for exchange={}", exchange);
      }
    } catch (Exception var8) {
      exchange.setException(var8);
    }
  }
}
