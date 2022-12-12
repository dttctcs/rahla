package rahla.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.LinkedHashMap;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ExchangeFormatter;

public class RahlaLogFormatter implements ExchangeFormatter {

  private final ObjectMapper om = new ObjectMapper();

  @Override
  public String format(Exchange exchange) {
    String exchangeId = exchange.getExchangeId();
    String body = exchange.getIn().getBody(String.class);
    StringBuilder sb = new StringBuilder();
    first("exchangeId", exchangeId, sb);
    add("message", body, sb);
    exchange.getMessage().getHeaders().forEach((key, value) -> add(key, value, sb));
    return sb.toString();
  }

  private void first(String key, Object value, StringBuilder sb) {
    sb.append(key);
    sb.append("=");
    try {
      sb.append(om.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      sb.append(e.getMessage());
    }
  }

  private void add(String key, Object value, StringBuilder sb) {
    sb.append(", ");
    sb.append(key);
    sb.append("=");
    try {
      sb.append(om.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      sb.append(e.getMessage());
    }
  }
}
