package rahla.graphsource.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.util.MessageSerializer;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;

/**
 * Configuration-driven {@link rahla.graphsource.GraphSource} backed by a TinkerPop
 * Gremlin {@link Cluster}.
 * <p>
 * One service instance is registered per {@code rahla.graphsource-<name>.cfg} dropped
 * into the deploy directory. Recognised configuration keys:
 * <ul>
 *   <li>{@code hosts} (required) — comma-separated list of host names</li>
 *   <li>{@code port} (required) — Gremlin server port</li>
 *   <li>{@code user}, {@code pass} — credentials (optional)</li>
 *   <li>{@code graphTraversalSourceName} — name of the remote traversal source</li>
 *   <li>{@code serializer}, {@code serializerConfig} — message serializer FQCN and JSON config</li>
 *   <li>connection-pool tuning: {@code minConnectionPoolSize}, {@code maxConnectionPoolSize},
 *       {@code minInProcessPerConnection}, {@code maxInProcessPerConnection},
 *       {@code minSimultaneousUsagePerConnection}, {@code maxSimultaneousUsagePerConnection},
 *       {@code maxWaitForConnection}, {@code maxContentLength},
 *       {@code nioPoolSize}, {@code workerPoolSize}</li>
 * </ul>
 */
@Log4j2
@Component(
        configurationPid = "rahla.graphsource",
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true)
public class GraphSourceImpl implements rahla.graphsource.GraphSource<GraphTraversalSource, Client> {

  private static final String HOSTS_KEY = "hosts";
  private static final String PORT_KEY = "port";
  private static final String USER_KEY = "user";
  private static final String PASS_KEY = "pass";
  private static final String GRAPHTRAVERSALSOURCENAME_KEY = "graphTraversalSourceName";
  private static final String SERIALIZER_KEY = "serializer";
  private static final String SERIALIZERCONFIG_KEY = "serializerConfig";
  private static final String NIOPOOLSIZE_KEY = "nioPoolSize";
  private static final String WORKERPOOLSIZE_KEY = "workerPoolSize";
  private static final String MINCONNECTIONPOOLSIZE_KEY = "minConnectionPoolSize";
  private static final String MAXCONNECTIONPOOLSIZE_KEY = "maxConnectionPoolSize";
  private static final String MININPROCESSPERCONNECTION_KEY = "minInProcessPerConnection";
  private static final String MAXINPROCESSPERCONNECTION_KEY = "maxInProcessPerConnection";
  private static final String MINSIMULTANEOUSUSAGEPERCONNECTION_KEY = "minSimultaneousUsagePerConnection";
  private static final String MAXSIMULTANEOUSUSAGEPERCONNECTION_KEY = "maxSimultaneousUsagePerConnection";
  private static final String MAXWAITFORCONNECTION_KEY = "maxWaitForConnection";
  private static final String MAXCONTENTLENGTH_KEY = "maxContentLength";
  private static final String DEFAULT_SERIALIZER =
          "org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1";

  private Cluster cluster;
  private Client client;
  private String graphTraversalSourceName;
  private List<String> hosts;
  private String user;

  @Override
  public List<String> getHosts() {
    return hosts;
  }

  @Override
  public String getUser() {
    return user;
  }

  @Override
  public Client getClient() {
    return client;
  }

