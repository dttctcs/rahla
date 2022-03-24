package rahla;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsName;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Path("/camel")
@Slf4j
@JaxrsResource
@JaxrsName("camel")
@Component(immediate = true)
public class CamelResource {

  @Reference private volatile List<CamelContext> contexts;

  @Path("/routeinfo")
  @Produces(MediaType.APPLICATION_JSON)
  @GET
  public List<RouteInfo> doit() throws Exception {
    List<RouteInfo> res = new LinkedList<>();
    for (CamelContext context : contexts) {

      List<RouteDefinition> routeDefinitions =
          ((DefaultCamelContext) context).getRouteDefinitions();
      for (RouteDefinition routeDefinition : routeDefinitions) {
        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        String model =
            ecc.getModelToXMLDumper().dumpModelAsXml(context, routeDefinition, true, true);

        res.add(new RouteInfo(model, "foo"));
      }
    }
    return res;
  }

  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  @GET
  public Map<String, ?> routes() {
    Map<String, String> res = new LinkedHashMap<>();
    contexts.forEach(
        camelContext -> {
          try {
            ManagedCamelContext managed = camelContext.getExtension(ManagedCamelContext.class);
            String xml = managed.getManagedCamelContext().dumpRoutesAsXml(true);
            if (!(xml == null) && !xml.isBlank()) {
              res.put(camelContext.getName(), xml);
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
    return res;
  }

  @Getter
  @AllArgsConstructor
  public static class RouteInfo {
    String model;
    String stats;
  }
}
