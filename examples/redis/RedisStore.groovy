package example.redis

import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.Connection
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.RedisClient

/**
 * Pooled Redis client as a plain Blueprint bean — the replacement for rahla.jedissource.JedisSource.
 * Properties are set from example.redis.cfg (see redis.xml); start()/close() are the Blueprint
 * init-/destroy-methods, so the pool lives exactly as long as the Blueprint context.
 *
 * start() does not contact Redis: the context comes up even while Redis is down, and each call
 * fails (and is retried by the route's error handling) until it is reachable.
 */
class RedisStore {
  String host = 'localhost'
  int port = 6379
  String user = ''
  String password = ''
  int database = 0
  int timeoutMillis = 2000
  int maxTotal = 8

  private RedisClient client

  void start() {
    def config = DefaultJedisClientConfig.builder().database(database).timeoutMillis(timeoutMillis)
    if (user) config.user(user)
    if (password) config.password(password)
    def pool = new GenericObjectPoolConfig<Connection>()
    pool.maxTotal = maxTotal
    client = RedisClient.builder()
        .hostAndPort(host, port)
        .clientConfig(config.build())
        .poolConfig(pool)
        .build()
  }

  void close() {
    client?.close()
    client = null
  }

  /** The client itself, for anything the helpers below do not cover. */
  RedisClient getClient() { client }

  String ping() { client.ping() }

  long incr(String key) { client.incr(key) }

  String get(String key) { client.get(key) }

  String set(String key, String value) { client.set(key, value) }
}
