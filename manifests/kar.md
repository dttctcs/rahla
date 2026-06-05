# Deploying a Rahla project as a KAR

A project's `deploy/` folder holds the `features.xml` plus runtime files (Camel XMLs, `.cfg`, Groovy,
…). **dev:** mount it (`-v $PWD/deploy:/config/deploy`). **prod:** ship it offline via a KAR.

All three approaches reuse one build pom, fetched over HTTP (no Maven publish, no token):
`https://codeberg.org/datatactics/rahla/raw/branch/main/manifests/feature-kar.xml`

It auto-detects the feature from `features.xml` (no `-D` needed), resolves its full bundle closure
(camel, pax-jdbc-oracle/ojdbc8, …) into `target/closure/repository`, packs `<feature>.kar`, and
stubs out features rahla already ships (fradi, graphsource, jedissource, camel-route-templates).
Build needs **JDK ≥ 22**; versions default to rahla 1.3.2 (override `-Dkar.karaf.version` /
`-Dkar.camel.version`).

## 1 — build & deploy the KAR

Bundles stay zipped in the `.kar` → **Trivy image scans can't see them**.

```dockerfile
FROM docker.io/library/maven:3.9-eclipse-temurin-23 AS build
WORKDIR /build
ADD https://codeberg.org/datatactics/rahla/raw/branch/main/manifests/feature-kar.xml ./pom.xml
COPY deploy/features.xml ./deploy/features.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f pom.xml package

FROM docker.io/datatactics/rahla:1.3.2
COPY --from=build --chown=911:911 /build/target/*.kar /config/deploy/
COPY --chown=911:911 deploy /config/deploy
RUN rm -f /config/deploy/features.xml      # the KAR is the feature
```

## 2 — pre-extract into the image (recommended)

Same offline result, but bundles are loose jars in `system/` → **Trivy-scannable**. `features.xml`
stays and triggers the install from the pre-seeded `system/`.

```dockerfile
FROM docker.io/library/maven:3.9-eclipse-temurin-23 AS build
WORKDIR /build
ADD https://codeberg.org/datatactics/rahla/raw/branch/main/manifests/feature-kar.xml ./pom.xml
COPY deploy/features.xml ./deploy/features.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f pom.xml package \
 && mkdir overlay && (cd overlay && for k in /build/target/*.kar; do jar xf "$k" repository; done)

FROM docker.io/datatactics/rahla:1.3.2
COPY --from=build --chown=911:911 /build/overlay/repository/ /app/rahla/system/
COPY --chown=911:911 deploy /config/deploy
```

## 3 — check locally with Trivy (no image)

See what *would* be installed. Run from the project repo:

```sh
curl -fsSL https://codeberg.org/datatactics/rahla/raw/branch/main/manifests/feature-kar.xml -o /tmp/fk-pom.xml
mkdir -p /tmp/fk/deploy && cp deploy/features.xml /tmp/fk/deploy/ && cp /tmp/fk-pom.xml /tmp/fk/pom.xml
( cd /tmp/fk && mvn -B -q -f pom.xml prepare-package )      # resolves the closure
trivy rootfs --scanners vuln /tmp/fk/target/closure/repository
```

Use `trivy rootfs`, **not `trivy fs`** — `fs` only reads pom/lockfiles and finds nothing in a folder
of loose jars.

## Notes

- fradi/siddhi/graphsource/jedissource/camel-route-templates are **not** in the KAR — rahla provides
  them offline from its own `system/` (hence the stubs).
- `camel-jetty` pulls Eclipse Jetty (12.1.6, HIGH CVE) although rahla serves HTTP via undertow — drop
  `<feature>camel-jetty</feature>` if your routes don't use it.
- `wrap:` bundles with broken manifests (e.g. Camunda DMN on some branches) are **not** reliably
  packed by the closure tooling — needs a separate solution (real Karaf resolver, or keep online).
- The pom is fetched from `main`; pin via `-D…` or a tag if a project targets an older rahla.
