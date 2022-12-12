package rahla.smarturl;

import org.osgi.service.url.URLStreamHandlerService;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public interface SmartURLStreamHandlerService extends URLStreamHandlerService {

  String BASE_PID = "rahla.smarturl.";

  SmartURLConnection openConnection(URL u) throws IOException;

  String getProtocol();

}
