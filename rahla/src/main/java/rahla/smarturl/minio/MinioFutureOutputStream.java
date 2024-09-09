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
