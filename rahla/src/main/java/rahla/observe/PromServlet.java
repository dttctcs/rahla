package rahla.observe;

import io.prometheus.client.Adapter;
import io.prometheus.client.Collector;
import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.*;
import io.prometheus.client.servlet.common.exporter.Exporter;
import io.prometheus.client.servlet.common.exporter.ServletConfigurationException;
import lombok.extern.log4j.Log4j2;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import rahla.api.CollectorRegistryService;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@Component(
    property = {"alias=/metrics", "servlet-name=metrics"},
    immediate = true)
@Log4j2
public class PromServlet extends MetricsServlet implements Servlet {

  private List<Collector> collectors = new LinkedList<>();
  @Reference
  private CollectorRegistryService registryService;
  private Exporter exporter;
  public PromServlet() {  }

  @Activate
  public void activate() throws ServletConfigurationException {
    collectors.add((new StandardExports()).register(registryService.getRegistry()));
    collectors.add((new MemoryPoolsExports()).register(registryService.getRegistry()));
    collectors.add((new MemoryAllocationExports()).register(registryService.getRegistry()));
    collectors.add((new BufferPoolsExports()).register(registryService.getRegistry()));
    collectors.add((new GarbageCollectorExports()).register(registryService.getRegistry()));
    collectors.add((new ThreadExports()).register(registryService.getRegistry()));
    collectors.add((new ClassLoadingExports()).register(registryService.getRegistry()));
    collectors.add((new VersionInfoExports()).register(registryService.getRegistry()));
    this.exporter = new Exporter(registryService.getRegistry(), null);
  }

  @Deactivate
  public void deactivate() {
    for (Collector collector : collectors) {
      registryService.getRegistry().unregister(collector);
    }
  }

  /*public void init(ServletConfig servletConfig) throws ServletException {
    try {
      super.init(servletConfig);
      this.exporter.init(Adapter.wrap(servletConfig));
    } catch (ServletConfigurationException var3) {
      throw new ServletException(var3);
    }
  }

  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    this.exporter.doGet(Adapter.wrap(req), Adapter.wrap(resp));
  }

  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    this.exporter.doPost(Adapter.wrap(req), Adapter.wrap(resp));
  }
*/
}
