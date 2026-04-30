package rahla.routetemplates;

import lombok.extern.log4j.Log4j2;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Camel {@link RouteTemplateParameterSource} backed by a
 * {@code camel.route.template} factory configuration created by
 * {@link TemplateFileInstaller}. Property keys follow the pattern
 * {@code camel.route.template.<routeId>.<parameterName>}.
 */
@Component(
    configurationPid = "camel.route.template",
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true)
@Log4j2
public class OsgiRouteTemplateParameterSource implements RouteTemplateParameterSource {
  public static final String TEMPLATE_PREFIX = "camel.route.template.";

  private String templateId;
  private final Set<String> routeIds = new HashSet<>();
  private final Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();


  @Activate
  public void activate(ComponentContext cc) {
    Dictionary<String, Object> properties = cc.getProperties();

    templateId = (String) properties.get(RouteTemplateParameterSource.TEMPLATE_ID);
    Enumeration<String> e = properties.keys();
    while (e.hasMoreElements()) {
      String k = e.nextElement();
      if (!k.startsWith(TEMPLATE_PREFIX)) continue;
      String suffix = k.substring(TEMPLATE_PREFIX.length());
      int dot = suffix.lastIndexOf('.');
      if (dot <= 0 || dot == suffix.length() - 1) {
        log.warn("Skipping malformed template parameter key: {}", k);
        continue;
      }
      String routeId = suffix.substring(0, dot);
      String parameterName = suffix.substring(dot + 1);
      routeIds.add(routeId);
      Map<String, Object> parameter = parameters
              .computeIfAbsent(routeId, s -> new HashMap<>());
      parameter.put(parameterName, properties.get(k));
      parameter.put("routeId", routeId);
    }
  }


  @Override
  public Map<String, Object> parameters(String routeId) {
    Map<String, Object> source = parameters.get(routeId);
    if (source == null) {
      throw new IllegalArgumentException("Unknown routeId: " + routeId);
    }
    HashMap<String, Object> res = new HashMap<>(source);
    res.put(RouteTemplateParameterSource.TEMPLATE_ID, templateId);
    return res;
  }

  @Override
  public Set<String> routeIds() {
    return new HashSet<>(routeIds);
  }
}
