package rahla;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.opentelemetry.OpenTelemetrySpanAdapter;
import org.apache.camel.tracing.ActiveSpanManager;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetCorrelationContextProcessor implements Processor {
  private static final Logger LOG = LoggerFactory.getLogger(SetAttributeProcessor.class);
  private final String baggageNameHeader;
  private final String baggageValueHeader;

  public SetCorrelationContextProcessor() {
    baggageNameHeader = "baggageName";
    baggageValueHeader = "baggageValue";
  }

  public SetCorrelationContextProcessor(String baggageNameHeader, String baggageValueHeader) {
    this.baggageNameHeader = baggageNameHeader;
    this.baggageValueHeader = baggageValueHeader;
    ObjectHelper.notNull(baggageNameHeader, "baggageNameHeader");
    ObjectHelper.notNull(baggageValueHeader, "baggageValueHeader");
  }

  @Override
  public void process(Exchange exchange) {

    try {
      OpenTelemetrySpanAdapter camelSpan =
          (OpenTelemetrySpanAdapter) ActiveSpanManager.getSpan(exchange);

      if (camelSpan != null) {
        String baggageName = (String) exchange.getMessage().getHeader(baggageNameHeader);
        if (baggageName == null) {
          baggageName = baggageNameHeader;
        }
        Object baggageValue = exchange.getMessage().getHeader(baggageValueHeader);
        ObjectHelper.notNull(baggageValue, baggageValueHeader);
        camelSpan.setCorrelationContextItem(baggageName, baggageValue.toString());
      } else {
        LOG.warn("OpenTelemetry: could not find managed span for exchange={}", exchange);
      }
    } catch (Exception var8) {
      exchange.setException(var8);
    }
  }
}
