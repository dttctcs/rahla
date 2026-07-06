# 1.3.4 (unreleased)

### Dependency Updates

* Jedis: `7.5.2 > 7.5.3`
* Log4j2: `2.26.0 > 2.26.1`

# 1.3.3 (2026-06-10)

### Improvements

* Custom CA certificates: drop PEM/`.crt` files into `/config/certs`; the new `init-rahla-certs` s6 service imports them into the JVM truststore before Karaf starts (idempotent, alias derived from the certificate CN) — no hand-built JKS needed
* Container pins `org.ops4j.pax.url.mvn.localRepository` to `/config/.m2` (pre-created, owned by `abc`) so feature-install downloads use a fixed, persistent path instead of an ambiguous `~/.m2`; set in the `Dockerfile` only, so local non-container runs are unaffected
* Renovate: only minor/patch updates are PR'd (no major); the Temurin JRE version is monitored via a customManager (Dependency Dashboard, approval-gated); Siddhi (EOL) and `wagon-ssh-external` are ignored

### Dependency Updates

* JRE (Temurin): `jdk-21.0.10+7 > jdk-21.0.11+10`
* opentelemetry agent: `2.22.0 > 2.26.1`
* Apache Camel: `4.18.1 > 4.18.2` (held at 4.18.x — the Karaf `apache-camel` features the assembly needs are not published for 4.20.x)
* Groovy: `4.0.30 > 4.0.32`
* Jackson: `2.21.3 > 2.22.0`
* Log4j2: `2.25.4 > 2.26.0`
* pax-logging: `2.3.3 > 2.3.4`
* Woodstox stax2-api: `4.2.2 > 4.3.0`
* commons-collections4: `4.4 > 4.5.0`
* commons-configuration2: `2.13.0 > 2.15.1`
* TinkerPop/Gremlin: `3.7.3 > 3.8.1`
* Jedis: `7.5.0 > 7.5.2`
* OpenTelemetry SDK extension-autoconfigure: `1.61.0 > 1.63.0`
* Agrona (loki-appender): `1.12.0 > 1.23.1`
* Maven plugins: compiler `3.14.0 > 3.15.0`, resources `3.3.0 > 3.5.0`, deploy `3.1.0 > 3.1.4`, install `3.1.0 > 3.1.4`
* Not pulled: major updates (per Renovate policy); `janusgraph-driver` stays at `1.1.0` (the only newer version offered is a timestamped build, not a clean release)

### Bug Fixes

* `JedisServiceImpl`: the SSL-support change used `Dictionary.getOrDefault(...)`, which does not compile (method is on `Map`, not `Dictionary`); now uses the existing `boolProp()` helper — `main` did not build before this
* `Dockerfile`: `KARAF_SYSTEM_OPTS` still referenced `opentelemetry-javaagent-2.22.0.jar` while the assembly ships `2.26.1`, so the `-javaagent` path did not exist and the JVM failed to boot — synced to `2.26.1`

### Removed

* KAR-based offline packaging (`manifests/feature-kar.xml`). Offline images now prime Karaf's OSGi cache at build time (boot once → install → ship the cache) instead of resolving a KAR closure.

# 1.3.2 (2026-05-02)

### Bug Fixes

* `etc/org.ops4j.pax.url.mvn.cfg`: fixed empty repository entry that broke boot-feature resolution on Karaf 4.4.11. The bundled `pax-url-aether` parser strips the leading `+` flag from `org.ops4j.pax.url.mvn.repositories`; the previous ` +,` value left a stray comma, so the split produced an empty first entry which the (now stricter) `MavenRepositoryURL` constructor rejects with `Repository spec is empty string.` The value is now `+ ` (no comma), preserving the "include settings.xml profile repos" semantics.

### Dependency Updates

* karaf: `4.4.9 > 4.4.11`

# 1.3.1 (2026-04-29)

### Bug Fixes

* `JedisServiceImpl`: fixed broken default for `host` (was `"65536"` — copy-paste from `maxContentLength`) → `localhost`
* `JedisServiceImpl`: connect-retry loop no longer swallows `InterruptedException`; the interrupt flag is restored
* `GraphSourceImpl`: stopped leaking a `Cluster` instance built twice during activate
* `GraphSourceImpl`: `deactivate` no longer NPEs when activate failed before the cluster/client were created
* `GroovyBeanFactoryImpl`: `InputStream` is now closed via try-with-resources, replaced deprecated `new URL(String)` with `URI.create(...).toURL()`
* `OsgiRouteTemplateParameterSource`: malformed `camel.route.template.*` keys are skipped instead of throwing `StringIndexOutOfBoundsException`
* `TemplateFileInstaller`: fixed broken log messages (`BUG():`, `template{}`); YAML parser is now closed

### Improvements

* Public APIs (`GroovyBeanFactory`, `GraphSource`, `JedisSource`) and component impls now have Javadoc describing accepted URLs, factory PIDs and config keys
* Replaced raw types and string-concatenated log messages with parameterised logging
* `JedisSource` and `JedisServiceImpl` are marked `@Deprecated` (already EOL per runtime warning)

### Build / POM cleanup

* Parent `pom.xml`: removed duplicate plugin-version property block, dead properties (`java.version`, `javax.ws.rs-api.version`, `opentelemetry.version`, `org.osgi.service.jpa.version`, `org.osgi.util.function.version`) and dead `org.codehaus.groovy` `indy` entries (Groovy 4 ships under `org.apache.groovy` without `indy` classifier); regrouped properties by topic
* Parent `pom.xml`: `disruptor` dependency now uses `${disruptor.version}` instead of a hardcoded version
* `assembly/pom.xml`: removed duplicate `mvn:io.undertow/undertow-core/2.2.37.Final` entry from `<blacklistedBundles>`
* `rahla/pom.xml`: dropped ~150 lines of stale commented-out Groovy embedding configuration; consolidated `groovy-*` dependencies and normalised tag order

