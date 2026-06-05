# Deploying a Rahla project as a KAR

A Rahla project ships a `deploy/` folder: a `features.xml` (the OSGi features to install) plus the
runtime artifacts that Karaf hot-loads — Camel/Blueprint XMLs, `.cfg`, Groovy processors, MyBatis
mappers, SQL, DMN, etc.

- **dev:** bind-mount `deploy/` into a running rahla container (`-v $PWD/deploy:/config/deploy`).
  Everything is downloaded/installed online and hot-reloads on edit.
- **int/prod:** ship it self-contained so it installs **offline**. That's what the three approaches
  below are for.

## The shared build pom

All approaches reuse one build pom that lives in this repo and is fetched over HTTP from the public
codeberg mirror (no Maven publishing, no token):

```
https://codeberg.org/datatactics/rahla/raw/branch/main/manifests/feature-kar.xml
```

What it does, given a project's `deploy/features.xml`:

- **auto-detects the feature** from the first `<feature name="…">` (gmavenplus) — projects pass **no
  `-D` properties**. Override with `-Dkar.feature=…` only to pick a non-first feature.
- resolves that feature's **full bundle closure** (camel, pax-jdbc-oracle incl. ojdbc8, …) into
  `target/closure/repository`, and packs a KAR named `<feature>.kar`.
- **stubs out features rahla already ships** (`fradi`, `graphsource`, `jedissource`,
  `camel-route-templates`) so the build needs only Maven Central (no dtacs repo) and the KAR carries
  only the *delta* rahla lacks. rahla provides those (and `camel-core`, fradi/siddhi) from its own
  `system/` at runtime.
- version defaults track the target rahla (`kar.karaf.version=4.4.11`, `kar.camel.version=4.18.1`
  = rahla 1.3.2). Override with `-D…` if you target a different rahla.

> Build requirement: the karaf tooling needs **Java ≥ 22** (recent Maven enforces it) — use a
> JDK 22+ Maven image. The resolved bundles are plain jars, so the runtime JDK is unaffected.

---

## Approach 1 — build the KAR and deploy it

The classic Karaf way: build the `.kar`, drop it in `/config/deploy`. On boot the KAR deployer
extracts its bundles into `system/` and installs the feature.

```dockerfile
# syntax=docker/dockerfile:1
FROM docker.io/library/maven:3.9-eclipse-temurin-23 AS build
WORKDIR /build
ADD https://codeberg.org/datatactics/rahla/raw/branch/main/manifests/feature-kar.xml ./pom.xml
COPY deploy/features.xml ./deploy/features.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f pom.xml package

FROM docker.io/datatactics/rahla:1.3.2
COPY --from=build --chown=911:911 /build/target/*.kar /config/deploy/
COPY --chown=911:911 deploy /config/deploy
RUN rm -f /config/deploy/features.xml   # the KAR *is* the feature now; keeping the raw file installs it twice
```

- **+** simplest mental model, one self-contained `.kar` artifact.
- **−** the bundles live *inside* the `.kar` (a zip). **Trivy does not look inside it** → image
  scans miss those bundles. Bundles only land in `system/` at first boot.

## Approach 2 — pre-extract the KAR into the image (recommended)

Extract the KAR's bundles into Karaf's `system/` repo **at build time**. Same offline result, but the
bundles are loose jars in the image, so **Trivy (image scan) sees them**. The `features.xml` stays in
`deploy/` and triggers the install on boot, resolving from the pre-seeded `system/`.

```dockerfile
# syntax=docker/dockerfile:1
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

- **+** offline **and** Trivy-scannable (loose jars in `system/`); no first-boot extraction.
- **−** marginally larger image layer (the jars vs. the zipped `.kar`).

## Approach 3 — resolve locally and scan with Trivy (no image)

Check *what would be installed* — without building or extracting an image. The script
`feature-kar-libs.sh` resolves the closure into a tmp folder and runs Trivy on it:

```sh
# from a project repo (e.g. fuel-service), using the script from this repo:
bash <(curl -fsSL https://codeberg.org/datatactics/rahla/raw/branch/main/manifests/feature-kar-libs.sh) deploy/features.xml
# or a local checkout:
manifests/feature-kar-libs.sh [features.xml] [out-dir]   # defaults: deploy/features.xml -> /tmp/feature-kar-libs
```

It fetches the shared pom, runs `mvn prepare-package` (auto-detect, no `-D`), writes the resolved
jars to the out-dir, and scans them.

> **Use `trivy rootfs`, not `trivy fs`.** `trivy fs` only reads dependency manifests (pom.xml,
> lockfiles) and finds **nothing** in a folder of loose jars; `trivy rootfs` walks the tree and
> detects each `.jar`. The script already uses `rootfs`.

```sh
trivy rootfs --scanners vuln /tmp/feature-kar-libs
```

- **+** fast feedback in CI/pre-commit; no Docker; shows exactly the delta the KAR adds.
- **−** scans only the project's delta (rahla-provided bundles like fradi/siddhi are scanned via the
  rahla base image, not here).

---

## Notes

- **fradi / siddhi / graphsource / jedissource / camel-route-templates** are *not* in the KAR — rahla
  ships them and resolves them offline from its own `system/`. That's why they're stubbed in the pom.
- **camel-jetty** drags in Eclipse Jetty (currently 12.1.6, a HIGH-severity CVE) even though rahla
  serves HTTP via undertow. If your routes don't use Jetty, drop `<feature>camel-jetty</feature>`
  from your `features.xml` to eliminate those findings.
- **`wrap:` bundles with broken manifests** (e.g. the Camunda DMN bundles used by some branches) are
  *not* reliably packed by the Maven closure tooling. If a feature needs them offline, that needs a
  separate solution (real Karaf resolver at build time, or leaving that feature online).
- **Versions** come from `main`. If a project pins an older rahla, override `-Dkar.karaf.version` /
  `-Dkar.camel.version`, or fetch the pom from a matching tag instead of `main`.
