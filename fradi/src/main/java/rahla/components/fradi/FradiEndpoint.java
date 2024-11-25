package rahla.components.fradi;

import io.siddhi.core.stream.input.InputHandler;
import io.siddhi.query.api.definition.StreamDefinition;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;

/**
 * fradi component which does bla bla.
 * <p>
 * TODO: Update one line description above what the component does.
 */
@UriEndpoint(firstVersion = "1.0", scheme = "fradi", title = "fradi", syntax = "fradi:streamId", category = {Category.BIGDATA})
public class FradiEndpoint extends DefaultEndpoint {
  private final FradiEngine fradiEngine;
  @UriPath
  @Metadata(required = true)
  private String streamId;
  @UriParam(defaultValue = "true")
  private boolean eventsAsMaps = true;

  @UriParam(defaultValue = "")
  private String headerForEvents = null;

  private static final Logger log = LogManager.getLogger();

  public FradiEndpoint(String uri, String streamId, FradiComponent component, FradiEngine fradiEngine) {
    super(uri, component);
    this.streamId = streamId;
    this.fradiEngine = fradiEngine;
  }

  public Producer createProducer() throws Exception {
    FradiProducer fradiProducer = new FradiProducer(this, streamId, fradiEngine);
    return fradiProducer;

  }


  public Consumer createConsumer(Processor processor) throws Exception {
    FradiConsumer consumer = new FradiConsumer(this, processor, streamId, fradiEngine);
    configureConsumer(consumer);
    return consumer;
  }

  /**
   * Some description of this option, and what it does
   */
  public void setStreamId(String streamId) {
    this.streamId = streamId;
  }

  public String getStreamId() {
    return streamId;
  }

  public boolean isEventsAsMaps() {
    return eventsAsMaps;
  }

  /**
   * Controls if the endpoint should handle in and output data as maps
   * @param eventsAsMaps
   */
  public void setEventsAsMaps(boolean eventsAsMaps) {
    this.eventsAsMaps = eventsAsMaps;
  }


  public String getHeaderForEvents() {
    return headerForEvents;
  }

  /**
   * if header name is set in/output events are used from header
   * @param headerForEvents
   */
  public void setHeaderForEvents(String headerForEvents) {
    this.headerForEvents = headerForEvents;
  }


  public ExecutorService createExecutor() {
    // TODO: Delete me when you implemented your custom component
    return getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "fradiConsumer");
  }
}
