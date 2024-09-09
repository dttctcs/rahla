package rahla.smarturl.redis;

import lombok.extern.log4j.Log4j2;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import rahla.api.JedisSource;
import rahla.smarturl.SmartURLConnection;
import rahla.smarturl.SmartURLStreamHandlerService;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component(
    configurationPid =
        SmartURLStreamHandlerService.BASE_PID + RedisURLStreamHandlerService.CONFIG_PID,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true,
    service = {SmartURLStreamHandlerService.class, URLStreamHandlerService.class})
@Log4j2
public final class RedisURLStreamHandlerService extends AbstractURLStreamHandlerService
    implements SmartURLStreamHandlerService, URLStreamHandlerService {

  public static final String CONFIG_PID = "redis";
  public static final String DATAMAP_PREFIX = "smarturl::";

  private final Map<String, byte[]> dataMaps = new ConcurrentHashMap<>();
  private String protocol;

  @Reference private JedisSource jedisService;

  @Activate
  public void activate(ComponentContext cc) {
    protocol = (String) cc.getProperties().get(URLConstants.URL_HANDLER_PROTOCOL);
    log.info("action=activated url handler for {}", protocol);
  }

  @Deactivate
  public void deactivate(ComponentContext cc) {
    log.info("action=deactivated url handler for  {}", protocol);
  }

  @Override
  public SmartURLConnection openConnection(URL u) throws IOException {
    return new RedisSmartURLConnection(u, this);
  }

  @Override
  public String getProtocol() {
    return protocol;
  }

  synchronized byte[] get(String context, String id) {
    byte[] bytes = dataMaps.computeIfAbsent(context, s -> (DATAMAP_PREFIX + context).getBytes());
    try (Jedis jedis = jedisService.getResource()) {
      return jedis.hget(bytes, id.getBytes());
    } catch (Exception e) {
      log.error("action=get object {}/{},reason={}", context, id, e.getMessage(), e);
      return new byte[0];
    }
  }

  synchronized void store(String context, String id, byte[] data) {
    byte[] bytes = dataMaps.computeIfAbsent(context, s -> (DATAMAP_PREFIX + context).getBytes());
    try (Jedis jedis = jedisService.getResource()) {
      jedis.hset(bytes, id.getBytes(), data);
    } catch (Exception e) {
      log.error("action=store object {}/{},reason={}", context, id, e.getMessage(), e);
    }
  }

  synchronized void delete(String context, String id) {
    byte[] bytes = dataMaps.computeIfAbsent(context, s -> (DATAMAP_PREFIX + context).getBytes());
    try (Jedis jedis = jedisService.getResource()) {
      jedis.hdel(bytes, id.getBytes());
    } catch (Exception e) {
      log.error("action=delete object {}/{},reason={}", context, id, e.getMessage(), e);
    }
  }
}
