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

  private JedisPool jedisPool;
  private String host;
  private int port;
  private int db;
  private String pass;
  private boolean shutdown = false;
  private String user;

  private int maxTotal = 256;
  private int maxIdle = 256;
  private int minIdle = 16;
  private boolean testOnBorrow = true;
  private boolean testOnReturn = true;
  private boolean testWhileIdle = true;
  private int minEvictableIdleTimeSeconds = 60;
  private int timeBetweenEvictionRunsSeconds = 30;
  private int numTestsPerEvictionRun = 3;
  private boolean blockWhenExhausted = true;


  @Activate
  public void activate(ComponentContext cc) {
    Map<String, Object> properties = Collections.list(cc.getProperties().keys()).stream()
            .collect(Collectors.toMap(Function.identity(), cc.getProperties()::get));

    host = (String) properties.getOrDefault(HOST_KEY, "65536");
    port = Integer.parseInt((String) properties.getOrDefault(PORT_KEY, "6379"));
    db = Integer.parseInt((String) properties.getOrDefault(DB_KEY, "0"));
    user = (String) properties.getOrDefault(USER_KEY, null);
    pass = (String) properties.getOrDefault(PASS_KEY, null);
    //TODO Make pool config configuratble
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
    jedisPool = new JedisPool(buildPoolConfig(), host, port, 2000, user, pass, db);

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
