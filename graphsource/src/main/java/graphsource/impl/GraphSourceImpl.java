package rahla.graphsource.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;

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
  private static final String GRAPHSOURCENAME_KEY = "graphSourceName";
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
  public synchronized void activate(ComponentContext cc)
          throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
          InvocationTargetException, InstantiationException, JsonProcessingException {
    Map<String, Object> properties = Collections.list(cc.getProperties().keys()).stream()
            .collect(Collectors.toMap(Function.identity(), cc.getProperties()::get));

    String hosts = (String) properties.get(HOSTS_KEY);
    String[] hostss = hosts.split(",");

    log.info("Starting Janusgraph client...");

    String host = hostss[0];

    Integer port = Integer.parseInt((String) properties.get(PORT_KEY));
    user = (String) properties.get(USER_KEY);
    String pass = (String) properties.get(PASS_KEY);
    graphTraversalSourceName = (String) properties.get(GRAPHTRAVERSALSOURCENAME_KEY);

    Cluster.Builder builder = Cluster.build(host).port(port);

    String serializerClassName = (String) properties.getOrDefault(SERIALIZER_KEY, "org.apache.tinkerpop.gremlin.driver.ser.GryoMessageSerializerV3d0");

    Class clazz =
            cc.getBundleContext()
                    .getBundle()
                    .adapt(BundleWiring.class)
                    .getClassLoader()
                    .loadClass(serializerClassName);

    String serializerConfigJson = (String) properties.get(SERIALIZERCONFIG_KEY);

    Map serilaizerConfig = new ObjectMapper().readValue(serializerConfigJson, Map.class);

    MessageSerializer serializer = (MessageSerializer) clazz.getDeclaredConstructor().newInstance();
    Optional.ofNullable(serilaizerConfig)
            .ifPresent(
                    (c) -> {
                      serializer.configure(c, null);
                    });

    int maxContentLength = Integer.parseInt((String) properties.getOrDefault(MAXCONTENTLENGTH_KEY, "65536"));
    int minConnectionPoolSize = Integer.parseInt((String) properties.getOrDefault(MINCONNECTIONPOOLSIZE_KEY, "32"));
    int maxConnectionPoolSize = Integer.parseInt((String) properties.getOrDefault(MAXCONNECTIONPOOLSIZE_KEY, "32"));
    int minSimultaneousUsagePerConnection = Integer.parseInt((String) properties.getOrDefault(MINSIMULTANEOUSUSAGEPERCONNECTION_KEY, "16"));
    int maxSimultaneousUsagePerConnection = Integer.parseInt((String) properties.getOrDefault(MAXSIMULTANEOUSUSAGEPERCONNECTION_KEY, "16"));
    int minInProcessPerConnection = Integer.parseInt((String) properties.getOrDefault(MININPROCESSPERCONNECTION_KEY, "16"));
    int maxInProcessPerConnection = Integer.parseInt((String) properties.getOrDefault(MAXINPROCESSPERCONNECTION_KEY, "16"));
    int maxWaitForConnection = Integer.parseInt((String) properties.getOrDefault(MAXWAITFORCONNECTION_KEY, "3000"));
    int nioPoolSize = Integer.parseInt((String) properties.getOrDefault(NIOPOOLSIZE_KEY, "" + Runtime.getRuntime().availableProcessors()));
    int workerPoolSize = Integer.parseInt((String) properties.getOrDefault(WORKERPOOLSIZE_KEY, "" + Runtime.getRuntime().availableProcessors() * 2));
    builder
            .serializer(serializer)
            .maxContentLength(maxContentLength)
            .minConnectionPoolSize(minConnectionPoolSize)
            .maxConnectionPoolSize(maxConnectionPoolSize)
            .minSimultaneousUsagePerConnection(minSimultaneousUsagePerConnection)
            .maxSimultaneousUsagePerConnection(maxSimultaneousUsagePerConnection)
            .minInProcessPerConnection(minInProcessPerConnection)
            .maxInProcessPerConnection(maxInProcessPerConnection)
            .nioPoolSize(nioPoolSize)
            .maxWaitForConnection(maxWaitForConnection)
            .workerPoolSize(workerPoolSize)
            .create();

    for (int i = 1; i < hostss.length; i++) {
      String hostname = hostss[i].trim();
      if (!hostname.isEmpty()) {
        builder.addContactPoint(hostname);
      }
    }
    if (pass != null && !pass.isBlank()) {
      builder.credentials(user, pass);
    }
    cluster = builder.create();

    client = cluster.connect();
    log.info("Started Janusgraph client for: " + cluster.toString());
  }

  @Deactivate
  public synchronized void deactivate(ComponentContext cc) {
    log.info("Stopping Janusgraph client...");
    try {
      client.close();
      cluster.close();
    } catch (Exception e) {
      log.error("Error closing graph traversal source", e);
    }
    log.info("Stopped Janusgraph client for: " + cluster.toString());

  }

  @Override
  public GraphTraversalSource getGraphTraversalSource() {
    return traversal().withRemote(DriverRemoteConnection.using(client, graphTraversalSourceName));
  }
}