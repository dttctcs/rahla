package rahla.api;

import io.prometheus.client.CollectorRegistry;

public interface CollectorRegistryService {

    CollectorRegistry getRegistry();
}
