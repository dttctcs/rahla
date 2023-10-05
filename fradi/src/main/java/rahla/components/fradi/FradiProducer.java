package rahla.components.fradi;

import io.siddhi.core.event.Event;
import io.siddhi.core.stream.input.InputHandler;
import io.siddhi.query.api.definition.Attribute;
import lombok.extern.log4j.Log4j2;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static rahla.components.fradi.FradiComponent.FRADI_TIMESTAMP_HEADER;

@Log4j2
public class FradiProducer extends DefaultProducer {
  private final String streamId;
  private final FradiEngine fradiEngine;
  private final InputHandler inputHandler;
  private FradiEndpoint endpoint;


  public FradiProducer(FradiEndpoint endpoint, String streamId, FradiEngine fradiEngine) {
    super(endpoint);
    this.endpoint = endpoint;
    this.fradiEngine = fradiEngine;
    this.streamId = streamId;
    this.inputHandler = fradiEngine.getInputHandler(streamId);
  }

  private Object convertValue(Attribute attribute, Object val) {
    try {
      if (val == null) {
        return null;
      }
      switch (attribute.getType()) {
        case STRING:
          return String.valueOf(val);
        case BOOL:
          return val;
        case INT:
          return ((Number) (val)).intValue();
        case LONG:
          return ((Number) (val)).longValue();
        case FLOAT:
          return ((Number) (val)).floatValue();
        case DOUBLE:
          return ((Number) (val)).doubleValue();
        case OBJECT:
          return val;
        default:
          throw new RuntimeException("Object column can not be loaded via input file.");
      }
    } catch (Exception e) {
      log.error("converting={}:{}, instanceOf={}, to={}", attribute.getName(), val, val.getClass(), attribute.getType(), e);
      throw e;
    }
  }

  Object[] buildEventData(Map<String, Object> mapEvent) {
    List<Attribute> attributeList = fradiEngine.getStreamDefinition(streamId).getAttributeList();
    Object[] eventData = new Object[attributeList.size()];
    for (int i = 0; i < attributeList.size(); i++) {
      Attribute attribute = attributeList.get(i);
      String attibuteName = attribute.getName();
      Attribute.Type type = attribute.getType();
      if (!mapEvent.containsKey(attibuteName)) {
        throw new RuntimeException("Attribute " + attibuteName + " is missing in input Event");
      }
      eventData[i] = convertValue(attribute, mapEvent.get(attibuteName));
    }
    return eventData;
  }

  List<Event> generateEventsFromMaps(Long timestamp, Object body) {
    LinkedList<Event> res = new LinkedList<>();

    if (body instanceof Map) {
      Map<String, Object> mapEvent = (Map<String, Object>) body;
      res.add(new Event(timestamp, buildEventData(mapEvent)));
    } else if (body instanceof Iterable) {
      Iterable<Map<String, Object>> maps = (Iterable<Map<String, Object>>) body;
      for (Map<String, Object> mapEvent : maps) {
        res.add(new Event(timestamp, buildEventData(mapEvent)));
      }
    } else {
      throw new RuntimeException("Exchange body for generateEventsFromMaps must be map or iterable of maps, but is: " + body.toString());
    }
    return res;
  }

  private Object[] convertTypes(List<Attribute> attributeList, Object[] array) {
    for (int i = 0; i < attributeList.size(); i++) {
      array[i] = convertValue(attributeList.get(i), array[i]);
    }
    return array;

  }

  List<Event> generateEventsFromArrays(Long timestamp, Object body) {
    LinkedList<Event> res = new LinkedList<>();
    List<Attribute> attributeList = fradiEngine.getStreamDefinition(streamId).getAttributeList();
    if (body instanceof Iterable) {
      Iterable<Object[]> arrays = (Iterable<Object[]>) body;
      for (Object[] array : arrays) {
        if (array.length == attributeList.size()) {
          res.add(new Event(timestamp, convertTypes(attributeList, array)));
        } else {
          throw new RuntimeException("Invalid array length data for nested event arrays!");
        }
      }
    } else if (body instanceof Object[]) {
      Object[] array = (Object[]) body;
      if (array.length == attributeList.size()) {
        res.add(new Event(timestamp, convertTypes(attributeList, array)));
      } else {
        throw new RuntimeException("Invalid array length data for nested event arrays!");
      }
    } else throw new RuntimeException("Invalid Exchange data for event arrays!");
    return res;
  }


  public void process(Exchange exchange) throws Exception {
    Object header = exchange.getMessage().getHeader(FRADI_TIMESTAMP_HEADER);
    Long timestamp = null;
    if (header != null) {
      if (header instanceof Date) {
        timestamp = ((Date) header).getTime();
      } else if (header instanceof Long) {
        timestamp = (long) header;
      }
    }
    if (timestamp == null) {
      timestamp = exchange.getMessage().getMessageTimestamp();

    }

    String headerForEvents = endpoint.getHeaderForEvents();

    Object data;
    if (headerForEvents != null) {
      data = exchange.getMessage().getHeader(headerForEvents);
    } else {
      data = exchange.getMessage().getBody();
    }

    Event[] objects;
    if (endpoint.isEventsAsMaps()) {
      objects = generateEventsFromMaps(timestamp, data).toArray(new Event[]{});
    } else {
      objects = generateEventsFromArrays(timestamp, data).toArray(new Event[]{});
    }

    if (objects.length == 1) {
      inputHandler.send(objects[0]);
    } else if (objects.length > 1) {
      inputHandler.send(objects);
    }


  }

}
