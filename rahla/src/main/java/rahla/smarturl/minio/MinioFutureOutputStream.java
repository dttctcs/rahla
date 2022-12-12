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

package rahla.smarturl.minio;

import io.minio.ObjectWriteResponse;
import lombok.extern.log4j.Log4j2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Future;

@Log4j2
public class MinioFutureOutputStream extends OutputStream {
  private final OutputStream dataOutputStream;
  private final Future<ObjectWriteResponse> future;
  private final DataInputStream dataInputStream;

  public MinioFutureOutputStream(DataOutputStream dataOutputStream, DataInputStream dataInputStream, Future<ObjectWriteResponse> future) {
    this.dataOutputStream = dataOutputStream;
    this.dataInputStream = dataInputStream;
    this.future = future;
  }


  @Override
  public void write(int b) throws IOException {
    dataOutputStream.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    dataOutputStream.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    dataOutputStream.write(b, off, len);
  }

  @Override
  public void flush() throws IOException {
    dataOutputStream.flush();
  }

  @Override
  public void close() throws IOException {
    dataOutputStream.flush();

    while(dataInputStream.available()>0 ) {
      dataOutputStream.flush();
      //FIXME here we have pipe problems with minio > 8.2
      //      try {
      //        Thread.sleep(0,1);
      //      } catch (InterruptedException e) {
      //        log.warn("reason=busy wait interrupted",e);
//      }
    }
    dataOutputStream.close();
    try {
      ObjectWriteResponse objectWriteResponse = future.get();
      if (objectWriteResponse==null){
        throw new RuntimeException("objectWrite failed");
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
