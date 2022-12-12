package rahla.extensions;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import lombok.extern.log4j.Log4j2;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;

@Component(
    configurationPid = "camel.route.template",
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true)
@Log4j2
public class OsgiRouteTemplateParameterSource implements RouteTemplateParameterSource {
  public static final String SHARED_CONFIG_PID = "shared.config.pid";
  public static final String SHARED_CONFIG_COUNTER = "shared.config.counter";
  public static final String TEMPLATE_PREFIX = "camel.route.template.";

  private String templateId;
  private Set<String> routeIds = new HashSet();
  private Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();

  @Reference private ConfigurationAdmin configurationAdmin;

  @Activate
  public void activate(ComponentContext cc) {
    Dictionary<String, Object> properties = cc.getProperties();

    templateId = (String) properties.get(RouteTemplateParameterSource.TEMPLATE_ID);
    String sharedConfigPid = (String) properties.get(SHARED_CONFIG_PID);
    Enumeration<String> e = properties.keys();
    while (e.hasMoreElements()) {
      String k = e.nextElement();
      if (!k.startsWith(TEMPLATE_PREFIX)) continue;
      String substring = k.substring(TEMPLATE_PREFIX.length());
      int i = substring.lastIndexOf(".");
      String routeId = substring.substring(0, i);
      String parameterName = substring.substring(i + 1);
      routeIds.add(routeId);
      Map<String, Object> parameter = parameters
              .computeIfAbsent(routeId, s -> new HashMap<>());
      parameter.put(parameterName, properties.get(k));
      parameter.put("routeId", routeId);

    }
    if (sharedConfigPid != null) {
      try {
        updateSharedConfig(sharedConfigPid);
      } catch (IOException ioException) {
        log.error("action=update shared config, reason={}", ioException.getMessage(), ioException);
      }
    }
  }

  private void updateSharedConfig(String sharedConfigPid) throws IOException {
    Configuration configuration;

    configuration = configurationAdmin.getConfiguration(sharedConfigPid, null);
    Dictionary<String, Object> props = configuration.getProperties();
    if (props == null) {
      props = new Hashtable<>();
    }
    Object o = props.get(SHARED_CONFIG_COUNTER);
    int newCnt = 0;
    if (o != null) {
      newCnt = ((int) o) + 1;
    }
    props.put(SHARED_CONFIG_COUNTER, newCnt);
    configuration.update(props);
  }

  @Override
  public Map<String, Object> parameters(String routeId) {
    HashMap<String, Object> res = new HashMap<>(parameters.get(routeId));
    res.put(RouteTemplateParameterSource.TEMPLATE_ID, templateId);
    return res;
  }

  @Override
  public Set<String> routeIds() {
    return new HashSet<>(routeIds);
  }
}