  @Activate
  public synchronized void activate(ComponentContext cc) throws ReflectiveOperationException, java.io.IOException {
    Map<String, Object> properties = readProperties(cc);

    String hostsRaw = (String) properties.get(HOSTS_KEY);
    if (hostsRaw == null || hostsRaw.isBlank()) {
      throw new IllegalArgumentException("Required property '" + HOSTS_KEY + "' is missing");
    }
    hosts = Arrays.stream(hostsRaw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

    int port = Integer.parseInt((String) properties.get(PORT_KEY));
    user = (String) properties.get(USER_KEY);
    String pass = (String) properties.get(PASS_KEY);
    graphTraversalSourceName = (String) properties.get(GRAPHTRAVERSALSOURCENAME_KEY);

    log.info("Starting Janusgraph client for hosts={} port={}", hosts, port);

    MessageSerializer<?> serializer = buildSerializer(cc, properties);

    Cluster.Builder builder = Cluster.build(hosts.get(0))
            .port(port)
            .serializer(serializer)
            .maxContentLength(intProp(properties, MAXCONTENTLENGTH_KEY, 65536))
            .minConnectionPoolSize(intProp(properties, MINCONNECTIONPOOLSIZE_KEY, 32))
            .maxConnectionPoolSize(intProp(properties, MAXCONNECTIONPOOLSIZE_KEY, 32))
            .minSimultaneousUsagePerConnection(intProp(properties, MINSIMULTANEOUSUSAGEPERCONNECTION_KEY, 16))
            .maxSimultaneousUsagePerConnection(intProp(properties, MAXSIMULTANEOUSUSAGEPERCONNECTION_KEY, 16))
            .minInProcessPerConnection(intProp(properties, MININPROCESSPERCONNECTION_KEY, 16))
            .maxInProcessPerConnection(intProp(properties, MAXINPROCESSPERCONNECTION_KEY, 16))
            .nioPoolSize(intProp(properties, NIOPOOLSIZE_KEY, Runtime.getRuntime().availableProcessors()))
            .maxWaitForConnection(intProp(properties, MAXWAITFORCONNECTION_KEY, 3000))
            .workerPoolSize(intProp(properties, WORKERPOOLSIZE_KEY, Runtime.getRuntime().availableProcessors() * 2));

    for (int i = 1; i < hosts.size(); i++) {
      builder.addContactPoint(hosts.get(i));
    }
    if (pass != null && !pass.isBlank()) {
      builder.credentials(user, pass);
    }
    cluster = builder.create();
    client = cluster.connect();
    log.info("Started Janusgraph client for: {}", cluster);
  }

  @Deactivate
  public synchronized void deactivate(ComponentContext cc) {
    log.info("Stopping Janusgraph client...");
    try {
      if (client != null) {
        client.close();
      }
    } catch (Exception e) {
      log.error("Error closing Janusgraph client", e);
    }
    try {
      if (cluster != null) {
        cluster.close();
      }
    } catch (Exception e) {
      log.error("Error closing Janusgraph cluster", e);
    }
    log.info("Stopped Janusgraph client");
  }

  @Override
  public GraphTraversalSource getGraphTraversalSource() {
    return traversal().withRemote(DriverRemoteConnection.using(client, graphTraversalSourceName));
  }

  private static Map<String, Object> readProperties(ComponentContext cc) {
    Map<String, Object> properties = new java.util.HashMap<>();
    var keys = cc.getProperties().keys();
    while (keys.hasMoreElements()) {
      String k = keys.nextElement();
      properties.put(k, cc.getProperties().get(k));
    }
    return properties;
  }

  private static int intProp(Map<String, Object> properties, String key, int defaultValue) {
    Object raw = properties.get(key);
    return raw == null ? defaultValue : Integer.parseInt(raw.toString());
  }

  private static MessageSerializer<?> buildSerializer(ComponentContext cc, Map<String, Object> properties)
          throws ReflectiveOperationException, java.io.IOException {
    String serializerClassName = (String) properties.getOrDefault(SERIALIZER_KEY, DEFAULT_SERIALIZER);
    Class<?> clazz = cc.getBundleContext()
            .getBundle()
            .adapt(BundleWiring.class)
            .getClassLoader()
            .loadClass(serializerClassName);
    MessageSerializer<?> serializer =
            (MessageSerializer<?>) clazz.getDeclaredConstructor().newInstance();

    String serializerConfigJson = (String) properties.get(SERIALIZERCONFIG_KEY);
    if (serializerConfigJson != null && !serializerConfigJson.isBlank()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> serializerConfig =
              new ObjectMapper().readValue(serializerConfigJson, Map.class);
      // Second arg (Map<String, Graph>) is unused for our configurations.
      serializer.configure(serializerConfig, null);
    }
    return serializer;
  }
}
