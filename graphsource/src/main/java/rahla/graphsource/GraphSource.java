package rahla.graphsource;

import java.util.List;

/**
 * OSGi service representing a connection to a TinkerPop-compatible graph database.
 * <p>
 * Each instance is bound to a {@code rahla.graphsource-<name>.cfg} configuration; consumers
 * select a specific source by filtering on the {@code graphSourceName} service property
 * (e.g. {@code <reference filter="(graphSourceName=foo)" .../>}).
 *
 * @param <T> the graph traversal source type (typically
 *            {@link org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource})
 * @param <V> the underlying client type (typically
 *            {@link org.apache.tinkerpop.gremlin.driver.Client})
 */
public interface GraphSource<T, V> {

  /** @return the configured Gremlin server hosts (one or more). */
  List<String> getHosts();

  /** @return the configured user name, or {@code null} if anonymous. */
  String getUser();

  /** @return the underlying driver client. */
  V getClient();

  /**
   * @return a graph traversal source bound to the configured
   *         {@code graphTraversalSourceName}; safe to call multiple times.
   */
  T getGraphTraversalSource();
}