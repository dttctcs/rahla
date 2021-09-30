package rahla;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.BufferPoolsExports;
import io.prometheus.client.hotspot.ClassLoadingExports;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import io.prometheus.client.hotspot.MemoryAllocationExports;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import io.prometheus.client.hotspot.StandardExports;
import io.prometheus.client.hotspot.ThreadExports;
import io.prometheus.client.hotspot.VersionInfoExports;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.Servlet;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;

@Component(
    property = {"alias=/metrics", "servlet-name=metrics"},
    immediate = true)
@Slf4j
public class PromServlet extends MetricsServlet implements Servlet {

  private List<Collector> collectors = new LinkedList<>();

  @Activate
  public void activate() {

    collectors.add((new StandardExports()).register(CollectorRegistry.defaultRegistry));
    collectors.add((new MemoryPoolsExports()).register(CollectorRegistry.defaultRegistry));
    collectors.add((new MemoryAllocationExports()).register(CollectorRegistry.defaultRegistry));
    collectors.add((new BufferPoolsExports()).register(CollectorRegistry.defaultRegistry));
    collectors.add((new GarbageCollectorExports()).register(CollectorRegistry.defaultRegistry));
    collectors.add((new ThreadExports()).register(CollectorRegistry.defaultRegistry));
    collectors.add((new ClassLoadingExports()).register(CollectorRegistry.defaultRegistry));
    collectors.add((new VersionInfoExports()).register(CollectorRegistry.defaultRegistry));
  }

  @Deactivate
  public void deactivate() {
    for (Collector collector : collectors) {
      CollectorRegistry.defaultRegistry.unregister(collector);
    }
  }
}
