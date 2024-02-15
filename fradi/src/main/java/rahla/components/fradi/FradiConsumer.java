package rahla.components.fradi;

import io.siddhi.core.event.Event;
import io.siddhi.core.stream.output.StreamCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static rahla.components.fradi.FradiComponent.FRADI_HEADER_FOR_EVENTS;

public class FradiConsumer extends DefaultConsumer {
  private final FradiEndpoint endpoint;
  private final FradiEngine fradiEngine;
  private final String streamId;

  private ExecutorService executorService;
  private StreamCallback streamCallback;

  public FradiConsumer(FradiEndpoint endpoint, Processor processor, String streamId, FradiEngine fradiEngine) {
    super(endpoint, processor);
    this.endpoint = endpoint;
    this.fradiEngine = fradiEngine;
    this.streamId = streamId;
  }


  @Override
  protected void doStart() throws Exception {
    super.doStart();
    if (streamCallback == null) {
      streamCallback = new StreamCallback() {

        @Override
        public void receive(Event[] events) {
          final Exchange exchange = createExchange(false);
          List<Object> res = new LinkedList<>();
          if (endpoint.isEventsAsMaps()) {

            for (Event event : events) {
              String[] attributeNameArray = fradiEngine.getStreamDefinition(streamId).getAttributeNameArray();
              LinkedHashMap<String, Object> eventMap = new LinkedHashMap<>();
              for (int i = 0; i < attributeNameArray.length; i++) {
                eventMap.put(attributeNameArray[i], event.getData(i));
              }
              res.add(eventMap);
            }

          } else {
            for (Event event : events) {
              res.add(event.getData());
            }
          }
          String headerForEvents = endpoint.getHeaderForEvents();

          if (headerForEvents != null) {
            exchange.getMessage().setHeader(FRADI_HEADER_FOR_EVENTS, res);
          } else {

            exchange.getMessage().setBody(res);
          }

          try {
            getProcessor().process(exchange);
          } catch (Exception e) {
            exchange.setException(e);
          } finally {
            if (exchange.getException() != null) {
              getExceptionHandler().handleException("Error processing exchange: ", exchange, exchange.getException());
            }
            releaseExchange(exchange, false);
          }
        }
      };
    }
    fradiEngine.addCallback(streamId, streamCallback);
  }

  @Override
  protected void doStop() throws Exception {
    fradiEngine.removeCallback(streamCallback);
    super.doStop();
  }

}
