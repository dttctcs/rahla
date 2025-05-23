package rahla.extensions;

import lombok.extern.log4j.Log4j2;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.ResourceResolver;
import org.apache.camel.support.ResourceResolverSupport;
import org.apache.camel.support.ResourceSupport;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Log4j2
@ResourceResolver(DeployResourceResolver.SCHEME)
public class DeployResourceResolver extends ResourceResolverSupport {
  public static final String SCHEME = "deploy";
  private String deploy_path = System.getenv().getOrDefault("RAHLA_DEPLOY_PATH" , "/deploy");

  public DeployResourceResolver() {
    super(SCHEME);
    if (!deploy_path.endsWith("/"))
      deploy_path += "/";
  }

  @Override
  public Resource createResource(String location, String remaining) {
    log.warn("resource:deploy is deprecated. Use different ResourceResolver e.g. resource:file witl fully qualified path or relative to /rahla instead.");
    final File path = new File(tryDecodeUri(deploy_path + remaining));

    return new ResourceSupport(SCHEME, location) {
      @Override
      public boolean exists() {
        return path.exists();
      }

      @Override
      public URI getURI() {
        return path.toURI();
      }

      @Override
      public InputStream getInputStream() throws IOException {
        if (!exists()) {
          throw new FileNotFoundException(path + " does not exists");
        }
        if (path.isDirectory()) {
          throw new FileNotFoundException(path + " is a directory");
        }

        return new FileInputStream(path);
      }
    };
  }

  protected String tryDecodeUri(String uri) {
    try {
      // try to decode as the uri may contain %20 for spaces etc
      uri = URLDecoder.decode(uri, StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      getLogger().trace("Error URL decoding uri using UTF-8 encoding: {}. This exception is ignored.", uri);
      // ignore
    }

    return uri;
  }
}