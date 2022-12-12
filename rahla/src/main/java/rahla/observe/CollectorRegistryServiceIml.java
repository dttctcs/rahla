package rahla.observe;

import io.prometheus.client.CollectorRegistry;
import org.osgi.service.component.annotations.Component;
import rahla.api.CollectorRegistryService;

@Component(immediate = true)
public class CollectorRegistryServiceIml implements CollectorRegistryService {

    private final CollectorRegistry registry = CollectorRegistry.defaultRegistry;

    @Override
    public CollectorRegistry getRegistry() {
        return registry;
    }
}
