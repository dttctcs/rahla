/*
 * MIT License
 *
 * Copyright © 2020 Matthias Leinweber datatactics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package rahla.extensions;

import lombok.extern.log4j.Log4j2;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import rahla.api.JedisSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.io.BufferedReader;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Component(
        configurationPid = "rahla.jedissource",
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true)
public class JedisServiceImpl implements JedisSource {
  private static final String PORT_KEY = "port";
  private static final String HOST_KEY = "host";
  private static final String DB_KEY = "db";
  private static final String PASS_KEY = "pass";
  private static final String USER_KEY = "user";
  private static final String TIMEOUT = "timeout";
  private static final String MAX_TOTAL = "maxTotal";
  private static final String MAX_IDLE = "maxIdle";
  private static final String MIN_IDLE = "minIdle";
  private static final String TEST_ON_BURROW = "testOnBorrow";
  private static final String TEST_ON_RETURN = "testOnReturn";
  private static final String TEST_WHILE_IDLE = "testWhileIdle";
  private static final String MIN_EVICTABLE_IDLE_TIME_SECONDS = "minEvictableIdleTimeSeconds";
  private static final String TIME_BETWEEN_EVICTION_RUNS_SECONDS = "timeBetweenEvictionRunsSeconds";
  private static final String NUM_TESTS_PER_EVICTION_RUN = "numTestsPerEvictionRun";
  private static final String BLOCK_WHEN_EXHAUSTED = "blockWhenExhausted";

  private JedisPool jedisPool;
  private String host;
  private int port;
  private int db;
  private String pass;
  private boolean shutdown = false;
  private String user;
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
    Map<String, Object> properties = Collections.list(cc.getProperties().keys()).stream()
            .collect(Collectors.toMap(Function.identity(), cc.getProperties()::get));

    host = (String) properties.getOrDefault(HOST_KEY, "65536");
    port = Integer.parseInt((String) properties.getOrDefault(PORT_KEY, "6379"));
    db = Integer.parseInt((String) properties.getOrDefault(DB_KEY, "0"));
    user = (String) properties.getOrDefault(USER_KEY, null);
    pass = (String) properties.getOrDefault(PASS_KEY, null);
    timeout = Integer.parseInt((String) properties.getOrDefault(TIMEOUT, "2000"));
    maxTotal = Integer.parseInt((String) properties.getOrDefault(MAX_TOTAL, "256"));
    maxIdle = Integer.parseInt((String) properties.getOrDefault(MAX_IDLE, "256"));
    minIdle = Integer.parseInt((String) properties.getOrDefault(MIN_IDLE, "16"));
    testOnBorrow = Boolean.parseBoolean((String) properties.getOrDefault(TEST_ON_BURROW, "true"));
    testOnReturn = Boolean.parseBoolean((String) properties.getOrDefault(TEST_ON_RETURN, "true"));
    testWhileIdle = Boolean.parseBoolean((String) properties.getOrDefault(TEST_WHILE_IDLE, "true"));
    minEvictableIdleTimeSeconds = Integer.parseInt((String) properties.getOrDefault(MIN_EVICTABLE_IDLE_TIME_SECONDS, "60"));
    timeBetweenEvictionRunsSeconds = Integer.parseInt((String) properties.getOrDefault(TIME_BETWEEN_EVICTION_RUNS_SECONDS, "30"));
    numTestsPerEvictionRun = Integer.parseInt((String) properties.getOrDefault(NUM_TESTS_PER_EVICTION_RUN, "3"));
    blockWhenExhausted = Boolean.parseBoolean((String) properties.getOrDefault(BLOCK_WHEN_EXHAUSTED, "true"));
    init();
  }

  public void init() {
    if (jedisPool != null) {

      try {
        jedisPool.close();
      } catch (Exception e) {
        log.error("action=close jedis pool, reason={}", e.getMessage());
      }
    }
    jedisPool = new JedisPool(buildPoolConfig(), host, port, timeout, user, pass, db);

    for (int i = 0; i < 120; i++) {
      try (Jedis resource = jedisPool.getResource()) {
        if (resource.isConnected()) {
          break;
        }
      } catch (Exception e) {
        log.warn("action=init jedis waiting, reason={}", e.getMessage());
      }
      if (shutdown) {
        break;
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ignored) {
      }
    }
  }

  @Deactivate
  public void deactivate(ComponentContext cc) {
    shutdown = true;
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
      Jedis resource = jedisPool.getResource();
      return resource;
    } catch (JedisException e) {
      try {
        init();
        Jedis resource = jedisPool.getResource();
        return resource;
      } catch (Exception ex) {
        throw ex;
      }
    }
  }

}
