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
