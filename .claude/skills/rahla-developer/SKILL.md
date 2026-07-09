---
name: rahla-developer
description: >-
  Author runtime artifacts for Rahla — the datatactics open-source Apache Karaf appliance
  that hot-loads Camel routes, OSGi configs, and Groovy beans from a deploy directory
  (`deploy/` locally, `/config/deploy/` in the container). Use whenever the user writes
  or edits files destined for a Rahla deploy directory: Camel Blueprint XML (`*.xml`)
  with REST endpoints / routes / OSGi `<reference>` wiring (incl. multi-file
  routeContexts), `features.xml` feature installs, Groovy classes via
  `rahla.api.GroovyBeanFactory`, OSGi `*.cfg` files (datasources, factory PIDs), Camel
  route-template YAMLs, or legacy `*.siddhi` (Fradi). Also for building service images
  `FROM datatactics/rahla` (incl. air-gapped/offline builds), migrating a service
  between Rahla versions, Karaf shell debugging, and why a file dropped into deploy
  isn't picking up (bundle Waiting / GracePeriod, empty camel:context-list). Prefer this
  skill over generic Camel or OSGi guidance whenever the runtime is Rahla.
---

# Rahla Developer

<!-- Sync note: the master copy of this skill lives in tactician/skills (datatactics-skills
     plugin, skills/rahla-developer/SKILL.md). This in-repo copy exists so contributors get the
     skill when working on the rahla repo itself — keep both in sync when editing. -->

Author Camel integration artifacts for the Rahla Karaf appliance. Rahla is a packaged Karaf 4.4 + Camel 4.x distribution (1.3.4 ships Karaf 4.4.11 + Camel 4.18.2 on JDK 21 — see the README for current versions) that monitors a single directory and hot-loads anything dropped into it. Almost all user-facing development in Rahla happens by writing files into that directory — there is no application code to recompile, no service to restart. Understanding how Rahla wires those files together is the whole job of this skill.

When **upgrading a service between Rahla versions** (path moves in 1.3.0, the Camel 4.10 → 4.18 jump in 1.3.1, …), don't reconstruct the deltas — the rahla repo ships `migration.md`, a sourced per-version migration reference from 1.2.1 onward, including the relevant upstream Camel breaking changes.

## The deploy directory is the API

Rahla's `org.apache.felix.fileinstall` configuration polls one directory and dispatches each file to a handler based on extension:

| File ext | What happens when it lands |
|----------|----------------------------|
| `*.xml`  | Loaded as a **Camel Blueprint context** (routes, REST, beans, OSGi `<reference>`s) |
| `*.groovy` | Compiled at runtime, available as a bean via `rahla.api.GroovyBeanFactory` |
| `*.cfg`  | Registered as an **OSGi configuration** (factory PIDs split on the `-` in the filename) |
| `*.yaml` | If it parses as a Rahla route-template doc, becomes Camel route templates via `TemplateFileInstaller` |
| `*.jar`  | Installed as an OSGi bundle |
| `*.kar`  | Installed as a Karaf feature archive |
| `*.siddhi` | Loaded by the Fradi CEP component — **legacy, do not use for new work** |

Removing a file uninstalls the corresponding artifact; editing a file updates it. There is no need to "restart" anything — but if Rahla refuses a file (typo in XML, missing service reference) it logs the failure and does nothing, so always check `data/log/karaf.log` (local) or the container logs after dropping a new file.

