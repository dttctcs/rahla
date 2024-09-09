package rahla.smarturl.minio;

import io.minio.*;
import lombok.extern.log4j.Log4j2;
import rahla.smarturl.SmartURLConnection;
import rahla.smarturl.SmartURLStreamHandlerService;

import java.io.*;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Log4j2
public class MinioSmartURLConnection extends SmartURLConnection {

  private static final Set<String> bucketCache = Collections.synchronizedSet(new HashSet<>());
  private final MinioClient minioClient;
  private final String bucketName;
  private final String objectName;
  private ExecutorService executor = Executors.newCachedThreadPool();

  public MinioSmartURLConnection(
      URL u, MinioClient minioClient, SmartURLStreamHandlerService smartUrlStreamHandlerService) {
    super(u, smartUrlStreamHandlerService);
    this.minioClient = minioClient;
    this.bucketName = url.getHost();
    this.objectName = url.getPath().substring(1);
    if (!bucketCache.contains(bucketName)) {
      try {
        boolean exists =
            minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
          minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
        bucketCache.add(bucketName);
      } catch (Exception e) {
        log.error("Bug():{}", e.getMessage() ,e);
      }
    }
  }

  @Override
  public void connect() throws IOException {}

  @Override
  public InputStream getInputStream() throws IOException {
    GetObjectArgs build =
        GetObjectArgs.builder().bucket(bucketName).object(this.objectName).build();
    try {
      return new BufferedInputStream(minioClient.getObject(build));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos, 8192);
    DataInputStream dataInputStream = new DataInputStream(pis);
    DataOutputStream dataOutputStream = new DataOutputStream(pos);
    Future<ObjectWriteResponse> future =
        executor.submit(
            () -> {
              try {
                ObjectWriteResponse objectWriteResponse = minioClient.putObject(
                        PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                                        dataInputStream, -1, ObjectWriteArgs.MIN_MULTIPART_SIZE)
                                .build());
                return objectWriteResponse;
              } catch (Exception e) {
                log.error("Error during minio put object; Unexpected", e);
                throw new RuntimeException(e);
              }
            });
    return new MinioFutureOutputStream(dataOutputStream, dataInputStream, future);
  }

  public void delete() {
    RemoveObjectArgs build =
        RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build();
    try {
      minioClient.removeObject(build);
    } catch (Exception e) {
      log.error("Erroing during delete {}/{}", bucketName, objectName, e);
    }
  }

  @Override
  public void duplicate(URL url) {
    CopyObjectArgs build =
        CopyObjectArgs.builder()
            .bucket(url.getHost())
            .object(url.getPath().substring(1))
            .source(CopySource.builder().bucket(bucketName).object(objectName).build())
            .build();
    try {
      minioClient.copyObject(build);
    } catch (Exception e) {
      log.error("Erroing during duplication {}/{}", bucketName, objectName, e);
      throw new RuntimeException(e);
    }
  }
}
