package rahla.jedissource.impl;

import lombok.extern.log4j.Log4j2;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import rahla.jedissource.JedisSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;
import java.util.Dictionary;

/**
 * Configuration-driven {@link JedisSource} backed by a {@link JedisPool}.
 * <p>
 * One service instance is registered per {@code rahla.jedissource-<name>.cfg} dropped
 * into the deploy directory. Recognised configuration keys (with defaults):
 * <ul>
 *   <li>{@code host} (default {@code localhost}), {@code port} ({@code 6379}),
 *       {@code db} ({@code 0}), {@code user}, {@code pass}</li>
 *   <li>{@code timeout} ({@code 2000} ms)</li>
 *   <li>pool sizing: {@code maxTotal} ({@code 256}), {@code maxIdle} ({@code 256}),
 *       {@code minIdle} ({@code 16})</li>
 *   <li>health checks: {@code testOnBorrow}, {@code testOnReturn}, {@code testWhileIdle}
 *       (all {@code true} by default)</li>
 *   <li>eviction: {@code minEvictableIdleTimeSeconds} ({@code 60}),
 *       {@code timeBetweenEvictionRunsSeconds} ({@code 30}),
 *       {@code numTestsPerEvictionRun} ({@code 3})</li>
 *   <li>{@code blockWhenExhausted} ({@code true})</li>
 * </ul>
 *
 * @deprecated end-of-life; see {@link JedisSource}.
 */
@Log4j2
@Deprecated
@Component(
        configurationPid = "rahla.jedissource",
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true)
public class JedisServiceImpl implements JedisSource {
  private static final String HOST_KEY = "host";
  private static final String PORT_KEY = "port";
  private static final String DB_KEY = "db";
  private static final String USER_KEY = "user";
  private static final String PASS_KEY = "pass";
  private static final String TIMEOUT_KEY = "timeout";
  private static final String MAX_TOTAL_KEY = "maxTotal";
  private static final String MAX_IDLE_KEY = "maxIdle";
  private static final String MIN_IDLE_KEY = "minIdle";
  private static final String TEST_ON_BORROW_KEY = "testOnBorrow";
  private static final String TEST_ON_RETURN_KEY = "testOnReturn";
  private static final String TEST_WHILE_IDLE_KEY = "testWhileIdle";
  private static final String MIN_EVICTABLE_IDLE_TIME_SECONDS_KEY = "minEvictableIdleTimeSeconds";
  private static final String TIME_BETWEEN_EVICTION_RUNS_SECONDS_KEY = "timeBetweenEvictionRunsSeconds";
  private static final String NUM_TESTS_PER_EVICTION_RUN_KEY = "numTestsPerEvictionRun";
  private static final String BLOCK_WHEN_EXHAUSTED_KEY = "blockWhenExhausted";

  private static final int CONNECT_RETRY_COUNT = 120;
  private static final long CONNECT_RETRY_DELAY_MS = 1000L;

  private volatile JedisPool jedisPool;
  private volatile boolean shutdown = false;

  private String host;
  private int port;
  private int db;
  private String user;
  private String pass;
  private int timeout;

  private int maxTotal;
  private int maxIdle;
  private int minIdle;
  private boolean testOnBorrow;
  private boolean testOnReturn;
  private boolean testWhileIdle;
  private int minEvictableIdleTimeSeconds;
  private int timeBetweenEvictionRunsSeconds;
  private int numTestsPerEvictionRun;
  private boolean blockWhenExhausted;

  @Activate
  public void activate(ComponentContext cc) {
    log.warn("JedisSource is EOL and will be removed in one of the next releases. " +
            "We recommend migrating to your own bean handling the connection pool.");
    Dictionary<String, Object> properties = cc.getProperties();

    host = stringProp(properties, HOST_KEY, "localhost");
    port = intProp(properties, PORT_KEY, 6379);
    db = intProp(properties, DB_KEY, 0);
    user = stringProp(properties, USER_KEY, null);
    pass = stringProp(properties, PASS_KEY, null);
    timeout = intProp(properties, TIMEOUT_KEY, 2000);
    maxTotal = intProp(properties, MAX_TOTAL_KEY, 256);
    maxIdle = intProp(properties, MAX_IDLE_KEY, 256);
    minIdle = intProp(properties, MIN_IDLE_KEY, 16);
    testOnBorrow = boolProp(properties, TEST_ON_BORROW_KEY, true);
    testOnReturn = boolProp(properties, TEST_ON_RETURN_KEY, true);
    testWhileIdle = boolProp(properties, TEST_WHILE_IDLE_KEY, true);
    minEvictableIdleTimeSeconds = intProp(properties, MIN_EVICTABLE_IDLE_TIME_SECONDS_KEY, 60);
    timeBetweenEvictionRunsSeconds = intProp(properties, TIME_BETWEEN_EVICTION_RUNS_SECONDS_KEY, 30);
    numTestsPerEvictionRun = intProp(properties, NUM_TESTS_PER_EVICTION_RUN_KEY, 3);
    blockWhenExhausted = boolProp(properties, BLOCK_WHEN_EXHAUSTED_KEY, true);
    init();
  }

  private void init() {
    closePoolQuietly();
    jedisPool = new JedisPool(buildPoolConfig(), host, port, timeout, user, pass, db);

    for (int i = 0; i < CONNECT_RETRY_COUNT && !shutdown; i++) {
      try (Jedis resource = jedisPool.getResource()) {
        if (resource.isConnected()) {
          return;
        }
      } catch (Exception e) {
        log.warn("Jedis connect is waiting, reason={}", e.getMessage());
      }
      try {
        Thread.sleep(CONNECT_RETRY_DELAY_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  @Deactivate
  public void deactivate(ComponentContext cc) {
    shutdown = true;
    closePoolQuietly();
  }

  private void closePoolQuietly() {
    if (jedisPool != null) {
      try {
        jedisPool.close();
      } catch (Exception e) {
        log.error("action=close jedis pool, reason={}", e.getMessage());
      }
    }
  }

  private JedisPoolConfig buildPoolConfig() {
    final JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(maxTotal);
    poolConfig.setMaxIdle(maxIdle);
    poolConfig.setMinIdle(minIdle);
    poolConfig.setTestOnBorrow(testOnBorrow);
    poolConfig.setTestOnReturn(testOnReturn);
    poolConfig.setTestWhileIdle(testWhileIdle);
    poolConfig.setMinEvictableIdleTime(Duration.ofSeconds(minEvictableIdleTimeSeconds));
    poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(timeBetweenEvictionRunsSeconds));
    poolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    poolConfig.setBlockWhenExhausted(blockWhenExhausted);
    return poolConfig;
  }

  @Override
  public void returnResource(Jedis jedis) {
    jedisPool.returnResource(jedis);
  }

  @Override
  public Jedis getResource() {
    try {
      return jedisPool.getResource();
    } catch (JedisException e) {
      log.warn("Jedis pool exhausted/disconnected, reinitialising", e);
      init();
      return jedisPool.getResource();
    }
  }

  private static String stringProp(Dictionary<String, Object> properties, String key, String defaultValue) {
    Object raw = properties.get(key);
    return raw == null ? defaultValue : raw.toString();
  }

  private static int intProp(Dictionary<String, Object> properties, String key, int defaultValue) {
    Object raw = properties.get(key);
    return raw == null ? defaultValue : Integer.parseInt(raw.toString());
  }

  private static boolean boolProp(Dictionary<String, Object> properties, String key, boolean defaultValue) {
    Object raw = properties.get(key);
    return raw == null ? defaultValue : Boolean.parseBoolean(raw.toString());
  }
}
