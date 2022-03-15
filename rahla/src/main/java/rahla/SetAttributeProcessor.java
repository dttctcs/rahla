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
  private final String tagNameHeader;
  private final String tagValueHeader;

  public SetAttributeProcessor() {
    tagNameHeader = "tagName";
    tagValueHeader = "tagValue";
  }

  public SetAttributeProcessor(String tagNameHeader, String tagValueHeader) {
    this.tagNameHeader = tagNameHeader;
    this.tagValueHeader = tagValueHeader;
    ObjectHelper.notNull(tagNameHeader, "tagNameHeader");
    ObjectHelper.notNull(tagValueHeader, "tagValueHeader");
  }

  @Override
  public void process(Exchange exchange) {

    try {
      OpenTelemetrySpanAdapter camelSpan =
          (OpenTelemetrySpanAdapter) ActiveSpanManager.getSpan(exchange);

      if (camelSpan != null) {
        String tagName = (String) exchange.getMessage().getHeader(tagNameHeader);
        if (tagName == null) {
          tagName = tagNameHeader;
        }
        Object tagValue = exchange.getMessage().getHeader(tagValueHeader);
        ObjectHelper.notNull(tagValue, tagValueHeader);
        camelSpan.setTag(tagName, tagValue.toString());
      } else {
        LOG.warn("OpenTelemetry: could not find managed span for exchange={}", exchange);
      }
    } catch (Exception var8) {
      exchange.setException(var8);
    }
  }
}
