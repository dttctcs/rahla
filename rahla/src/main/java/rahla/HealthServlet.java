package rahla;

import java.io.IOException;
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property = {"alias=/healthy", "servlet-name=Health"},
    immediate = true)
@Slf4j
public class HealthServlet extends HttpServlet implements Servlet {
  @Reference private volatile List<CamelContext> camelContextList;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    if (!check()) {
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      resp.getWriter().write("Sick");
      resp.getWriter().flush();
      resp.getWriter().close();
      return;
    }
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write("Healthy");
    resp.getWriter().flush();
    resp.getWriter().close();
  }

  private boolean check() {
    for (CamelContext camelContext : camelContextList) {
      List<Route> routes = camelContext.getRoutes();
      for (Route route : routes) {
        ServiceStatus routeStatus = camelContext.getRouteController().getRouteStatus(route.getId());
        boolean started = routeStatus.isStarted();
        boolean startable = routeStatus.isStartable();
        if (!started && !startable) {
          return false;
        }
      }
    }
    return !camelContextList.isEmpty();
  }
}
