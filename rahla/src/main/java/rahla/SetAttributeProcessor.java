package rahla;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.opentelemetry.OpenTelemetrySpanAdapter;
import org.apache.camel.tracing.ActiveSpanManager;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetAttributeProcessor implements Processor {
  private static final Logger LOG = LoggerFactory.getLogger(SetAttributeProcessor.class);
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
        LOG.warn("OpenTelemetry: could not find managed span for exchange={}", exchange);
      }
    } catch (Exception var8) {
      exchange.setException(var8);
    }
  }
}
