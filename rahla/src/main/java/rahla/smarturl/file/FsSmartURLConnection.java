package rahla.smarturl.file;

import lombok.extern.log4j.Log4j2;
import rahla.smarturl.SmartURLConnection;
import rahla.smarturl.SmartURLStreamHandlerService;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

@Log4j2
public class FsSmartURLConnection extends SmartURLConnection {

  private final Path path;
  private final Path basePath;

  public FsSmartURLConnection(
      URL u, Path basePath, SmartURLStreamHandlerService smartUrlStreamHandlerService) {
    super(u, smartUrlStreamHandlerService);
    this.basePath = basePath;
    path = basePath.resolve(url.getHost()).resolve(url.getPath().substring(1));
  }

  @Override
  public void connect() throws IOException {}

  @Override
  public InputStream getInputStream() throws IOException {
    return new BufferedInputStream(Files.newInputStream(path));
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    Files.createDirectories(path.getParent());
    return new BufferedOutputStream(Files.newOutputStream(path));
  }

  public void delete() {
    try {
      Files.delete(path);
    } catch (IOException e) {
      log.error("Error deleting {}", path, e);
    }
  }

  @Override
  public void duplicate(URL url) {
    Path newPath = basePath.resolve(url.getHost()).resolve(url.getPath().substring(1));
    try {
      Files.createDirectories(newPath.getParent());
      Files.copy(path, newPath);
    } catch (IOException e) {
      log.error("Error duplicating {}", path, e);
      throw new RuntimeException(e);
    }
  }
}
