package rahla.components.fradi;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component("fradi")
public class FradiComponent extends DefaultComponent {
  @Metadata(required = true)
  private String plan;

  private FradiEngine fradiEngine = null;

  public static final String FRADI_TIMESTAMP_HEADER = "fradi.timestamp";
  public static final String FRADI_HEADER_FOR_EVENTS = "headerForEvents";

  public static final String RESOURCE_FILE = "resource:file:";
  public static final String RESOURCE_DEPLOY = "resource:deploy:";
  private String deploy_path = System.getenv().getOrDefault("RAHLA_DEPLOY_PATH", "/deploy");
  private static final Logger log = LogManager.getLogger();

  public FradiComponent() {
      log.warn("Fradi Component is EOL and becomes removed in on of the next releases, due to inactivity on siddhi. We recommend to migrate towards camel-mybatis for database cruds.");
      if (!deploy_path.endsWith("/"))
        deploy_path += "/";
      fradiEngine = new FradiEngine();
    }


  public FradiComponent(String urlSpec) throws IOException {
    if (!deploy_path.endsWith("/"))
      deploy_path += "/";
    fradiEngine = new FradiEngine();
    URL url = new URL(urlSpec);
    InputStream inputStream = url.openStream();
    this.plan = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    inputStream.close();
    startEngine();
  }

  private void startEngine(){
    fradiEngine.init(this.plan);
    fradiEngine.start();
  }

    protected Endpoint createEndpoint (String uri, String remaining, Map < String, Object > parameters) throws Exception
    {
      if (plan == null) {
        throw new IllegalArgumentException("No Siddhi executionplan configured, default creation not possible");
      }
      Endpoint endpoint = new FradiEndpoint(uri, remaining, this, fradiEngine);
      setProperties(endpoint, parameters);
      return endpoint;
    }

    /**
     * To use the {@link String} plan.
     */
    public String getPlan () {
      return plan;
    }

    public void setPlan (String plan){
      log.warn("setPlan is deprecated and will be removed in a future release! Use FradiComponent(String urlSpec) instead");
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
      startEngine();
    }

    @Override
    protected void doStart () throws Exception {
      if (plan == null) {
        throw new IllegalArgumentException("No Fradi Plan configured, start not possible");
      }
      super.doStart();
    }

    @Override
    protected void doStop () throws Exception {
      super.doStop();
      fradiEngine.shutdown();
    }

    public FradiEngine getFradiEngine () {
      return fradiEngine;
    }
  }