> **Camel is NOT preinstalled.** A stock `datatactics/rahla` container has no Camel runtime — a freshly dropped Blueprint XML with routes will sit in `Waiting`/`GracePeriod` forever with an empty `camel:context-list`. Install Camel (and every Camel component your routes use) via a [`features.xml` in the deploy dir](#install-features-with-a-featuresxml-in-deploy), not via manual `feature:install`.

### Path depends on environment

| Where | Deploy path |
|-------|-------------|
| Local unpacked assembly (`mvn package` then `bin/karaf`) | `<assembly>/deploy/` |
| Inside the `datatactics/rahla` container (≥ 1.3) | `/config/deploy/` |
| Inside `datatactics/rahla` 1.2.x (legacy) | `/rahla/deploy/` |
| `datatactics/rahla` 1.0.x (legacy) | `/deploy/` |

Always check the project's Dockerfile (`FROM datatactics/rahla:<tag>`) before hardcoding a path. When wiring a Groovy bean or a config-from-file, the path inside a Blueprint XML must match the path **as Rahla sees it** — i.e. `/config/deploy/...` for 1.3+ container deployments, even if you're editing in a host-mounted folder.

`RAHLA_DEPLOY_PATH` no longer exists (removed in 1.3). To monitor an additional directory, drop an `org.apache.felix.fileinstall-<name>.cfg` into the deploy dir.

## Install features with a `features.xml` in deploy

The GitOps way to install Karaf features is a features-repository XML in the deploy dir with `install="auto"` — it is versioned with the routes and needs no shell command. One `<feature>` per service, listing everything the routes need:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<features xmlns="http://karaf.apache.org/xmlns/features/v1.6.0" name="runtime">
  <feature name="my-service" install="auto">
    <feature>camel</feature>              <!-- the Camel runtime itself -->
    <feature>camel-jackson</feature>
    <feature>camel-http</feature>
    <!-- database access via pax-jdbc (pairs with org.ops4j.datasource-*.cfg) -->
    <feature>jdbc</feature>
    <feature>pax-jdbc-config</feature>
    <feature>pax-jdbc-oracle</feature>
    <feature>pax-jdbc-pool-hikaricp</feature>
  </feature>
</features>
```

Every `camel-*` component used in a route URI or DSL element needs its feature here (`camel-groovy` for inline `<groovy>` expressions, `camel-mybatis`, `camel-elasticsearch`, …). Missing feature = bundle `Waiting`, `bundle:diag` names the unresolved package/service.

### Non-OSGi jars: `wrap:` bundles inside a feature

Plain jars (no OSGi manifest, or a broken one) can be pulled in via `pax-url-wrap`, fixing the manifest inline. Declare `wrap` as a prerequisite and use `overwrite=merge` to patch bad manifests:

```xml
<feature name="my-extra-libs" install="auto">
  <feature prerequisite="true">wrap</feature>
  <bundle start-level="50">wrap:mvn:org.example/plain-jar/1.0$overwrite=merge&amp;Bundle-SymbolicName=org.example.plain&amp;Bundle-Version=1.0&amp;Export-Package=org.example;version=1.0&amp;DynamicImport-Package=*</bundle>
</feature>
```

(Real-world reference: the FLT fuel-service wraps the whole Camunda DMN 7.24 stack this way — broken `Bundle-SymbolicName`s, a scala-shaded jar without manifest, and a mandatory-import-that-should-be-optional are all fixed with `wrap:` options in its `deploy/features.xml`.)

## Blueprint XML — the integration surface

A typical Rahla Blueprint XML has four parts: namespace declaration, OSGi service references, Camel beans/processors, and the Camel context with routes.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0"
           xmlns:cm="http://aries.apache.org/blueprint/xmlns/blueprint-cm/v1.1.0"
           xmlns:camel="http://camel.apache.org/schema/blueprint">

    <!-- 1. OSGi service references — wire what other bundles published -->
    <reference id="beanFactory" interface="rahla.api.GroovyBeanFactory"/>

    <!-- 2. Beans created in this context — your processors, components, etc. -->
    <bean id="myProcessor" factory-ref="beanFactory" factory-method="createBean">
        <argument value="file:///config/deploy/processors/MyProcessor.groovy"/>
    </bean>

    <!-- 3. Camel context — REST configuration and routes -->
    <camelContext id="my-context" xmlns="http://camel.apache.org/schema/blueprint">
        <restConfiguration component="undertow" port="8183" contextPath="/my-app" enableCORS="true"/>

        <rest>
            <post uri="/push"><to uri="direct:in"/></post>
            <get  uri="/health"><to uri="direct:health"/></get>
        </rest>

        <route id="in">
            <from uri="direct:in"/>
            <process ref="myProcessor"/>
            <to uri="log:out?level=INFO"/>
        </route>
    </camelContext>
</blueprint>
```

### REST components: `undertow` is the default, `jetty` still works

Since Rahla 1.3 the boot HTTP runtime is `pax-web-http-undertow` (serving the OSGi/Pax-Web port **8181**), and `<restConfiguration component="undertow" .../>` is the default choice. The Camel REST components (`camel-undertow`, `camel-jetty`) run their own listener on the port you configure — pick a service-specific port (commonly **8183**) so it doesn't clash with 8181. `component="jetty"` still works fine on Rahla 1.3+ when you install the `camel-jetty` feature via your deploy `features.xml` (this is what the FLT fuel-service runs); the pax-web boot feature being Undertow doesn't prevent that.

If you bring in Jetty (via `camel-jetty` or `pax-web-http-jetty`), pin a fixed version via `<blacklistedBundles>` (or a feature override) — the Jetty version transitively pulled on Karaf 4.4.x has known CVEs. Run only one Pax-Web HTTP feature at a time.

### OSGi service references with filters

Several Rahla components publish their service per-`.cfg`-file and tag it with a name property — you reference them by interface plus a filter on that name:

```xml
<reference id="myGraph" interface="rahla.graphsource.GraphSource"
           filter="(graphSourceName=aGraphSource)"/>

<reference id="myDS" interface="javax.sql.DataSource"
           filter="(dataSourceName=my-oracle)"/>

<!-- deprecated since 1.3.1 — existing consumers only, no new work -->
<reference id="myRedis" interface="rahla.jedissource.JedisSource"
           filter="(jedisSourceName=myJedisSource)"/>
```

The filter value must match a property set in the corresponding `.cfg` file (see [OSGi config files](#osgi-config-files-cfg) below). When `<reference>` blocks fail to satisfy, Blueprint will park the entire context in `GraceTimeoutException` and tear it down after ~5 minutes — `karaf@root()> bundle:list -s` will show your bundle as `Waiting`.

### Property placeholders from a `.cfg`

Bind a config PID into Blueprint properties so values from `<service>.cfg` are usable as `${...}`:

```xml
<cm:property-placeholder persistent-id="my.service" update-strategy="reload">
    <cm:default-properties>
        <cm:property name="endpoint" value="http://default"/>
    </cm:default-properties>
</cm:property-placeholder>

<camelContext ...>
    <route id="x">
        <from uri="timer:tick?period=10000"/>
        <to uri="{{endpoint}}/api/ping"/>
    </route>
</camelContext>
```

`update-strategy="reload"` re-creates the Blueprint container when the `.cfg` changes — quick to react, but in-flight exchanges are dropped during the reload.

### Error handlers

Declare error handlers as `<errorHandler>` elements **inside** `<camelContext>` (the Camel 4 Blueprint style — don't wire the old `*ErrorHandlerBuilder` classes as `<bean>`s): a `DefaultErrorHandler` for retries with exponential backoff, a `DeadLetterChannel` for terminal failures that should be parked rather than crash the route:

```xml
<camelContext ...>
    <errorHandler id="retry" type="DefaultErrorHandler">
        <redeliveryPolicy maximumRedeliveries="10"
                          redeliveryDelay="500"
                          useExponentialBackOff="true"
                          backOffMultiplier="2"
                          retryAttemptedLogLevel="WARN"/>
    </errorHandler>

    <errorHandler id="parkErrors" type="DeadLetterChannel" deadLetterUri="direct:errors"/>
    ...
</camelContext>
```

Reference per-route: `<route id="..." errorHandlerRef="retry">`. Or set `errorHandlerRef` on the `<camelContext>` for the whole context.

### Splitting one service across multiple XML files (shared CamelContext)

One XML file = one Blueprint container, but a big service doesn't have to be one file. A Camel `<routeContext>` is a plain `List<RouteDefinition>` bean — export it as an OSGi service from a sub-file and import it into the file that owns the single `<camelContext>`:

```xml
<!-- my-parsing.xml — exporting side: routes only, no camelContext -->
<blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0">
    <service ref="parsing-routes" auto-export="interfaces"/>
    <routeContext id="parsing-routes" xmlns="http://camel.apache.org/schema/blueprint">
        <route id="parse-a">
            <from uri="direct:parse-a"/>
            ...
        </route>
    </routeContext>
</blueprint>
```

```xml
<!-- my-service.xml — importing side: owns the camelContext, beans, error handlers -->
<reference id="parsing-routes" interface="java.util.List"
           filter="(osgi.service.blueprint.compname=parsing-routes)"/>

<camelContext id="my-service" xmlns="http://camel.apache.org/schema/blueprint">
    <routeContextRef ref="parsing-routes"/>
    ...
</camelContext>
```

The service interface is `java.util.List` (that's all a `<routeContext>` exports), matched on the Blueprint component name — so keep `routeContext id`, the `filter` value, and the local `reference id` identical. Processor beans, error handlers, and `direct:` endpoints used inside the sub-file's routes resolve against the **importing** camelContext's registry at route start-up, so define shared beans once in the master file. This is how the FLT fuel-service composes ~10 deploy XMLs into one `fuel` context. Caveat: the master context only starts once all referenced route lists are published — a broken sub-file parks the whole service (`bundle:diag` shows which reference is missing).

## Groovy bean compilation

`rahla.api.GroovyBeanFactory` compiles a Groovy source file (or any URL the JVM can `openStream()`) into a class at runtime and instantiates it. This is how custom Camel processors are added to Rahla without producing a bundle.

For small expressions you don't need a class at all — with the `camel-groovy` feature installed, inline `<groovy>...</groovy>` works anywhere Camel accepts an expression (`<setHeader>`, `<filter>`, `<transform>`, …). Use the bean factory for real processors, inline Groovy for one-liners.

### Wiring a Groovy class as a Camel `Processor`

```xml
<reference id="beanFactory" interface="rahla.api.GroovyBeanFactory"/>

<bean id="MyProcessor" factory-ref="beanFactory" factory-method="createBean">
    <argument value="file:///config/deploy/processors/MyProcessor.groovy"/>
</bean>
```

Then reference it in routes via `<process ref="MyProcessor"/>`.

```groovy
// /config/deploy/processors/MyProcessor.groovy
package processors

import org.apache.camel.Exchange
import org.apache.camel.Processor

class MyProcessor implements Processor {
    void process(Exchange exchange) throws Exception {
        def body = exchange.in.body
        // ... transform ...
        exchange.message.body = [field: "value"]
    }
}
```

### How `createBean` instantiates the class

`GroovyBeanFactoryImpl` first tries to call a constructor accepting `org.osgi.framework.BundleContext`; if that constructor doesn't exist it falls back to the no-arg constructor. Use the `BundleContext` constructor when the processor needs to look up other OSGi services by hand:

```groovy
import org.osgi.framework.BundleContext

class MyProcessor implements Processor {
    private final BundleContext bundleContext
    MyProcessor(BundleContext bundleContext) { this.bundleContext = bundleContext }
    // ...
}
```

For most processors, the no-arg constructor is enough — pass dependencies in via Blueprint setters or `<argument>` blocks added to the `<bean>` definition.

### URL forms (and the deprecated one)

`createBean` accepts any URL its `URL.openStream()` understands. The conventional choices:

- `file:///absolute/path/to/Class.groovy` — preferred. Works locally and inside the container.
- `http://host/Class.groovy` — works, occasionally useful for pulling beans from a config server.
- `resource:file:relative/path` — **deprecated.** Logs a warning on every load. Replace with `file://` and a fully qualified path. The deprecated form will be removed.

### Compilation failures

Groovy compilation errors are thrown out of `createBean` and bubble up as a `RuntimeException` during Blueprint initialization, which fails the whole context. The Karaf log shows a `MultipleCompilationErrorsException` with the source file path and line number — fix the Groovy file and Rahla will rebuild the context automatically.

## OSGi config files (`.cfg`)

Filenames map to OSGi PIDs:

- `<pid>.cfg` → singleton config for PID `<pid>`
- `<pid>-<name>.cfg` → factory config for `<pid>`, instance name = `<name>`. The instance name is **not** automatically a service property; if the bundle treats configs as factories (graphsource, jedissource), it usually publishes one service per instance and exposes a name property you wrote inside the file.

### Example: GraphSource (Gremlin / JanusGraph)

`rahla.graphsource-aGraphSource.cfg`:

```ini
hosts=janusgraph
port=8182
user=
pass=
graphSourceName=aGraphSource
graphTraversalSourceName=graph_traversal
serializer=org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1
serializerConfig={"ioRegistries": ["org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry"]}
```

### Example: JedisSource (Redis) — deprecated

**`JedisSource` is `@Deprecated` since Rahla 1.3.1 (EOL).** Don't build new integrations on it; the rahla repo names no drop-in successor, so for new Redis work bring your own client (Camel component or wrapped library) via `features.xml`. For maintaining an existing consumer, `rahla.jedissource-myJedisSource.cfg`:

```ini
host=redis
port=6379
db=0
user=
pass=
jedisSourceName=myJedisSource
```

### Example: pax-jdbc datasource

`org.ops4j.datasource-mydb.cfg`:

```ini
osgi.jdbc.driver.name=oracle
dataSourceName=my-oracle
pool=hikari
url=jdbc:oracle:thin:@oracledb:1521:ORCLCDB
user=APP
password=secret
```

The `dataSourceName` becomes the OSGi service property used to filter `javax.sql.DataSource` references — match it in the corresponding Blueprint `<reference filter="(dataSourceName=my-oracle)"/>`.

## Camel route templates (YAML)

`TemplateFileInstaller` turns YAML files in the deploy dir into `camel.route.template` factory configurations. The Camel context that hosts those templates picks them up via the route-template-parameter source and creates one route per `routes[]` entry.

```yaml
# /config/deploy/my-template.yaml
templateId: my-route-template
sharedConfigPid: rahla.shared.config   # optional; bumps a counter on update
routes:
  - id: ingest-feed-a
    parameters:
      from: "timer:feed-a?period=60000"
      to:   "log:feed-a"
  - id: ingest-feed-b
    parameters:
      from: "timer:feed-b?period=120000"
      to:   "log:feed-b"
```

The Camel context referenced must have a matching `<routeTemplate id="my-route-template">` definition with `<templateParameter name="from"/>` etc. — the YAML supplies the parameter values, the Blueprint XML supplies the template body.

Multiple template documents can share one YAML file (use `---` separators) and they will be installed together. Editing the YAML re-creates all the routes from that file.

## Shipping a service as a container image

A Rahla-based service repo is typically just a `deploy/` directory plus a two-line Dockerfile:

```dockerfile
FROM datatactics/rahla:1.3.4
# rahla runs as the LSIO user abc (uid/gid 911)
COPY --chown=911:911 deploy /config/deploy
```

Conventions that go with it:

- **Keep credential-bearing `.cfg` files empty in git** (`org.ops4j.datasource-*.cfg` as 0-byte files). The real contents are mounted over them at deploy time (k8s Secret/ConfigMap). Side benefit: nothing tries to connect to a database while the image builds.
- **Custom CA certificates** (1.3.3+): drop PEM/`.crt` files into `/config/certs`; the `init-rahla-certs` s6 service imports them into the JVM truststore before Karaf starts. No hand-built JKS.
- The container pins Karaf's maven repo to `/config/.m2` (1.3.3+), so feature-install downloads land in a fixed, persistent path.

### Air-gapped / offline images

KAR-based offline packaging was **removed in 1.3.3**. The supported pattern: boot the real Karaf once at build time so it installs the whole `deploy/features.xml` closure (including `wrap:` bundles) into the OSGi cache and `/config/.m2`, then remove the install trigger and force offline mode:

```dockerfile
FROM docker.io/datatactics/rahla:1.3.4
COPY --chown=911:911 deploy /config/deploy

# boot once → Karaf installs the full closure, then stop it.
# The sleep must cover boot + downloads; raise it on a cold cache / slow network.
USER 911:911
RUN (sleep 180; karaf stop) & karaf server

# drop only the install trigger; /config/.m2 stays for clean offline resolve
USER 0
RUN rm -f /config/deploy/features.xml
ENV KARAF_SYSTEM_OPTS="-Dorg.ops4j.pax.url.mvn.offline=true ${KARAF_SYSTEM_OPTS}"
```

This requires the datasource/secret `.cfg` files to be empty at build time (see above) so no external connection is attempted during the build boot.

## Karaf console — debugging recipes

The local assembly exposes SSH on **8101** (default user/password depend on `users.properties`; the upstream Karaf default is `karaf:karaf`, README example uses `admin`). The `bin/client` script connects without prompting if it can find the per-instance keys.

```sh
# Connect locally (requires the karaf instance to be running via bin/start)
assembly/target/assembly/bin/client

# Or interactively (foreground, with JDWP on 5005)
assembly/target/assembly/bin/karaf debug
```

Useful from the karaf shell:

| Command | What it tells you |
|---------|-------------------|
| `bundle:list -s` | All bundles + their state. `Waiting` typically means a missing OSGi service reference. |
| `bundle:diag <id>` | Why the bundle is stuck. Lists missing capabilities / services. |
| `scr:list` | Declarative-services components and whether they're SATISFIED / UNSATISFIED. |
| `scr:info <name>` | What references a component is waiting on. |
| `config:list "(service.pid=rahla.graphsource)"` | Inspect a factory PID's instances. |
| `camel:context-list` | All Camel contexts loaded from XML. |
| `camel:route-list` | Per-context route status. |
| `log:logs -n 200 -l ERROR` | Last 200 ERROR-level log entries (Rahla's own pretty `log:logs` — see `rahla/commands/ShowCommand.java`). |
| `log:tail` | Live tail. |

When a Blueprint context refuses to start, `bundle:diag` on the relevant bundle ID is the fastest diagnostic — it reports unresolved `<reference>`s by interface and filter.

## What to avoid

- **`resource:file:` and `resource:deploy:` prefixes** — deprecated. Use real URLs (`file://`, `http://`).
- **`RAHLA_DEPLOY_PATH`** — removed in 1.3. Add a `org.apache.felix.fileinstall-<name>.cfg` instead if a second watch directory is needed.
- **Unpinned Jetty.** Jetty is still usable on Rahla 1.3+ (it's just no longer the boot default), but the version that comes with Karaf 4.4.x's `pax-web-http-jetty` has a known zero-day — pin a fixed version via `<blacklistedBundles>` or a feature override before relying on it.
- **New `JedisSource` consumers** — `JedisSource`/`JedisServiceImpl` are `@Deprecated` since 1.3.1 (EOL). Maintain existing ones; don't add new ones.
- **KAR-based offline packaging** — removed in 1.3.3. Air-gapped images use the boot-once cache-priming pattern (see above).
- **Fradi / Siddhi (`*.siddhi` files, `<bean class="rahla.components.fradi.FradiComponent"/>`)** — marked EOL. New CEP/CRUD work should use `camel-mybatis`, `camel-stream`, or another supported Camel component.
- **Karaf-bundled Groovy** — the assembly blacklists `org.apache.groovy/groovy/[4,5)` and ships its own embedded Groovy 4 inside the `rahla` bundle. Do not declare a separate Groovy feature; if a feature pulls one in, add it to `<blacklistedBundles>` in `assembly/pom.xml`.
- **Karaf-bundled pax-logging** — replaced by the `rahla-logging` feature so the Loki appender (`Fragment-Host: pax-logging-log4j2`) loads correctly. Don't re-pin pax-logging from elsewhere.
- **Hardcoded `/rahla/deploy` or `/deploy` paths in container artifacts** — only `/config/deploy` is monitored on 1.3+; the s6 init script warns on the legacy paths.

## Constraints

- **No application restart loop.** Iterate by editing the file in the deploy dir and watching the log. Restarting Karaf is a strong signal something else is wrong.
- **Files are the build artifact.** `*.xml`, `*.groovy`, `*.cfg`, `*.yaml` dropped into the deploy dir are the deliverable. Bundles (`*.jar`) are an option, but for routes and processors prefer Blueprint + Groovy — that's why Rahla exists.
- **Blueprint context = transaction.** A single XML file becomes one OSGi Blueprint container. If any `<bean>` or `<reference>` fails, the whole context is destroyed. Split unrelated routes into separate XML files when isolation matters; for related routes that must share one CamelContext, use the exported-`routeContext` pattern (see above).
- **OSGi import/export hygiene applies to bundles, not to Blueprint XML.** Anything you reach for via `<reference>` must have been exported by some installed bundle's feature. If `bundle:diag` says a service interface isn't visible, the relevant feature isn't installed (`feature:list | grep <name>` in the karaf shell).
