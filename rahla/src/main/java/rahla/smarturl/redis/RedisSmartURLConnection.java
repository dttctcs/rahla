/*
 * MIT License
 *
 * Copyright © 2020 Matthias Leinweber datatactics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
