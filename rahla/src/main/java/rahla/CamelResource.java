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
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;
import rahla.auth.ApiKeySecured;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Slf4j
@JaxrsResource

@Component(service = CamelResource.class, immediate = true)
@Path("/camel")
@ApiKeySecured
public class CamelResource {

  @Reference private volatile List<CamelContext> contexts;

  @Path("/routeinfo")
  @Produces(MediaType.APPLICATION_JSON)
  @GET
  public Map<String, List<RouteInfo>> routeInfo() throws Exception {
    Map<String, List<RouteInfo>> res = new LinkedHashMap<>();
    for (CamelContext context : contexts) {
      List<RouteInfo> routeInfos = new LinkedList<>();
      for (RouteDefinition def : ((DefaultCamelContext) context).getRouteDefinitions()) {
        String model =
            context
                .adapt(ExtendedCamelContext.class)
                .getModelToXMLDumper()
                .dumpModelAsXml(context, def, true, true);
        String stats =
            context
                .getExtension(ManagedCamelContext.class)
                .getManagedRoute(def.getRouteId())
                .dumpRouteStatsAsXml(true, true);
        routeInfos.add(new RouteInfo(def.getRouteId(), model, stats));
      }

      res.put(context.getName(), routeInfos);
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
    private String id;
    public String model;
    public String stats;
  }
}
