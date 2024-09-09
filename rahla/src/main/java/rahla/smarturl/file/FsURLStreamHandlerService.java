package rahla.smarturl.file;

import lombok.extern.log4j.Log4j2;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import rahla.smarturl.SmartURLConnection;
import rahla.smarturl.SmartURLStreamHandlerService;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component(
        configurationPid = SmartURLStreamHandlerService.BASE_PID + FsURLStreamHandlerService.CONFIG_PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true,
        service = {SmartURLStreamHandlerService.class, URLStreamHandlerService.class})
@Log4j2
public final class FsURLStreamHandlerService extends AbstractURLStreamHandlerService
        implements SmartURLStreamHandlerService, URLStreamHandlerService {

  public static final String CONFIG_PID = "fs";
  public static final String BASE_PATH_PROP = "base.path";

  private String protocol;
  private Path basePath;

  @Activate
  public void activate(ComponentContext cc) {
    protocol = (String) cc.getProperties().get(URLConstants.URL_HANDLER_PROTOCOL);
    basePath = Paths.get((String) cc.getProperties().get(BASE_PATH_PROP));
    try {
      Files.createDirectories(basePath);
    } catch (IOException e) {
      log.error("action=create {}, reason={}", basePath, e.getMessage());
    }
    log.info("action=activated url handler for {}", protocol);
  }

  @Deactivate
  public void deactivate(ComponentContext cc) {
    log.info("action=deactivated url handler for  {}", protocol);
  }

  @Override
  public SmartURLConnection openConnection(URL u) throws IOException {
    return new FsSmartURLConnection(u, basePath, this);
  }

  @Override
  public String getProtocol() {
    return protocol;
  }

}
