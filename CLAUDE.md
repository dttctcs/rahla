# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo builds

Rahla is an Apache Karaf assembly (OSGi container) preconfigured for Apache Camel, packaged as a Docker image. The Maven reactor produces a Karaf distribution under `assembly/target/assembly/`, which the `Dockerfile` copies into `/app/rahla` of a `linuxserver/baseimage-alpine` image. Runtime convention is LSIO: `/app/rahla/etc` and `/app/rahla/deploy` are symlinks to `/config/etc` and `/config/deploy`, and the `org.apache.felix.fileinstall` bundle hot-loads anything dropped into `/config/deploy` (jars, `.cfg`, `.groovy`, Camel XML/YAML, route-template YAML). The `Dockerfile` also pins the pax-url maven repo to `/config/.m2` (`org.ops4j.pax.url.mvn.localRepository`, set there — **not** in the assembly's `org.ops4j.pax.url.mvn.cfg`, so local non-container runs are unaffected) and pre-creates it owned by `abc`: feature-install downloads then land at a deterministic, persistent path instead of an ambiguous `~/.m2` (under a numeric `USER`, `$HOME` is not read from passwd). This is what lets an offline image pre-seed `/config/.m2` at build time so the FeaturesService re-resolves `install="auto"` features offline without re-downloading.

## Dockerfile & base image (manually synced from adoptium)

The `Dockerfile` is `FROM ghcr.io/linuxserver/baseimage-alpine:3.24` (LSIO), but the **JRE-install
portion is copied from the upstream Eclipse Temurin image**:
<https://github.com/adoptium/containers/tree/main/21/jre/alpine/3.24> (the `apk add` dependency list,
`ENV JAVA_VERSION`, and the arch-`case` block that downloads + gpg-verifies + extracts the Temurin
JRE). **Renovate only _monitors_ the version, it cannot apply the bump.** A `# renovate:` annotation
above `ENV JAVA_VERSION` (customManager → `github-releases` on `adoptium/temurin21-binaries`, see
`renovate.json`) surfaces new `21.0.x` releases in the **Dependency Dashboard** (approval-gated, no
auto-PR — Renovate can't recompute the sha256 `ESUM`s). When the dashboard flags one, manually copy
from upstream and update **all** of: `JAVA_VERSION`, both `ESUM` checksums (aarch64 + x86_64), the two
`BINARY_URL`s, and the `apk add` list. Keep the alpine tag (`3.24`) aligned with the adoptium variant
you copy from.

Also manually coupled: `KARAF_SYSTEM_OPTS` hardcodes the **agent jar filenames+versions**
(`jmx_prometheus_javaagent-*.jar`, `opentelemetry-javaagent-*.jar`). These must match the versions
the assembly actually ships (the parent `pom.xml`) — bump them together, or the `-javaagent:` path
won't exist at runtime.

## Build commands

JDK 21 is required (matches the CI workflow and `maven-compiler-plugin` source/target). The reactor has no tests, so the standard build is just `package`:

```sh
mvn --batch-mode --update-snapshots clean package
```

A successful build produces `assembly/target/assembly/` (the Karaf distribution) which the Docker build consumes:

```sh
docker build -t datatactics/rahla:dev .
```

Build a single module against already-installed siblings, e.g. just the `rahla` bundle:

```sh
mvn -pl rahla -am package
```

Releases use `maven-release-plugin` (the recent commits `[maven-release-plugin] prepare release/next iteration` show the convention); tags are then picked up by `.forgejo/workflows/build.yaml` (Forgejo Actions — GitHub ignores `.forgejo/`) to publish `docker.io/datatactics/rahla`. The repo is push-mirrored to <https://github.com/dttctcs/rahla>; on a `v*` tag the mirror-side `.github/workflows/release.yaml` (guarded via `github.server_url`, inert on Forgejo) creates a GitHub Release from the matching `CHANGELOG.md` section — no build runs on GitHub; the release exists so Renovate consumers can track rahla via the `github-releases` datasource. The Maven artifact repo `https://mvn.datatactics.dev` (id `dtacs`) is required for some non-public Siddhi artifacts (`siddhi-store-rdbms` `7.0.16-dtacs`, `siddhi` `5.1.24-dt`).

**On every version bump (incl. Renovate updates), hand-update the version strings that are NOT derived from the pom:** the `README.md` lines `Current release ships **Karaf … + Camel …**` and `Rahla x.y.z ships **Camel …**`, and add a `CHANGELOG.md` entry. Also keep **`camel.version` capped at the latest published `org.apache.camel.karaf:apache-camel` features release** — `assembly/pom.xml` pulls the Karaf `apache-camel` features descriptor by `${camel.version}` (no explicit version), and the Karaf distribution lags camel-core, so don't take Renovate's camel-core version blindly (e.g. use `4.18.2`, not `4.20.0`). Always `mvn package` after a Camel bump. `renovate.json` enforces this automatically for bot PRs: updates for `org.apache.camel:*` (base camel) are disabled so the shared `${camel.version}` property tracks only `org.apache.camel.karaf` (<https://github.com/apache/camel-karaf>) — a "missing" camel PR is intentional; the cap rule above still applies to manual bumps. It also restricts `janusgraph-driver` to clean `X.Y.Z` releases (upstream ships only timestamped dev builds past 1.1.0) and sets `dependencyDashboard: true` (the global self-hosted config disables the dashboard; without the re-enable the approval-gated JRE update would surface nowhere).

## Running locally (without Docker)

After `mvn package`, the Karaf assembly under `assembly/target/assembly/` is directly runnable — useful for reproducing OSGi wiring problems without the container layer. The `bin/` scripts mirror Karaf's standard set: `karaf` (foreground), `karaf debug` (foreground + JDWP on `5005`), `start` (daemon), `stop`, `status`, `client` (SSH client to the running instance).

```sh
# foreground with debug agent
assembly/target/assembly/bin/karaf debug

# or as a background daemon
assembly/target/assembly/bin/start
assembly/target/assembly/bin/client    # admin / admin (users.properties defines only `admin`, password `${env:ADMIN_PASS:-admin}`)
assembly/target/assembly/bin/stop
```

Logs land in `assembly/target/assembly/data/log/karaf.log`. Note this layout differs from the container, where `etc/` and `deploy/` are symlinked to `/config/...` — locally they're just `assembly/target/assembly/etc/` and `.../deploy/`.

## Reactor layout (what each module is)

The root `pom.xml` is `<packaging>pom</packaging>` and lists modules in `<modules>`. Each bundle module has a sibling `features/<name>` module that emits a Karaf feature XML referencing it.

- **`rahla/`** — main OSGi bundle. Embeds Groovy 4 (private-packaged + exported, since Karaf's stock Groovy is blacklisted in the assembly) and provides `rahla.api.GroovyBeanFactory` (compiles a Groovy class from any URL into a bean usable from a Camel context). Also contributes a Karaf shell command (`log:logs`, see `rahla/commands/ShowCommand.java`).
- **`fradi/`** — custom Camel component (`fradi:`) wrapping the Siddhi CEP engine. Uses the `camel-package-maven-plugin` to generate `*Configurer.java` under `src/generated/`. Marked EOL — prefer `camel-mybatis` for new work.
- **`graphsource/`** — OSGi component, factory PID `rahla.graphsource`. Each `rahla.graphsource-<name>.cfg` becomes a `rahla.graphsource.GraphSource` service (TinkerPop/Gremlin client + GraphTraversalSource), filterable by `graphSourceName`.
- **`jedissource/`** — OSGi component, factory PID `rahla.jedissource`. Each `rahla.jedissource-<name>.cfg` becomes a `rahla.jedissource.JedisSource` service (pooled Jedis), filterable by `jedisSourceName`.
- **`camel-osgi-route-templates/`** — implements `org.apache.felix.fileinstall.ArtifactInstaller` to translate route-template YAML files dropped in `/config/deploy` into `camel.route.template` factory configurations (see `TemplateFileInstaller.java`). YAML schema: `templateId`, `sharedConfigPid` (optional), `routes[].id`, `routes[].parameters` (a flat map of template parameters).
- **`loki-appender/`** — Log4j2 appender for Grafana Loki, repackaged as a **`Fragment-Host: org.ops4j.pax.logging.pax-logging-log4j2`** bundle so it loads inside the pax-logging classloader. The pax-logging bundles come from the `rahla-logging` feature (see below), not Karaf's defaults. **Deprecated (1.3.5)** — still shipped and version-bumped, but prefer the OpenTelemetry logs exporter (`OTEL_LOGS_EXPORTER`) for new log shipping; see "Deprecated / removed".
- **`features/<name>/`** — Karaf feature XML modules (`<packaging>feature</packaging>`). The `features/rahla` feature additionally pins `io.undertow/undertow-{core,servlet}` to `2.2.39.Final` because pax-web hasn't bumped yet. The `features/logging` feature is `rahla-logging` and replaces Karaf's stock pax-logging bundles.
- **`assembly/`** — `<packaging>karaf-assembly</packaging>`; produces the Karaf distro. This is where Rahla's runtime profile is decided, and several things deserve attention before changing dependencies:
  - **`<startupFeatures>`**: `rahla-logging` (so Rahla's pax-logging replacements load before anything else), `wrap`.
  - **`<bootFeatures>`**: `standard`, `scr`, `spifly`, `pax-web-http-undertow`, `jolokia`, `rahla` — note Camel is NOT preinstalled (1.2.0 change) and `pax-web-http-undertow` replaces Jetty (1.3.0 change).
  - **`<installedFeatures>`**: `fradi`, `graphsource`, `jedissource`, `camel-route-templates` — present in the kar but not auto-started.
  - **`<blacklistedBundles>`**: Groovy 4.x (Rahla embeds its own), Woodstox 7.0.0, Undertow 2.2.37, pax-logging 2.3.0. Touch this list whenever a feature transitively pulls a conflicting version.
  - `<javase>17</javase>` is the OSGi execution-environment header for the assembly even though everything compiles to 21.

## OSGi runtime conventions

- Components use OSGi DS annotations (`@Component`, `@Activate`, `@Reference`) — no Blueprint inside Rahla's own code. The `karaf-services-maven-plugin`'s `service-metadata-generate` goal runs in `process-classes` to emit DS XML.
- Configuration-driven services use `configurationPid = "rahla.<name>"` with `ConfigurationPolicy.REQUIRE`, so they instantiate per `.cfg` file dropped in `/config/deploy` (felix.fileinstall picks them up).
- User-deployed Camel contexts are XML/YAML files in `/config/deploy` and reference services via `<reference interface="..." filter="..."/>`. The README has working examples for `GroovyBeanFactory`, `GraphSource`, `JedisSource`, `FradiComponent`.
- Lombok is used widely (`@Log4j2`, etc.); `maven.compiler.proc=full` is set in the parent POM so annotation processors run.

## Deprecated / removed (stay out unless intentionally restoring)

- `RAHLA_DEPLOY_PATH` env var — gone; use `org.apache.felix.fileinstall-*.cfg` instead.
- `/rahla/deploy` and `/deploy` paths — only `/config/deploy` is monitored; the s6 init script under `root/etc/s6-overlay/s6-rc.d/init-rahla/run` warns on the legacy paths.
- `resource:file:` URL prefix in `GroovyBeanFactoryImpl` and `FradiComponent` — deprecated; new code should pass real URLs (`file://`, `http://`).
- The Fradi/Siddhi component is marked EOL.
- The `loki-appender` (Grafana Loki Log4j2 appender) is deprecated (1.3.5) — kept working and still version-bumped, but new/additional log shipping should use the OpenTelemetry logs exporter (`OTEL_LOGS_EXPORTER`), not the `<Loki/>` appender.
