package rahla.routetemplates;

import lombok.extern.log4j.Log4j2;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import java.util.*;

@Component(
    configurationPid = "camel.route.template",
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true)
@Log4j2
public class OsgiRouteTemplateParameterSource implements RouteTemplateParameterSource {
  public static final String TEMPLATE_PREFIX = "camel.route.template.";

  private String templateId;
  private Set<String> routeIds = new HashSet();
  private Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();


  @Activate
  public void activate(ComponentContext cc) {
    Dictionary<String, Object> properties = cc.getProperties();

    templateId = (String) properties.get(RouteTemplateParameterSource.TEMPLATE_ID);
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
