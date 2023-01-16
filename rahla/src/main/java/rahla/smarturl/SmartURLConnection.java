package rahla.smarturl;

import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownServiceException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public abstract class SmartURLConnection extends URLConnection {

  private final SmartURLStreamHandlerService smartUrlStreamHandlerService;

  /**
   * Constructs a URL connection to the specified URL. A connection to the object referenced by the
   * URL is not created.
   *
   * @param url the specified URL.
   */
  protected SmartURLConnection(URL url, SmartURLStreamHandlerService smartUrlStreamHandlerService) {
    super(url);
    this.smartUrlStreamHandlerService = smartUrlStreamHandlerService;
  }

  public abstract void delete();

  public abstract void duplicate(URL url);

  public abstract InputStream getInputStream() throws IOException;

  public InputStream getGZInputStream() throws IOException {
    return new GZIPInputStream(getInputStream());
  }

  public OutputStream getGZOutputStream() throws IOException {
    return new GZIPOutputStream(getOutputStream());
  }

    public InputStream getSnappyInputStream() throws IOException {
    return new SnappyInputStream(getInputStream());
  }

  public OutputStream getSnappyOutputStream() throws IOException {
    return new SnappyOutputStream(getOutputStream());
  }

}
