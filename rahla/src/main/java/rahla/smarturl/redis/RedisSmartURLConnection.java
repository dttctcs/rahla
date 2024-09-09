package rahla.smarturl.redis;

import lombok.extern.log4j.Log4j2;
import rahla.smarturl.SmartURLConnection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

@Log4j2
public class RedisSmartURLConnection extends SmartURLConnection {

  private final RedisURLStreamHandlerService redisURLStreamHandlerService;
  private final String id;
  private final String context;

  public RedisSmartURLConnection(URL u, RedisURLStreamHandlerService redisURLStreamHandlerService) {
    super(u, redisURLStreamHandlerService);
    id = url.getPath().substring(1);
    context = url.getHost();
    this.redisURLStreamHandlerService = redisURLStreamHandlerService;
  }

  @Override
  public void connect() throws IOException {}

  @Override
  public InputStream getInputStream() throws IOException {
    byte[] bytes = redisURLStreamHandlerService.get(context, id);
    if (bytes == null) {
      bytes = new byte[0];
      log.warn("Read from not existing key.");
    }
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return new RedisOutputStream(context, id, redisURLStreamHandlerService);
  }

  public void delete() {
    redisURLStreamHandlerService.delete(context, id);
  }

  @Override
  public void duplicate(URL url) {
    byte[] bytes = redisURLStreamHandlerService.get(context, id);
    String newContext = url.getHost();
    String newId = url.getPath().substring(1);
    redisURLStreamHandlerService.store(newContext, newId, bytes);
  }
}
