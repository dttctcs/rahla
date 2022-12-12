package rahla.observe;

import static org.osgi.framework.Bundle.ACTIVE;
import static org.osgi.framework.Bundle.RESOLVED;

import java.io.IOException;
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.log4j.Log4j2;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleRevisions;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property = {"alias=/healthy", "servlet-name=Health"},
    immediate = true)
@Log4j2
public class HealthServlet extends HttpServlet implements Servlet {
  @Reference private volatile List<CamelContext> camelContextList;

  private BundleContext bundleContext;
  private boolean strictHealth;

  @Activate
  public void activate(ComponentContext cc) throws InvalidSyntaxException, IOException {
    bundleContext = cc.getBundleContext();
    strictHealth = "strict".equalsIgnoreCase(System.getenv().getOrDefault("HEALTH_MODE", "normal"));
  }

  private boolean bundleCheck() {
    for (Bundle bundle : bundleContext.getBundles()) {
      BundleRevisions revisions = bundle.adapt(BundleRevisions.class);
      if (revisions == null) {
        continue;
      }
      boolean isFragment = false;
      for (BundleRevision revision : revisions.getRevisions()) {
        if (revision.getWiring() != null) {
          List<BundleWire> wires =
              revision.getWiring().getRequiredWires(BundleRevision.HOST_NAMESPACE);
          if (wires != null) {
            for (BundleWire w : wires) {
              Bundle b = w.getProviderWiring().getBundle();
              if (b != null) {
                isFragment = true;
              }
            }
          }
        }
      }

      if (((!isFragment && bundle.getState() != ACTIVE)
          || (bundle.getState() != RESOLVED && isFragment))) {
        log.warn(
            "action=bundle check failed, bundle={}, state={}",
            bundle.getSymbolicName(),
            bundle.getState());
        return false;
      }
    }
    return true;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (!camelCheck() || (strictHealth && !bundleCheck() )) {
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      resp.getWriter().write("Bad");
      resp.getWriter().flush();
      resp.getWriter().close();
      return;
    }
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write("Good");
    resp.getWriter().flush();
    resp.getWriter().close();
  }

  private boolean camelCheck() {
    for (CamelContext camelContext : camelContextList) {
      List<Route> routes = camelContext.getRoutes();
      for (Route route : routes) {
        ServiceStatus routeStatus = camelContext.getRouteController().getRouteStatus(route.getId());
        if (!(routeStatus.isStarted() ||  routeStatus.isStartable())) {
          log.warn("action=health check failed, reason={}", "camel route not startable or started");
          return false;
        }
      }
    }
    return !camelContextList.isEmpty();
  }
}
