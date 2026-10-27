package example.graph

import groovy.util.logging.Slf4j

import org.apache.tinkerpop.gremlin.driver.Client
import org.apache.tinkerpop.gremlin.driver.Cluster
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection
import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__
import org.apache.tinkerpop.gremlin.util.MessageSerializer

/**
 * Gremlin/JanusGraph client as a plain Blueprint bean — the replacement for
 * rahla.graphsource.GraphSource. Same settings as the old rahla.graphsource-<name>.cfg
 * (hosts, port, user, pass, graphTraversalSourceName, serializer, ioRegistries, pool sizes),
 * set from example.graph.cfg by graph.xml; start()/close() are Blueprint init-/destroy-methods.
 *
 * The serializer and the IoRegistry are created by class name. That only works because the
 * driver and janusgraph-driver share one classloader (host bundle tinkerpop.client + fragments,
 * see 00-features.xml), so we load them through the driver's classloader, not our own.
 */
@Slf4j
class GraphClient {
  String hosts = 'localhost'
  int port = 8182
  String user = ''
  String pass = ''
  String graphTraversalSourceName = 'g'
  String serializer = 'org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1'
  /** comma-separated IoRegistry class names, e.g. org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry */
  String ioRegistries = 'org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry'
  int minConnectionPoolSize = 2
  int maxConnectionPoolSize = 8
  int maxContentLength = 10 * 1024 * 1024
  int maxWaitForConnection = 3000

  private Cluster cluster
  private Client client

  /**
   * Blueprint init-method. Never fails the context: if the server is unreachable (or its host name
   * does not resolve yet), the error is logged and the connection is retried on first use.
   */
  void start() {
    try {
      ensureConnected()
    } catch (Exception e) {
      log.warn("Gremlin server {}:{} not reachable yet ({}), will retry on first use", hosts, port, e.message)
    }
  }

  private synchronized void ensureConnected() {
    if (client != null) return
    def hostList = hosts.split(',')*.trim().findAll { it }
    ClassLoader driverLoader = Cluster.classLoader
    MessageSerializer ser = (MessageSerializer) driverLoader.loadClass(serializer).getDeclaredConstructor().newInstance()
    def registries = ioRegistries.split(',')*.trim().findAll { it }
    if (registries) {
      ser.configure([ioRegistries: registries], null)
    }
    def builder = Cluster.build()
        .addContactPoints(hostList as String[])
        .port(port)
        .serializer(ser)
        .minConnectionPoolSize(minConnectionPoolSize)
        .maxConnectionPoolSize(maxConnectionPoolSize)
        .maxContentLength(maxContentLength)
        .maxWaitForConnection(maxWaitForConnection)
    if (pass) {
      builder.credentials(user, pass)
    }
    Cluster c = builder.create()   // resolves the host names, fails if they do not resolve
    cluster = c
    client = c.connect()
    log.info("Connected Gremlin client to {}:{}", hostList, port)
  }

  synchronized void close() {
    try { client?.close() } finally { cluster?.close(); client = null; cluster = null }
  }

  Client getClient() {
    ensureConnected()
    client
  }

  /** A remote traversal source; create one per use, it is cheap. */
  GraphTraversalSource g() {
    AnonymousTraversalSource.traversal().withRemote(DriverRemoteConnection.using(getClient(), graphTraversalSourceName))
  }

  long countVertices() { g().V().count().next() }

  Object addPerson(String name) {
    g().addV('person').property('name', name).id().next()
  }

  List<String> names() { g().V().hasLabel('person').values('name').toList() as List<String> }

  /**
   * Adds a 'knows' edge and returns its id. JanusGraph edge ids are RelationIdentifier objects,
   * which only deserialize when JanusGraphIoRegistry is configured — a quick end-to-end check that
   * the serializer setup works.
   */
  String connect(String from, String to) {
    def g = g()
    def a = g.V().has('person', 'name', from).next()
    def b = g.V().has('person', 'name', to).next()
    def id = g.V(a).addE('knows').to(__.V(b)).id().next()
    "${id.getClass().simpleName}:${id}"
  }
}
