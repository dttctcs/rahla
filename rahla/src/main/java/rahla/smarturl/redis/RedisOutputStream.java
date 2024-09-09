package rahla.smarturl.redis;

import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@Log4j2
public class RedisOutputStream extends ByteArrayOutputStream {

  private final RedisURLStreamHandlerService redisURLStreamHandlerService;
  private final String id;
  private final String context;
  private volatile boolean open = true;

  public RedisOutputStream(
      String context, String id, RedisURLStreamHandlerService redisURLStreamHandlerService) {
    super(8192);
    this.redisURLStreamHandlerService = redisURLStreamHandlerService;
    this.id = id;
    this.context = context;
  }

  @Override
  public void write(int b) {
    if (!open) {
      throw new RuntimeException("Stream is closed");
    }
    super.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) {
    if (!open) {
      throw new RuntimeException("Stream is closed");
    }
    super.write(b, off, len);
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    if (!open) {
      throw new RuntimeException("Stream is closed");
    }
    super.writeTo(out);
  }

  @Override
  public synchronized void close() {
    if (open) {
      open = false;
    }
    redisURLStreamHandlerService.store(context, id, toByteArray());
  }
}
