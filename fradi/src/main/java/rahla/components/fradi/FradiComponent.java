package rahla.components.fradi;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component("fradi")
public class FradiComponent extends DefaultComponent {
  @Metadata(required = true)
  private String plan;

  private FradiEngine fradiEngine = null;

  public static final String FRADI_TIMESTAMP_HEADER = "timestamp";
  public static final String FRADI_HEADER_FOR_EVENTS = "headerForEvents";

  public static final String RESOURCE_FILE = "resource:file:";
  public static final String RESOURCE_DEPLOY = "resource:deploy:";
  private String deploy_path = System.getenv("RAHLA_DEPLOY_PATH");

  public FradiComponent() {
    if (!deploy_path.endsWith("/"))
      deploy_path += "/";
    fradiEngine = new FradiEngine();
  }

  protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
    if (plan == null) {
      throw new IllegalArgumentException("No Fradi Plan configured, default creation not possible");
    }
    Endpoint endpoint = new FradiEndpoint(uri, remaining, this, fradiEngine);
    setProperties(endpoint, parameters);
    return endpoint;
  }

  /**
   * To use the {@link String} plan.
   */
  public String getPlan() {
    return plan;
  }

  public void setPlan(String plan) {
    if (plan.startsWith(RESOURCE_FILE)) {
      String fileName = plan.substring(RESOURCE_FILE.length());
      Path path = Path.of(fileName);
      try {
        plan = Files.readString(path, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else if (plan.startsWith(RESOURCE_DEPLOY)) {
      String fileName = plan.substring(RESOURCE_DEPLOY.length());
      if (fileName.startsWith("/")) {
        fileName = fileName.substring(1);
      }
      fileName = deploy_path + fileName;
      Path path = Path.of(fileName);
      try {
        plan = Files.readString(path, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    this.plan = plan;
    fradiEngine.init(this.plan);
  }

  @Override
  protected void doStart() throws Exception {
    if (plan == null) {
      throw new IllegalArgumentException("No Fradi Plan configured, start not possible");
    }
    super.doStart();
    fradiEngine.start();
  }

  @Override
  protected void doStop() throws Exception {
    super.doStop();
    fradiEngine.shutdown();
  }

  public FradiEngine getFradiEngine() {
    return fradiEngine;
  }
}