### Dependency Updates

* camel: `4.10.7 > 4.18.1`
* jackson2: `2.21.0 > 2.21.3`
* pax-logging: `2.3.2 > 2.3.3`
* lombok: `1.18.42 > 1.18.46`
* log4j2: `2.25.3 > 2.25.4`
* jedis: `7.2.1 > 7.5.0`
* log4j loki appender: `0.9.41 > 0.9.42`
* opentelemetry-sdk-extension-autoconfigure: `1.59.0 > 1.61.0`

# 1.3.0 (2026-02-12)

### Changes *BREAKING*
* Switched base iamge to `linuxserver/baseimage-alpine:3.23`
* Switched to temurin `jdk-21.0.10+7`
* **Breaking:** LSIO conventions 
  * moved rahla from `/rahla` to `/app/rahla`. 
  * `/app/rahla/deploy` is linked to `/config/deploy`.
  * `/app/rahla/etc` is linked to `/config/etc`.
  * `/rahla/deploy` is no longer scanned for files
* Added kubernetes deployments `manifests/rahla.yaml`
*  **Breaking:** RAHLA_DEPLOY_PATH has been removed; if you require this functionality use `org.apache.felix.fileinstall`
*  **Breaking:** replaced pax-web-http-jetty with pax-web-http-undertow
*  Replaced pax-logging-* from karaf with rahla-logging version

### Dependency Updates

* karaf: `4.4.8 > 4.4.9`
* groovy: `4.0.29 > 4.0.30`
* opentelemetry-sdk-extension-autoconfigure: `1.57.0 > 1.59.0`
* jackson2: `2.20.1 > 2.21.0`
* pax-logging: `2.3.1 > 2.3.2`
* log4j2: `2.25.2 > 2.25.3`
* log4j loki appender `0.9.32 > 0.9.41`
* redis.clients:jedis  `7.1.0 > 7.2.1`

# 1.2.4 (2025-09-19)

### Dependency Updates

* Switched base image from eclipse temurin to linuxserver/baseimage-debian:trixie
* karaf: 4.4.7 -> 4.4.8
* camel: 4.10.3 -> 4.10.7
* groovy: 4.0.25 -> 4.0.29
* jackson2: 2.18.2 -> 2.20.1
* pax-logging: 2.2.8 -> 2.3.1
* lombok: 1.18.36 -> 1.18.42
* commons-configuration2: 2.12.0 -> 2.13.0
* log4j2: 2.24.3 -> 2.25.2
* jedis: 5.2.0 -> 7.1.0
* opentelemetry_agent: 2.12.0 -> 2.22.0
* opentelemetry-sdk-extension-autoconfigure: 1.21.0-alpha -> 1.57.0

# 1.2.3 (2025-09-19)


### Fix 

* Fixed invalid useradd

# 1.2.2 (2025-09-19)


### Changes 

* Switched base image from eclipse-temurin:21-jre-alpine to eclipse-temurin:21-jre


# 1.2.1 (2025-07-15)


### Changes 

* Camel Osgi Route Templates have been moved into a separate feature `<feature>camel-route-templates</feature>`


# 1.2.0 (2025-05-20)


### Changes (A lot breaking)

* Camel feature was removed from preinstalled feature list, so add `<feature>camel</feature>` to your features.xml or install manually (Improves startup speed)
* SmartUrl was removed without replacement
* Jedis and Janusgraph sources have been moved into separate bundles and features `<feature>graphsource</feature>` `<feature>jedissource</feature>`
* Interfaces GraphSource and JedisSource have been moved from rahla.api to rahla.jedissource and rahla.graphsource. For referencing: `interface="rahla.graphsource.GraphSource"`
* Base docker image was bumped to java temurin 21 on alpine 3
* Updated to camel 4.10.3
* Updated janusgraph client to 1.1.0
* We recommmend to use /rahla/deploy instead of /deploy. Hence, RAHLA_DEPLOY_PATH env variable and ```org.apache.felix.fileinstall-rahla.cfg ```will be removed in a future release. You can add you own file install handler via deploy.
    ```
    felix.fileinstall.dir = ${env:RAHLA_DEPLOY_PATH:-/deploy/}
    felix.fileinstall.filter = .*
    felix.fileinstall.poll = 1000
    felix.fileinstall.start.level = 80
    felix.fileinstall.active.level = 80
    felix.fileinstall.log.level = 3
   ```
* Siddhi was set to deprecated due to inactivity of the project. If db crud is required we recommend migrating to camel-mybatis
* resource:deploy for Groovy bean Factory is set to deprecated. Groovy bean Factory now accepts URLs like file:// http://
* Camel resource:deploy in context is set to deprecated. Use resource:file: with fully qualified path or relative to /rahla instead

#### Tips

If you build your own container from rahla base image you can start and stop karaf during building the images. This preinstalls all features and keeps container start times very low.
```
FROM datatactics/rahla:latest
USER rahla
ADD deploy /rahla/deploy
RUN (sleep 60; karaf stop ) & karaf server
```
  
  

# 1.1.2 (2025-02-26)


### Bug Fix

* Added Capabilities to handle SpiFly handling for ServiceLoader


# 1.1.1 (2025-02-12)


### Changes

* Shaded Groovy Libs into Rahla bundle until groovy 4 is implemented as an fragment host

# 1.1.0 (2025-02-07)


### Changes

* Camel Version : 4.9.0
* Karf Version: 4.4.9
* Mulitiple dependency updates


# 1.0.21 (2024-XX-XX)


### Changes

* Added documentaiton
* Camel Version : 3.22.2
* Karf Version: 4.4.6
