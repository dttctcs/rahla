package rahla.extensions;

import lombok.extern.log4j.Log4j2;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.opentelemetry.OpenTelemetrySpanAdapter;
import org.apache.camel.tracing.ActiveSpanManager;
import org.apache.camel.util.ObjectHelper;

@Log4j2
public class SetAttributeProcessor implements Processor {
  private final String tagName;
  private final String tagFromHeader;

  public SetAttributeProcessor() {
    tagName = "tag";
    tagFromHeader = "tag";
  }

  public SetAttributeProcessor(String tagName, String tagFromHeader) {
    this.tagName = tagName;
    this.tagFromHeader = tagFromHeader;
    ObjectHelper.notNull(tagName, "tagName");
    ObjectHelper.notNull(tagFromHeader, "tagFromHeader");
  }

  @Override
  public void process(Exchange exchange) {

    try {
      OpenTelemetrySpanAdapter camelSpan =
          (OpenTelemetrySpanAdapter) ActiveSpanManager.getSpan(exchange);

      if (camelSpan != null) {
        Object tagValue = exchange.getMessage().getHeader(tagFromHeader);
        ObjectHelper.notNull(tagValue, tagFromHeader);
        camelSpan.setTag(tagName, tagValue.toString());
      } else {
        log.warn("OpenTelemetry: could not find managed span for exchange={}", exchange);
      }
    } catch (Exception var8) {
      exchange.setException(var8);
    }
  }
}
