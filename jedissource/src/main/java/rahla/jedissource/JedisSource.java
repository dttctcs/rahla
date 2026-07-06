package rahla.jedissource;

import redis.clients.jedis.Jedis;

/**
 * OSGi service exposing a pooled Redis (Jedis) connection.
 * <p>
 * Each instance is bound to a {@code rahla.jedissource-<name>.cfg} configuration; consumers
 * select a specific source by filtering on the {@code jedisSourceName} service property.
 *
 * @deprecated The component is end-of-life and will be removed in a future release.
 *             Maintain your own connection-pool bean instead.
 */
@Deprecated
public interface JedisSource {

  /**
   * Borrows a {@link Jedis} resource from the pool. The returned instance is also
   * {@link AutoCloseable}, so prefer try-with-resources over {@link #returnResource(Jedis)}.
   *
   * @return a connected {@link Jedis} instance
   */
  Jedis getResource();

  /**
   * @deprecated calling {@link Jedis#close()} (e.g. via try-with-resources) is preferred
   *             and returns the connection to the pool automatically.
   */
  @Deprecated
  void returnResource(Jedis jedis);

}
