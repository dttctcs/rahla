package rahla.jedissource;

import redis.clients.jedis.Jedis;

public interface JedisSource {

  Jedis getResource();

  void returnResource(Jedis jedis);

}
