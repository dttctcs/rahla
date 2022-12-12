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
