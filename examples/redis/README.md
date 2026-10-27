# Redis without JedisSource

Replacement for `rahla.jedissource.JedisSource` (removed in Rahla 1.3.6): Jedis comes in through
`00-features.xml`, a Groovy class builds the pooled client from `example.redis.cfg`, and Blueprint
wires it as an ordinary bean. Tested on Rahla 1.3.6-SNAPSHOT (Karaf 4.4.12, Camel 4.18.4) against
Valkey 8.

| File | Purpose |
|------|---------|
| `00-features.xml` | Jedis 7.5.3 + its runtime deps (commons-pool2, gson, org.json, redis-authx-core) — all real OSGi bundles, no `wrap:` needed; plus `camel-blueprint` / `camel-jetty` for the demo REST endpoint |
| `RedisStore.groovy` | `example.redis.RedisStore`: builds a `redis.clients.jedis.RedisClient` (pooled) in `start()`, closes it in `close()` |
| `example.redis.cfg` | PID `example.redis`: `host`, `port`, `user`, `password`, `database`, `timeoutMillis`, `maxTotal` |
| `redis.xml` | `cm:property-placeholder` → `<bean class="example.redis.RedisStore" init-method="start" destroy-method="close">` → REST routes |

## Run

```sh
docker network create rahla-test
docker run -d --name valkey --network rahla-test docker.io/valkey/valkey:8-alpine
mkdir deploy && cp examples/redis/*.xml examples/redis/*.groovy examples/redis/*.cfg deploy/ && sed -i 's/^host=localhost/host=valkey/' deploy/example.redis.cfg
docker run -d --name rahla --network rahla-test -p 8285:8285 -v ./deploy:/config/deploy datatactics/rahla:1.3.6

curl -s localhost:8285/redis/ping                 # PONG
curl -s -XPOST localhost:8285/redis/counter/hits  # 1, 2, 3, …
curl -s localhost:8285/redis/counter/hits         # current value
```

## What was verified

* All five bundles `Active`, only one commons-pool2 / gson in the runtime.
* Editing `example.redis.cfg` (`database=1`) reloads the context (`update-strategy="reload"`); new
  keys land in the new database.
* Redis down → the call fails with HTTP 500 (`JedisConnectionException`), the context stays up and
  answers again as soon as Redis is back.
* Removing `redis.xml` calls `close()`: the pool's connections disappear from `CLIENT LIST`.

## Moving an existing JedisSource consumer

| before (≤ 1.3.5) | after |
|---|---|
| `rahla.jedissource-myJedisSource.cfg` with `host`, `port`, `db`, `user`, `pass`, `jedisSourceName` | `example.redis.cfg` (any PID you like) with `host`, `port`, `database`, `user`, `password` |
| `<reference id="myRedis" interface="rahla.jedissource.JedisSource" filter="(jedisSourceName=…)"/>` | `<bean id="myRedis" class="example.redis.RedisStore" init-method="start" destroy-method="close">` |
| `def jedis = myRedis.getResource(); try { jedis.get(k) } finally { myRedis.returnResource(jedis) }` | `myRedis.client.get(k)` — `RedisClient` borrows/returns pool connections itself |

`RedisClient` is the Jedis 7 successor of `JedisPooled`; every Redis command is a method on it.
Placeholders use `$[…]` so they do not collide with Camel's `${header.x}` in the same file.
