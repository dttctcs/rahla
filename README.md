# Rahla

**Rahla** is an open-source [Apache Karaf](https://karaf.apache.org/) appliance preconfigured for
[Apache Camel](https://camel.apache.org/), packaged as a Docker image. It runs your integrations in
an OSGi container and **hot-loads everything you drop into one directory** — Camel routes, OSGi
configs, Groovy beans, route-template YAML, bundles and feature archives — with no rebuild and no
restart.

Current release ships **Karaf 4.4.12 + Camel 4.18.4** on JDK 21.

## Contents

- [Key features](#key-features)
- [Getting started](#getting-started)
- [The deploy directory is the API](#the-deploy-directory-is-the-api)
- [Special features](#special-features)
- [HTTP / REST](#http--rest)
- [Air-gapped / offline images](#air-gapped--offline-images)
- [Configuration](#configuration)
- [Monitoring](#monitoring)
- [Kubernetes](#kubernetes)
- [Build from source](#build-from-source)
- [FAQ](#faq)
- [Contributing](#contributing)

## Key features

* **Dynamic deployment** — monitors `/config/deploy` and loads resources on the fly.
* **Groovy bean factory** — compile Groovy processors/beans into Camel routes at runtime.
* **Camel route templates** — define reusable templates in YAML.
* **GraphSource** — pooled Gremlin/TinkerPop client (e.g. JanusGraph) as an OSGi service.
* **JedisSource** — pooled Redis (Jedis) client as an OSGi service *(deprecated)*.
* **Loki appender** — pax-logging-compatible Log4j2 appender for Grafana Loki *(deprecated — prefer the OpenTelemetry logs exporter)*.
* **Monitoring** — Prometheus JMX Exporter and OpenTelemetry agent built in.
* **Air-gapped images** — bake a project's full dependency closure into the image; no startup downloads.
* **Easy CA certs** — drop PEM/`.crt` into `/config/certs`; they're imported into the JVM truststore on start.

## Getting started

### Prerequisites

* Docker / Podman (or any OCI runtime).

### Run

```sh
mkdir -p deploy
docker run --rm \
  -p 8101:8101 -p 8181:8181 \
  -v ./deploy:/config/deploy \
  datatactics/rahla:latest
```

Drop your artifacts into `./deploy` — Rahla picks up changes automatically. Removing a file
uninstalls the artifact; editing it reloads it.

> **LSIO base image:** the container runs as `PUID`/`PGID` (default `911`). `/config/etc` and
> `/config/deploy` are the persistent config and deploy mounts; `/app/rahla/{etc,deploy}` are
> symlinks to them.

### Console access

SSH on port **8101** (default user/password `admin` / `admin`, see `users.properties`):

```sh
ssh -p 8101 admin@localhost
```

Useful shell commands: `bundle:list -s`, `bundle:diag <id>` (why a bundle is `Waiting`),
`feature:list -i`, `camel:context-list`, `log:tail`.

## The deploy directory is the API

Rahla dispatches each file in `/config/deploy` by extension:

| File         | What happens |
|--------------|--------------|
| `*.xml`      | loaded as a **Camel Blueprint context** (routes, REST, beans, OSGi `<reference>`s) |
| `*.groovy`   | compiled into an **OSGi bundle** `rahla.groovy.<name>` (classes usable as `<bean class="…"/>`); legacy: referenced by URL via `rahla.api.GroovyBeanFactory` |
| `*.cfg`      | registered as an **OSGi configuration** (factory PIDs split on the `-` in the filename) |
| `*.yaml`     | turned into **Camel route templates** by `TemplateFileInstaller` |
| `*.jar`      | installed as an **OSGi bundle** |
| `*.kar`      | installed as a **feature archive** (Karaf KAR) |

If a file is rejected (bad XML, missing service reference, …), Rahla logs it and moves on — always
check the logs after dropping something new.

> Camel itself is **not** preinstalled. A dropped `*.xml` Camel context needs the Camel runtime —
> install it via a `features.xml` in deploy (e.g. `<feature>camel-blueprint</feature>`); for
> air-gapped use, bake the closure in (see [Air-gapped / offline images](#air-gapped--offline-images)).

## Special features

### Groovy bean factory

Compile a Groovy class from any URL into a Camel `Processor`/bean at runtime — no bundle needed:

```xml
<reference id="beanFactory" interface="rahla.api.GroovyBeanFactory"/>

<bean id="myProcessor" factory-ref="beanFactory" factory-method="createBean">
  <argument value="file:///config/deploy/processors/MyProcessor.groovy"/>
</bean>
```

`createBean` tries a constructor taking `org.osgi.framework.BundleContext`, then a no-arg one. Use a
real URL (`file://`, `http://`); the old `resource:` prefix is deprecated.

### Groovy files as bundles

Since 1.3.6 every `*.groovy` dropped into `/config/deploy` is also compiled into a real OSGi bundle
(`rahla.groovy.GroovyBundleDeployer`, a fileinstall `ArtifactUrlTransformer` plus `rahla-groovy:` URL
handler, the same mechanism Karaf's blueprint deployer uses). The classes then need no factory:

```groovy
// /config/deploy/MyProcessor.groovy — declare a package, default-package classes cannot be exported
package processors

import org.apache.camel.Exchange
import org.apache.camel.Processor

class MyProcessor implements Processor {
  void process(Exchange exchange) throws Exception { exchange.message.setHeader('seen', true) }
}
```

```xml
<bean id="myProcessor" class="processors.MyProcessor"/>
```

What you get, compared to the factory:

* `bundle:list` shows `rahla.groovy.MyProcessor`; the version qualifier is a hash of the source, so
  you can see which revision is live. `bundle:headers` shows `Rahla-Groovy-Source`.
* Editing the file updates the bundle and restarts the Blueprint contexts that use it — no need to
  touch the XML. Deleting the file uninstalls the bundle.
* The bundle exports its packages and dynamically imports everything else, so the code sees the same
  Camel/Groovy/Jackson packages the factory-compiled classes do.
* Deploy order does not matter. If the source imports packages that are not in the runtime yet
  (Camel still installing, a feature missing), a class-less **placeholder** bundle with an
  `Import-Package` on those packages is installed instead; it shows up as `Installed` and
  `bundle:diag` names the missing package. As soon as the packages appear the deployer recompiles
  the source in place. Everything else (syntax errors, typos in class names) is a hard error, logged
  once with file name and line, and retried only when the file changes.

One file may hold several classes — they share the file's `package` and all land in the same bundle.
A package declaration is required; classes in the default package cannot be exported and are refused
at deploy time with an explicit message.

A complete, runnable example (features + multi-class Groovy + REST routes, no Docker) lives in
[`examples/local/`](examples/local/).

The factory path is unchanged: an XML that references the same file via `createBean` keeps working
(it compiles its own copy of the class, so the two do not share static state).

### Camel route templates

Define route templates as YAML; `TemplateFileInstaller` turns each into a `camel.route.template`
config the hosting Camel context instantiates:

```yaml
templateId: my-route-template
sharedConfigPid: rahla.shared.config   # optional
routes:
  - id: ingest-a
    parameters: { from: "timer:a?period=60000", to: "log:a" }
  - id: ingest-b
    parameters: { from: "timer:b?period=120000", to: "log:b" }
```

The Camel context must define a matching `<routeTemplate id="my-route-template">`; the YAML supplies
the parameter values.

### GraphSource (Gremlin / JanusGraph)

Each `rahla.graphsource-<name>.cfg` publishes a `rahla.graphsource.GraphSource` service, filterable by name:

```ini
hosts=janusgraph
port=8182
graphSourceName=aGraphSource
graphTraversalSourceName=graph_traversal
serializer=org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1
serializerConfig={"ioRegistries": ["org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry"]}
```

```xml
<reference id="myGraph" interface="rahla.graphsource.GraphSource" filter="(graphSourceName=aGraphSource)"/>
```

```java
public interface GraphSource<T, V> {
  List<String> getHosts();
  String getUser();
  V getClient();                 // Gremlin client
  T getGraphTraversalSource();   // g
}
```

### JedisSource (Redis) *(deprecated)*

> The `JedisSource` interface is marked `@Deprecated`. Still functional, but avoid for new work.

Each `rahla.jedissource-<name>.cfg` publishes a pooled `rahla.jedissource.JedisSource`:

```ini
host=redis
port=6379
db=0
jedisSourceName=myJedisSource
```

```xml
<reference id="myRedis" interface="rahla.jedissource.JedisSource" filter="(jedisSourceName=myJedisSource)"/>
```

```java
package rahla.jedissource;

@Deprecated
public interface JedisSource {
  Jedis getResource();
  void returnResource(Jedis jedis);
}
```

### Loki Log4j2 appender

> **Deprecated.** The Loki appender is retained for backward compatibility and still receives
> dependency bumps, but new or additional log shipping should go through the OpenTelemetry logs
> exporter (`OTEL_LOGS_EXPORTER`, see Monitoring) rather than the `<Loki .../>` appender.

A pax-logging-compatible appender for Grafana Loki ships in the `rahla-logging` feature.

> You must add `<Configuration packages="pl.tkowalcz.tjahzi.log4j2">` to `log4j2.xml` for the
> `<Loki .../>` appender to resolve.

## HTTP / REST

The boot HTTP runtime is **Undertow** (`pax-web-http-undertow`); the OSGi/Pax-Web port defaults to
**8181**. Use it from a Camel REST config:

```xml
<restConfiguration component="undertow" port="8183" contextPath="/my-app"/>
```

Jetty is **not** the default. `camel-jetty` / `pax-web-http-jetty` still work, but the Jetty version
pulled by Karaf 4.4.x has known CVEs — pin a fixed version (or just stay on Undertow). Run one
Pax-Web HTTP feature at a time.

## Air-gapped / offline images

For dev you just mount `deploy/`. For an **air-gapped** image (no downloads at startup) you bake the
whole dependency closure in by letting the *real Karaf* install it once at build time, then shipping
its caches. No KAR, no closure resolver, no stubs — Karaf resolves everything itself, including
`wrap:` bundles and rahla's own features (`graphsource`, …).

How it works:

1. **Boot Karaf during the build.** It installs everything in `deploy/features.xml` (camel, your
   feature's full bundle closure, ojdbc, …), downloading bundles into the OSGi cache (`data/cache`)
   and the maven repo (`/config/.m2`, pinned + pre-created by rahla).
2. **Stop it and drop the `features.xml` install trigger.**
3. **At runtime, offline:** the bundles start from the persistent OSGi cache, and `install="auto"`
   features re-resolve from `/config/.m2` — all without network.

A project `Dockerfile.airgap`:

```dockerfile
FROM docker.io/datatactics/rahla:1.3.3

COPY --chown=911:911 deploy /config/deploy

# abc boots Karaf once -> installs the full closure into the OSGi cache + /config/.m2, then stops.
# Raise the sleep on a cold build / slow network: Karaf must finish installing before it is stopped.
USER 911:911
RUN (sleep 120; karaf stop) & karaf server

# drop the install trigger; the pinned /config/.m2 stays so the FeaturesService re-resolves offline
USER 0
RUN rm -f /config/deploy/features.xml
ENV KARAF_SYSTEM_OPTS="-Dorg.ops4j.pax.url.mvn.offline=true ${KARAF_SYSTEM_OPTS}"
```

The camel contexts / `.cfg` / processors in `deploy/` stay and load at runtime as usual; only
`features.xml` is removed (its bundles are already installed).

**Scanning with Trivy:** the dependencies are real jars under `data/cache` and `/config/.m2`, so a
normal image scan finds them. Use `trivy image` (or `trivy rootfs` on an exported tree) — **not**
`trivy fs`, which only reads dependency files (pom/lockfiles), not jars.

## Configuration

### JVM options

Add JVM parameters (truststore, `-Xmx`, …) via `EXTRA_JAVA_OPTS`. Java agents are configured via
`KARAF_SYSTEM_OPTS`.

### Important config files

Override these by mounting your own into `/config/etc`:

| File | Purpose |
|------|---------|
| `users.properties` | user accounts / roles |
| `log4j2.xml` | logging (JSON to stdout by default) |
| `config.yaml` | Prometheus JMX Exporter |
| `org.apache.felix.fileinstall-*.cfg` | which directory is monitored (`/config/deploy`) |

To watch an additional directory, drop another `org.apache.felix.fileinstall-<name>.cfg`.

### Custom CA certificates

Drop PEM / `.crt` files into **`/config/certs`** — on start the `init-rahla-certs` service imports
them into the JVM truststore (`cacerts`), so Camel routes and HTTPS clients trust them. No hand-built
JKS needed. Files may hold multiple certificates; the alias is derived from each certificate's CN
(and the serial on collision). Re-import is idempotent (skips certs already trusted by fingerprint).

## Monitoring

Both agents ship in the assembly (attached via `KARAF_SYSTEM_OPTS`, see [Configuration](#configuration)).

- **Prometheus JMX Exporter** — exposes metrics at `http://<host>:9001/metrics` (config in
  `config.yaml`).
- **OpenTelemetry agent** — `OTEL_LOGS_EXPORTER` / `OTEL_METRICS_EXPORTER` / `OTEL_TRACES_EXPORTER`
  are `none` by default; set them to enable export.

## Kubernetes

A sample manifest is in [`manifests/rahla.yaml`](manifests/rahla.yaml): it wires the ports (SSH 8101,
HTTP 8181, Prometheus 9001) and volumes. Mount your routes to `/config/deploy` (ConfigMap / PVC).

## Build from source

JDK 21 is required (matches CI and the compiler target). The reactor has no tests:

```sh
mvn --batch-mode --update-snapshots clean package   # -> assembly/target/assembly/
docker build -t datatactics/rahla:dev .
```

The runnable Karaf distribution under `assembly/target/assembly/` can also be started directly
(`bin/karaf`, `bin/karaf debug`, `bin/start`/`bin/stop`, `bin/client`) — handy for reproducing OSGi
wiring problems without the container layer.

## FAQ

**Which Camel version, and how do I migrate routes?**
Rahla 1.3.6 ships **Camel 4.18.4**. Coming from Camel 3.x, read the official
[Camel 4 migration guide](https://camel.apache.org/manual/camel-4-migration-guide.html) (and the
[Camel 4.x upgrade notes](https://camel.apache.org/manual/camel-4x-upgrade-guide.html)).

**My `*.xml` route isn't starting.**
Camel is not preinstalled — make sure the Camel runtime is installed (a `features.xml` with
`<feature>camel-blueprint</feature>`, or a KAR). Check `bundle:diag <id>`: a `Waiting` bundle usually
means an unsatisfied OSGi `<reference>` (missing service or feature).

**Trivy / a scanner flags Jetty.**
`camel-jetty` pulls in Eclipse Jetty even though Rahla serves HTTP via Undertow. If your routes
don't use Jetty, drop `<feature>camel-jetty</feature>`.

**How do I make a fully offline / air-gapped image?**
Let Karaf install the closure at build time and ship its caches — see
[Air-gapped / offline images](#air-gapped--offline-images). The build-time boot pulls the entire
bundle closure (camel, ojdbc, `wrap:` bundles, rahla's own features) into `data/cache` + `/config/.m2`;
at runtime nothing is downloaded. If the build stops Karaf before the install finishes (cold build /
slow network), the cache is incomplete — raise the `sleep`.

**`RAHLA_DEPLOY_PATH` / `/rahla/deploy` / `/deploy`?**
Gone since 1.3. Only `/config/deploy` is monitored; add an
`org.apache.felix.fileinstall-<name>.cfg` for extra directories.

## Contributing

Contributions are welcome. Rahla is licensed under the **Apache 2.0 License**.

Development happens on [repo.datatactics.dev/oss/rahla](https://repo.datatactics.dev/oss/rahla);
[github.com/dttctcs/rahla](https://github.com/dttctcs/rahla) is a push mirror where GitHub
Releases are published on every `v*` tag.

