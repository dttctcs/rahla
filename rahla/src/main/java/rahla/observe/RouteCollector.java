package rahla.observe;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import io.prometheus.client.*;
import lombok.extern.log4j.Log4j2;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.component.metrics.routepolicy.MetricsRegistryService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import rahla.api.CollectorRegistryService;

import java.util.*;
import java.util.Map.Entry;

@Component
@Log4j2
public class RouteCollector extends Collector {

  private final Map<String, GaugeMetricFamily> gauges = new LinkedHashMap<>();
  private final Map<String, CounterMetricFamily> counters = new LinkedHashMap<>();
  private Gauge info;
  private Collector collector;
  @Reference private volatile List<CamelContext> contexts;

  @Reference private CollectorRegistryService registryService;

  @Activate
  public void activate(ComponentContext cc) {
    collector = this.register(registryService.getRegistry());
    info =
        Gauge.build()
            .name("rahla_up")
            .labelNames("version")
            .help("Version of Rahla core.")
            .register(registryService.getRegistry());
    info.labels(cc.getBundleContext().getBundle().getVersion().toString()).set(1);
  }

  @Deactivate
  public void deactivate() {
    registryService.getRegistry().unregister(info);
    registryService.getRegistry().unregister(collector);
  }

  @Override
  public List<MetricFamilySamples> collect() {
    List<Collector.MetricFamilySamples> mfs = new ArrayList<>();
    GaugeMetricFamily start_timestamp =
        new GaugeMetricFamily(
            "camel_route_start_time",
            "Timestamp when the stats was initially started",
            Arrays.asList("context", "route"));
    GaugeMetricFamily reset_timestamp =
        new GaugeMetricFamily(
            "camel_route_reset_time",
            "Timestamp when the stats was last reset or initially started",
            Arrays.asList("context", "route"));
    CounterMetricFamily exchanges_total =
        new CounterMetricFamily(
            "camel_route_exchanges_total",
            "Total number of exchanges",
            Arrays.asList("context", "route"));
    CounterMetricFamily exchanges_completed =
        new CounterMetricFamily(
            "camel_route_exchanges_completed_total",
            "Number of completed exchanges",
            Arrays.asList("context", "route"));
    CounterMetricFamily exchanges_failed =
        new CounterMetricFamily(
            "camel_route_exchanges_failed_total",
            "Number of failed exchanges",
            Arrays.asList("context", "route"));
    CounterMetricFamily exchanges_inflight =
        new CounterMetricFamily(
            "camel_route_exchanges_inflight_total",
            "Number of inflight exchanges",
            Arrays.asList("context", "route"));
    CounterMetricFamily failures_handled =
        new CounterMetricFamily(
            "camel_route_failures_handled_total",
            "Number of failures handled",
            Arrays.asList("context", "route"));
    CounterMetricFamily external_redeliveries =
        new CounterMetricFamily(
            "camel_route_external_redeliveries_total",
            "Number of external initiated redeliveries (such as from JMS broker)",
            Arrays.asList("context", "route"));
    GaugeMetricFamily min_processing =
        new GaugeMetricFamily(
            "camel_route_min_processing_time",
            "Min Processing Time",
            Arrays.asList("context", "route"));
    GaugeMetricFamily mean_processing =
        new GaugeMetricFamily(
            "camel_route_mean_processing_time",
            "Mean Processing Time",
            Arrays.asList("context", "route"));
    GaugeMetricFamily max_processing =
        new GaugeMetricFamily(
            "camel_route_max_processing_time",
            "Max Processing Time",
            Arrays.asList("context", "route"));
    CounterMetricFamily total_processing =
        new CounterMetricFamily(
            "camel_route_total_processing_time",
            "Total Processing Time",
            Arrays.asList("context", "route"));
    GaugeMetricFamily last_processing =
        new GaugeMetricFamily(
            "camel_route_last_processing_time",
            "Last Processing Time",
            Arrays.asList("context", "route"));
    GaugeMetricFamily delta_processing =
        new GaugeMetricFamily(
            "camel_route_delta_processing_time",
            "Delta Processing Time",
            Arrays.asList("context", "route"));
    GaugeMetricFamily last_exchange_completed =
        new GaugeMetricFamily(
            "camel_route_last_exchange_completed_timestamp",
            "Last Exchange Completed Timestamp",
            Arrays.asList("context", "route"));
    GaugeMetricFamily first_exchange_completed =
        new GaugeMetricFamily(
            "camel_route_first_exchange_completed_timestamp",
            "First Exchange Completed Timestamp",
            Arrays.asList("context", "route"));
    GaugeMetricFamily last_exchange_failure =
        new GaugeMetricFamily(
            "camel_route_last_exchange_failure_timestamp",
            "Last Exchange Failed Timestamp",
            Arrays.asList("context", "route"));
    GaugeMetricFamily first_exchange_failure =
        new GaugeMetricFamily(
            "camel_route_first_exchange_failure_timestamp",
            "First Exchange Failed Timestamp",
            Arrays.asList("context", "route"));
    GaugeMetricFamily state =
        new GaugeMetricFamily(
            "camel_route_up",
            "Camel Route state 1=started, 0=else ",
            Arrays.asList("context", "route"));
    for (CamelContext camelContext : contexts) {
      List<Route> routes = camelContext.getRoutes();
      String contextName = camelContext.getName();
      for (Route route : routes) {
        ManagedCamelContext managed = camelContext.getExtension(ManagedCamelContext.class);
        ManagedRouteMBean mr = managed.getManagedRoute(route.getId(), ManagedRouteMBean.class);
        try {

          state.addMetric(
              Arrays.asList(contextName, route.getId()),
              ServiceStatus.Started.name().equals(mr.getState()) ? 1 : 0);

          Date startTimestamp = mr.getStartTimestamp();
          if (startTimestamp != null) {
            start_timestamp.addMetric(
                Arrays.asList(contextName, route.getId()), startTimestamp.getTime());
          }

          Date resetTimestamp = mr.getResetTimestamp();
          if (resetTimestamp != null) {
            start_timestamp.addMetric(
                Arrays.asList(contextName, route.getId()), resetTimestamp.getTime());
          }
          exchanges_total.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getExchangesTotal());
          exchanges_completed.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getExchangesCompleted());
          exchanges_failed.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getExchangesFailed());
          exchanges_inflight.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getExchangesInflight());
          failures_handled.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getFailuresHandled());
          external_redeliveries.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getExternalRedeliveries());
          min_processing.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getMinProcessingTime() / 1000.0d);
          mean_processing.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getMeanProcessingTime() / 1000.0d);
          max_processing.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getMaxProcessingTime() / 1000.0d);
          total_processing.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getTotalProcessingTime() / 1000.0d);
          last_processing.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getLastProcessingTime() / 1000.0d);
          delta_processing.addMetric(
              Arrays.asList(contextName, route.getId()), mr.getDeltaProcessingTime() / 1000.0d);

          Date lastExchangeCompletedTimestamp = mr.getLastExchangeCompletedTimestamp();
          if (lastExchangeCompletedTimestamp != null) {
            last_exchange_completed.addMetric(
                Arrays.asList(contextName, route.getId()),
                lastExchangeCompletedTimestamp.getTime());
          }

          Date firstExchangeCompletedTimestamp = mr.getFirstExchangeCompletedTimestamp();
          if (firstExchangeCompletedTimestamp != null) {
            first_exchange_completed.addMetric(
                Arrays.asList(contextName, route.getId()),
                firstExchangeCompletedTimestamp.getTime());
          }

          Date lastExchangeFailureTimestamp = mr.getLastExchangeFailureTimestamp();
          if (lastExchangeFailureTimestamp != null) {
            last_exchange_failure.addMetric(
                Arrays.asList(contextName, route.getId()), lastExchangeFailureTimestamp.getTime());
          }

          Date firstExchangeFailureTimestamp = mr.getFirstExchangeFailureTimestamp();
          if (firstExchangeFailureTimestamp != null) {
            first_exchange_failure.addMetric(
                Arrays.asList(contextName, route.getId()), firstExchangeFailureTimestamp.getTime());
          }
        } catch (Exception e) {
          log.error("Error During metric creation.", e);
        }
      }
      gauges.clear();
      counters.clear();
      try {
        MetricsRegistryService metricsRegistryService =
            camelContext.hasService(MetricsRegistryService.class);

        if (metricsRegistryService != null) {
          MetricRegistry metricsRegistry = metricsRegistryService.getMetricsRegistry();
          for (Entry<String, com.codahale.metrics.Gauge> e :
              metricsRegistry.getGauges().entrySet()) {
            try {
              double v = 0.0d;
              if (!String.valueOf(e.getValue().getValue()).isBlank()) {
                v = Double.parseDouble(String.valueOf(e.getValue().getValue()));
              }
              String metricName = e.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "_");
              GaugeMetricFamily gaugeMetricFamily =
                  gauges.computeIfAbsent(
                      metricName,
                      s ->
                          new GaugeMetricFamily(
                              s, "Custom Camel Gauge", Collections.singletonList("context")));
              gaugeMetricFamily.addMetric(Collections.singletonList(contextName), v);
            } catch (Exception ex) {
              log.warn("action=can not add gauge {}, reason={}", e.getKey(), ex.getMessage(), ex);
            }
          }
          for (Entry<String, Counter> e : metricsRegistry.getCounters().entrySet()) {
            String metricName = e.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "_");

            CounterMetricFamily counterMetricFamily =
                counters.computeIfAbsent(
                    metricName,
                    s ->
                        new CounterMetricFamily(
                            s, "Custom Camel Counter", Collections.singletonList("context")));
            counterMetricFamily.addMetric(
                Collections.singletonList(contextName), e.getValue().getCount());
          }
        }

      } catch (Exception e) {
        log.error("Error During metric creation.", e);
      }
    }
    mfs.add(exchanges_total);
    mfs.add(start_timestamp);
    mfs.add(reset_timestamp);
    mfs.add(exchanges_completed);
    mfs.add(exchanges_failed);
    mfs.add(exchanges_inflight);
    mfs.add(failures_handled);
    mfs.add(external_redeliveries);
    mfs.add(min_processing);
    mfs.add(mean_processing);
    mfs.add(max_processing);
    mfs.add(total_processing);
    mfs.add(last_processing);
    mfs.add(delta_processing);
    mfs.add(state);
    mfs.add(last_exchange_completed);
    mfs.add(first_exchange_completed);
    mfs.add(last_exchange_failure);
    mfs.add(first_exchange_failure);
    mfs.addAll(counters.values());
    mfs.addAll(gauges.values());
    return mfs;
  }
}
