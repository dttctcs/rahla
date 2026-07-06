# Rahla — Migrations-Doku (ab 1.2.1)

Agent-Referenz für das Durchführen einer Rahla-Migration. Pro Version ein Abschnitt mit
**Breaking Changes**, **Migrationsschritten** und **Stolpersteinen**. Jede konkrete Aussage ist
mit Quelle belegt (Commit-Hash, `CHANGELOG.md`-Eintrag oder Datei). Nicht aus dem Repo Belegbares
ist als **„aus Repo nicht belegt"** bzw. **„zu verifizieren"** markiert — nichts davon raten.

Begriffe wie in `rahla-developer/SKILL.md` und `CLAUDE.md`: das Deploy-Verzeichnis ist die API
(`*.xml` Camel-Blueprint, `*.groovy`, `*.cfg`, `*.yaml` Route-Templates, `*.jar`, `*.siddhi`),
OSGi-DS statt Blueprint im Rahla-eigenen Code, `rahla.api.GroovyBeanFactory`, Faktor-PIDs
`rahla.graphsource` / `rahla.jedissource`, `TemplateFileInstaller`, Fradi/Siddhi = EOL.

---

## 1. Überblick + Versionsmatrix

| Version | Tag-Datum    | Camel    | Karaf   | Base-Image                                   | Kernänderung |
|---------|--------------|----------|---------|----------------------------------------------|--------------|
| 1.2.1   | 2025-07-15   | 4.10.3   | 4.4.7   | `eclipse-temurin:21-jre-alpine`              | Route-Templates in eigenes Feature `camel-route-templates` ausgelagert |
| 1.2.2   | 2025-08-19   | 4.10.3   | 4.4.7   | `eclipse-temurin:21-jre` (kein Alpine mehr)  | Base-Image: alpine → glibc/Debian-basiertes Temurin |
| 1.2.3   | 2025-08-22   | 4.10.3   | 4.4.7   | `eclipse-temurin:21-jre`                     | Fix: `adduser` → `useradd` (uid 1001) |
| 1.2.4   | 2025-12-09   | 4.10.7   | 4.4.8   | `linuxserver/baseimage-debian:trixie` (LSIO) | **Erster Wechsel auf linuxserver.io** + diverse Dep-Bumps |
| 1.3.0   | 2026-02-17   | 4.10.7   | 4.4.9   | `linuxserver/baseimage-alpine:3.23` (LSIO)   | **Großes Breaking-Release:** LSIO-Konventionen, Pfad-Umzug, Undertow statt Jetty, Camel nicht vorinstalliert |
| 1.3.1   | 2026-04-30   | **4.18.1** | 4.4.9 | `linuxserver/baseimage-alpine:3.23`          | **Camel-Sprung 4.10 → 4.18**; viele Component-Bugfixes; Jedis `@Deprecated` |
| 1.3.2   | 2026-05-02   | 4.18.1   | **4.4.11** | `linuxserver/baseimage-alpine:3.23`        | Karaf 4.4.11; Fix `org.ops4j.pax.url.mvn.cfg` Repo-Eintrag |
| 1.3.3   | 2026-06-10   | 4.18.2   | 4.4.11  | `linuxserver/baseimage-alpine:3.23`          | Cert-Import-Service, pax-url-Repo nach `/config/.m2` gepinnt, KAR-Offline-Packaging entfernt |

Quelle Camel/Karaf-Spalte: `git show <tag>:pom.xml` (`<camel.version>` / `<karaf.version>`).
Quelle Tag-Daten: `git log -1 --format=%ci <tag>`.
Quelle Base-Image: jeweilige `Dockerfile`-Diffs (Commits unten).

**Versionssprung gesamt (v1.2.1 → v1.3.3, belegt durch `git diff v1.2.1 v1.3.3 -- pom.xml`):**
Camel `4.10.3 → 4.18.2`, Karaf `4.4.7 → 4.4.11`.

---

## 2. Pro-Version-Abschnitte

### 1.2.1 (2025-07-15) — Route-Templates ausgelagert

**Breaking Changes**
- Camel-OSGi-Route-Templates sind aus dem Standard heraus in ein eigenes Feature
  `camel-route-templates` verschoben. Quelle: `CHANGELOG.md` 1.2.1.

**Migrationsschritte**
- Wer Route-Template-YAMLs (`TemplateFileInstaller`) nutzt: Feature
  `<feature>camel-route-templates</feature>` explizit installieren bzw. in die `features.xml`
  im Deploy aufnehmen.

**Stolpersteine**
- Ist seit 1.2.0 ohnehin Camel selbst nicht mehr vorinstalliert (`CHANGELOG.md` 1.2.0); Templates
  ohne installiertes Camel + Template-Feature laden nicht.

---

### 1.2.2 (2025-08-19) — Base-Image Alpine → Debian-Temurin

**Breaking Changes**
- Base-Image gewechselt von `eclipse-temurin:21-jre-alpine` auf `eclipse-temurin:21-jre`
  (glibc statt musl). Quelle: Commit `c18deab` „fix: chnaged base dockerlayer" (Dockerfile-Diff:
  `FROM docker.io/eclipse-temurin:21-jre-alpine` → `FROM docker.io/eclipse-temurin:21-jre`);
  `CHANGELOG.md` 1.2.2.

**Migrationsschritte**
- Keine Anwendungsänderung nötig. Relevant nur, wenn eigene Image-Layer Alpine-spezifisch waren
  (`apk`, musl). Auf das Debian-Image gehören `apt`-basierte Layer.

**Stolpersteine**
- Image wird deutlich größer (glibc-Basis statt Alpine).

---

### 1.2.3 (2025-08-22) — User-Anlage korrigiert

**Breaking Changes**
- Keine funktionalen für Routen/Deploys.

**Migrationsschritte**
- User-Anlage gefixt: `adduser -D ... -u 101 rahla` → `useradd -m -d /rahla -u 1001 rahla`.
  Quelle: Commit `d303c62` „fix: useradd"; `CHANGELOG.md` 1.2.3 („Fixed invalid useradd").
- Achtung: Run-User-UID ändert sich auf **1001**. Bei Bind-Mounts/Volumes mit festen Ownerships
  ggf. `chown` anpassen. (Diese UID-Welt gilt nur bis 1.2.x — ab 1.3.0 LSIO mit PUID/PGID, s.u.)

---

### 1.2.4 (2025-12-09) — Erster Wechsel auf linuxserver.io (Debian/trixie)

**Breaking Changes**
- Base-Image gewechselt auf `ghcr.io/linuxserver/baseimage-debian:trixie`. Quelle: Commit
  `89c91ad` „switched to linux servers base image" (Dockerfile-Diff); `CHANGELOG.md` 1.2.4.
- JRE wird im Dockerfile nun selbst aus dem Adoptium-APT-Repo installiert (`temurin-21-jre`),
  nicht mehr vom Temurin-Base-Image geerbt. Quelle: `89c91ad` Dockerfile-Diff (`apt install ...
  temurin-21-jre`).
- Run-User wechselt in die LSIO-Welt: `chown 911:911 /rahla`, `COPY --chown=911:911 ...`,
  `COPY root /` (s6-overlay-Strukturen). Quelle: `89c91ad` Dockerfile-Diff.

**Migrationsschritte**
- Volume-Ownerships von UID 1001 (1.2.3) auf **911** umstellen (LSIO `abc`-User).
- Eigene Image-Layer auf das LSIO-Debian-Image (s6-overlay-`init`-Mechanik) ausrichten statt auf
  ein nacktes Temurin-Image.

**Stolpersteine**
- In diesem Stand zeigt der Dockerfile noch `EXPOSE 8101 1099 44444 8181 8182` und Pfad `/rahla`
  (noch **nicht** `/app/rahla` + `/config/...` — das kommt erst 1.3.0). Quelle: `89c91ad`.
- `KARAF_SYSTEM_OPTS` referenziert hier `opentelemetry-javaagent-2.12.0.jar`; dep-bumps in
  1.2.4 hoben den OTel-Agent auf `2.22.0` (`CHANGELOG.md` 1.2.4). **Filename-vs-Version-Drift**
  ist der wiederkehrende Stolperstein (vgl. 1.3.3-Bugfix).

**Dependency-Bumps (Quelle `CHANGELOG.md` 1.2.4):** karaf 4.4.7→4.4.8, camel 4.10.3→4.10.7,
groovy 4.0.25→4.0.29, jackson2 2.18.2→2.20.1, pax-logging 2.2.8→2.3.1, lombok 1.18.36→1.18.42,
commons-configuration2 2.12.0→2.13.0, log4j2 2.24.3→2.25.2, jedis 5.2.0→7.1.0,
opentelemetry_agent 2.12.0→2.22.0, otel-sdk-extension-autoconfigure 1.21.0-alpha→1.57.0.

> **Jedis 5.2.0 → 7.1.0 ist ein Major-Sprung über zwei Major-Versionen.** Im Repo kein expliziter
> Migrationshinweis dokumentiert → **zu verifizieren**, ob eigene `JedisSource`-Nutzungen mit der
> Jedis-7-API kompatibel sind. (Jedis ist ab 1.3.1 ohnehin `@Deprecated`, s.u.)

---

### 1.3.0 (2026-02-17) — Großes Breaking-Release (LSIO-Alpine, Pfad-Umzug, Undertow)

Markiert in `CHANGELOG.md` ausdrücklich als **„Changes *BREAKING*"**.

**Breaking Changes** (Quelle: `CHANGELOG.md` 1.3.0 + Commits `fb10df6`, `56ed454`, `84cf846`, `e600d75`)
1. Base-Image auf `ghcr.io/linuxserver/baseimage-alpine:3.23` (zurück auf Alpine, jetzt LSIO).
   JRE-Block (Adoptium-Download + gpg-Verify + ESUM) wird im Dockerfile selbst gepflegt; Temurin
   `jdk-21.0.10+7`. Quelle: `fb10df6` Dockerfile-Diff; `CHANGELOG.md` 1.3.0.
2. **LSIO-Pfad-Konventionen (Breaking):**
   - Rahla von `/rahla` nach **`/app/rahla`** verschoben.
   - `/app/rahla/deploy` ist ein Symlink auf **`/config/deploy`**.
   - `/app/rahla/etc` ist ein Symlink auf **`/config/etc`** (etc-Move + Symlink in `56ed454`).
   - **`/rahla/deploy` wird nicht mehr gescannt.**
   Quelle: `CHANGELOG.md` 1.3.0; `fb10df6` (`rm -rf /app/rahla/deploy; mkdir -p /config/deploy;
   ln -s /config/deploy /app/rahla/deploy`); `56ed454` (etc nach `/config/etc`).
3. **`RAHLA_DEPLOY_PATH` entfernt (Breaking).** Für ein zusätzliches Watch-Verzeichnis stattdessen
   eigene `org.apache.felix.fileinstall-<name>.cfg`. Quelle: `CHANGELOG.md` 1.3.0; Commit
   `84cf846` „removed RAHLA_DEPLOY_PATH and added warning".
4. **`pax-web-http-jetty` durch `pax-web-http-undertow` ersetzt (Breaking).** Boot-HTTP-Runtime
   ist jetzt Undertow. Quelle: `CHANGELOG.md` 1.3.0; Commit `e600d75` „replaced pax-web-jetty with
   undertow ..." (`assembly/pom.xml`, `features/rahla/.../feature.xml`, `pom.xml`).
5. **pax-logging der Karaf-Defaults durch `rahla-logging`-Feature ersetzt.** Quelle: `CHANGELOG.md`
   1.3.0; Commit `e600d75` (`features/logging/pom.xml`).
6. Kubernetes-Manifest `manifests/rahla.yaml` hinzugefügt. Quelle: `CHANGELOG.md` 1.3.0; Commit
   `fb10df6` / `b357d08` „added rahla.yaml".

**Migrationsschritte**
- Alle Deploy-Artefakte nach `/config/deploy` mounten (nicht mehr `/rahla/deploy` oder `/deploy`).
- Eigene `etc`-Overrides nach `/config/etc` mounten.
- In Blueprint-XML enthaltene absolute Pfade (z. B. Groovy-URLs `file:///rahla/deploy/...`) auf
  **`file:///config/deploy/...`** umschreiben. Vgl. `rahla-developer/SKILL.md` (Pfad-Tabelle).
- REST-Configs auf Undertow umstellen: `<restConfiguration component="undertow" .../>` (statt
  `jetty`). Jetty bleibt installierbar (`pax-web-http-jetty`), aber nicht mehr Default; nur **eine**
  Pax-Web-HTTP-Feature gleichzeitig. Quelle: `README.md` HTTP/REST-Abschnitt; `rahla-developer/SKILL.md`.
- `RAHLA_DEPLOY_PATH` aus Run-Configs/Compose/K8s entfernen; bei Bedarf eines zweiten
  Verzeichnisses eine `org.apache.felix.fileinstall-<name>.cfg` deployen.
- Container-Run jetzt LSIO-typisch über **PUID/PGID** (Default `911`) statt fixem User; Volume-
  Ownership entsprechend. Quelle: `README.md` „LSIO base image"-Hinweis.

**Stolpersteine**
- Der s6-Init `root/etc/s6-overlay/s6-rc.d/init-rahla/run` warnt aktiv bei gesetztem
  `RAHLA_DEPLOY_PATH` sowie bei Dateien in `/deploy` bzw. vorhandenem `/rahla/deploy` — diese
  Warnungen im Log sind das Migrations-Signal. (Der Warntext nannte fälschlich „removed since
  version 1.3.5"; korrigiert auf 1.3.0 am 2026-07-06, zusammen mit dem Tippfehler
  `org.apachle.felix` → `org.apache.felix`.) Quelle: `git show HEAD:root/.../init-rahla/run`.
- Camel ist (seit 1.2.0) weiterhin **nicht** vorinstalliert — ein gedropptes `*.xml` ohne Camel-
  Feature bleibt `Waiting` (`bundle:diag <id>`). Quelle: `README.md` FAQ; `rahla-developer/SKILL.md`.
- Loki-Appender braucht die `rahla-logging`-pax-logging-Bundles; pax-logging **nicht** von
  woanders re-pinnen. Quelle: `CLAUDE.md` / `rahla-developer/SKILL.md`.

---

### 1.3.1 (2026-04-30) — Camel-Sprung 4.10 → 4.18 + Component-Bugfixes

**Breaking Changes / wichtigste Änderung**
- **Camel `4.10.7 → 4.18.1`.** Quelle: `CHANGELOG.md` 1.3.1 („camel: 4.10.7 > 4.18.1");
  `git show v1.3.1:pom.xml` (`<camel.version>4.18.1`). Das ist der einzige Camel-Minor-Sprung im
  betrachteten Bereich und der mit Abstand größte API-relevante Schritt. Details s. Abschnitt 3.
- `JedisSource` / `JedisServiceImpl` als **`@Deprecated`** markiert (laut Runtime-Warnung ohnehin
  EOL). Quelle: `CHANGELOG.md` 1.3.1.

**Bugfixes mit Migrations-Relevanz** (Quelle: `CHANGELOG.md` 1.3.1)
- `JedisServiceImpl`: kaputter Default für `host` (`"65536"` → `localhost`).
- `JedisServiceImpl`: Connect-Retry-Loop schluckt `InterruptedException` nicht mehr (Interrupt-Flag
  wird restauriert).
- `GraphSourceImpl`: kein doppelter `Cluster`-Build mehr im activate; `deactivate` NPE'd nicht mehr.
- `GroovyBeanFactoryImpl`: `InputStream` via try-with-resources geschlossen; deprecated
  `new URL(String)` → `URI.create(...).toURL()`.
- `OsgiRouteTemplateParameterSource`: fehlerhafte `camel.route.template.*`-Keys werden
  übersprungen statt `StringIndexOutOfBoundsException` zu werfen.
- `TemplateFileInstaller`: kaputte Log-Messages gefixt, YAML-Parser wird geschlossen.

**Migrationsschritte**
- Camel-Routen auf Kompatibilität mit Camel 4.18 prüfen — **immer `mvn package`** nach Camel-Bump
  (`CLAUDE.md`). Konkrete API-Bruchstellen: s. Abschnitt 3 (Querschnitt Camel-Upgrade-Pfad).
- Wer `JedisSource` nutzt: Migration einplanen (deprecated). Empfehlung im Repo (`CHANGELOG.md`
  1.2.0): für DB-CRUD `camel-mybatis` statt Siddhi/Fradi — analog für neue Arbeit keine neuen
  Jedis-Abhängigkeiten.

**Stolpersteine**
- Tag `v1.3.1` wurde via `maven-release-plugin` einmal zurückgerollt (`6e02780` „rollback the
  release of v1.3.1", danach `f924e07` „prepare release v1.3.1"). Beim Auschecken/Vergleichen den
  endgültigen Tag verwenden, nicht die Rollback-Zwischenstände.
- POM-/Properties-Cleanup in 1.3.1 entfernte tote Properties (`java.version`,
  `opentelemetry.version`, Groovy-`indy`-Klassifizierer u. a.). Wer im eigenen Build diese
  Properties referenziert hat, muss nachziehen. Quelle: `CHANGELOG.md` 1.3.1 „Build / POM cleanup".

**Weitere Dep-Bumps (Quelle `CHANGELOG.md` 1.3.1):** jackson2 2.21.0→2.21.3, pax-logging
2.3.2→2.3.3, lombok 1.18.42→1.18.46, log4j2 2.25.3→2.25.4, jedis 7.2.1→7.5.0,
loki-appender 0.9.41→0.9.42, otel-sdk-extension-autoconfigure 1.59.0→1.61.0.

---

### 1.3.2 (2026-05-02) — Karaf 4.4.11 + pax-url-Repo-Fix

**Breaking Changes**
- Keine API-Breaks; Boot-Fix.

**Migrationsschritte / Bugfix** (Quelle: `CHANGELOG.md` 1.3.2; Commit `01a0b1a` „updated karaf")
- Karaf `4.4.9 → 4.4.11`.
- `etc/org.ops4j.pax.url.mvn.cfg`: leerer Repository-Eintrag, der die Boot-Feature-Auflösung auf
  Karaf 4.4.11 brach, gefixt. Der `pax-url-aether`-Parser strippt das führende `+`-Flag aus
  `org.ops4j.pax.url.mvn.repositories`; der bisherige Wert ` +,` hinterließ ein leeres erstes
  Element, das der (jetzt strengere) `MavenRepositoryURL`-Konstruktor mit
  `Repository spec is empty string.` ablehnt. Neuer Wert: `+ ` (ohne Komma).

**Stolpersteine**
- Wer eine **eigene** `org.ops4j.pax.url.mvn.cfg` in `/config/etc` gemountet hat, muss denselben Fix
  selbst nachziehen, sonst bricht der Boot auf Karaf 4.4.11. Quelle: `CHANGELOG.md` 1.3.2.

---

### 1.3.3 (2026-06-10) — Cert-Import-Service, pax-url nach /config/.m2, KAR-Offline raus

**Breaking Changes**
- **KAR-basiertes Offline-Packaging entfernt** (`manifests/feature-kar.xml`). Offline-Images
  primen jetzt den OSGi-Cache zur Build-Zeit (boot once → install → Cache shippen) statt eine
  KAR-Closure aufzulösen. Quelle: `CHANGELOG.md` 1.3.3 „Removed"; Commit `f233c89` „remove KAR
  offline machinery".

**Migrationsschritte**
- **Custom CA-Zertifikate (neu, einfacher):** PEM/`.crt` nach **`/config/certs`** droppen; der neue
  s6-Oneshot `init-rahla-certs` importiert sie vor dem Karaf-Start idempotent in den JVM-Truststore
  (`cacerts`), Alias aus der Cert-CN abgeleitet — kein hand-gebautes JKS mehr nötig. Läuft vor
  `svc-rahla`. Quelle: `CHANGELOG.md` 1.3.3; Commit `ee97ef7`;
  `root/etc/s6-overlay/s6-rc.d/init-rahla-certs/run`.
- **pax-url localRepository auf `/config/.m2` gepinnt** (nur im `Dockerfile`, **nicht** in der
  Assembly-`org.ops4j.pax.url.mvn.cfg`, damit lokale Nicht-Container-Runs unberührt bleiben);
  Verzeichnis vorab als `abc` angelegt. Feature-Install-Downloads landen damit deterministisch und
  persistent statt in einem mehrdeutigen `~/.m2`. Quelle: `CHANGELOG.md` 1.3.3; Commit `36e8844`.
- Offline-/Air-gapped-Images von KAR-Closure auf das „boot once + Cache shippen"-Muster umstellen.
  Vorlage `Dockerfile.airgap` steht in `README.md` (Abschnitt „Air-gapped / offline images").
- JRE auf Temurin `21.0.11+10` (Quelle: `CHANGELOG.md` 1.3.3; Commit `ee97ef7`). Beim manuellen
  JRE-Bump immer alle gekoppelten Stellen synchron halten — s. Abschnitt 4.

**Bugfixes — „main baute vorher nicht"** (Quelle: `CHANGELOG.md` 1.3.3; Commit `92d32ae`)
- `JedisServiceImpl`: SSL-Support-Change nutzte `Dictionary.getOrDefault(...)` (existiert nur auf
  `Map`, nicht `Dictionary`) → kompilierte nicht; jetzt über `boolProp()`-Helper.
- `Dockerfile`: `KARAF_SYSTEM_OPTS` zeigte noch auf `opentelemetry-javaagent-2.22.0.jar`, während
  die Assembly `2.26.1` shippt → `-javaagent`-Pfad existierte nicht, JVM bootete nicht; auf
  `2.26.1` synchronisiert.

**Stolpersteine**
- Camel bewusst auf `4.18.2` gehalten (nicht 4.20.x), weil die Karaf-`apache-camel`-Features, die
  die Assembly braucht, für 4.20.x nicht published sind. **Renovate-Camel-Vorschläge nicht blind
  übernehmen** — `camel.version` an der jeweils neuesten published
  `org.apache.camel.karaf:apache-camel`-Features-Release deckeln. Quelle: `CHANGELOG.md` 1.3.3;
  `CLAUDE.md`. **Update 2026-07:** in `renovate.json` automatisiert — eine packageRule deaktiviert
  Updates für `org.apache.camel:*` (base camel), sodass die geteilte `${camel.version}`-Property nur
  noch `org.apache.camel.karaf` folgt; Renovate schlägt den zu-hohen camel-core-Bump gar nicht mehr
  vor. Für **manuelle** Bumps gilt die Deckel-Regel unverändert.
- Renovate-Policy: nur Minor/Patch werden als PR geöffnet, keine Major; Temurin-JRE wird via
  customManager nur **überwacht** (Dependency-Dashboard, approval-gated — Renovate kann die
  ESUM-Checksums nicht neu berechnen). Siddhi (EOL) und `wagon-ssh-external` werden ignoriert.
  Quelle: `CHANGELOG.md` 1.3.3; `CLAUDE.md`. **Update 2026-07:** zusätzlich ist
  `org.janusgraph:janusgraph-driver` per `allowedVersions` auf saubere `X.Y.Z`-Releases beschränkt
  (Upstream published nur Timestamped-Dev-Builds jenseits 1.1.0), und `dependencyDashboard: true`
  ist repo-lokal gesetzt (die globale Self-hosted-Config deaktiviert das Dashboard sonst — ohne
  Re-Enable würde das approval-gated JRE-Update nirgends auftauchen).

**Weitere Dep-Bumps (Quelle `CHANGELOG.md` 1.3.3):** OTel-Agent 2.22.0→2.26.1, Groovy 4.0.30→4.0.32,
Jackson 2.21.3→2.22.0, Log4j2 2.25.4→2.26.0, pax-logging 2.3.3→2.3.4, Woodstox stax2-api 4.2.2→4.3.0,
commons-collections4 4.4→4.5.0, commons-configuration2 2.13.0→2.15.1, TinkerPop/Gremlin 3.7.3→3.8.1,
Jedis 7.5.0→7.5.2, OTel-SDK-extension-autoconfigure 1.61.0→1.63.0, Agrona 1.12.0→1.23.1.

> **Hinweis Status 1.3.3:** ~~Im `CHANGELOG.md` ist 1.3.3 als „(unreleased)" überschrieben~~ —
> **behoben 2026-07-06:** Header auf `# 1.3.3 (2026-06-10)` datiert (= Tag-Datum von `v1.3.3`);
> darüber liegt jetzt `# 1.3.4 (unreleased)` mit den ersten Renovate-Bumps (Jedis `7.5.2 > 7.5.3`,
> Log4j2 `2.26.0 > 2.26.1` — Build + lokaler Karaf-Boot verifiziert).

---

## 3. Querschnitt: Camel-Upgrade-Pfad (4.10 → 4.18)

**Belegte Versionsabfolge** (Quelle: `git show <tag>:pom.xml`, `<camel.version>`):

| Bei Version | Camel  |
|-------------|--------|
| 1.2.1–1.2.3 | 4.10.3 |
| 1.2.4–1.3.0 | 4.10.7 |
| 1.3.1       | 4.18.1 |
| 1.3.2       | 4.18.1 |
| 1.3.3       | 4.18.2 |

- Innerhalb 4.10.x (`4.10.3 → 4.10.7`, in 1.2.4): reiner Patch-Bump.
- **Der relevante Sprung ist 4.10.7 → 4.18.1 in 1.3.1** (sieben Minor-Versionen auf einmal,
  innerhalb Camel 4.x — kein 3→4-Major-Wechsel). Quelle: `CHANGELOG.md` 1.3.1.

> **Hinweis zur Kausalität:** Das Rahla-**Repo** dokumentiert zu 1.3.1 keine Anpassungen, die explizit
> als Folge des Camel-Sprungs beschrieben sind (die 1.3.1-Fixes an `OsgiRouteTemplateParameterSource`,
> `TemplateFileInstaller`, `GroovyBeanFactoryImpl`, `GraphSourceImpl`, `JedisServiceImpl` sind
> eigenständige Bugfixes). Die folgenden Breaking Changes stammen aus den **offiziellen Apache-Camel-
> Upgrade-Notes** (Quellen unten) und gelten für **eigene Camel-Routen im Deploy-Verzeichnis**
> (Blueprint-XML, Groovy, Route-Template-YAMLs). Rahla-eigener Code nutzt OSGi-DS, nicht Blueprint
> (`CLAUDE.md`); Blueprint-XML bleibt die Integrations-Oberfläche für Deployments.

### 3.1 Für rahla-Deployments wahrscheinlich relevante Breaking Changes

Beim Heben eigener Routen von 4.10 auf 4.18 zuerst prüfen (Quellen je Punkt in 3.2):

- **Simple-Sprache — Wort-Operatoren mit Leerzeichen umbenannt (4.18):** `"not contains"`→`"!contains"`,
  `"not regex"`→`"!regex"`, `"not range"`→`"!range"`, `"starts with"`→`"startsWith"`,
  `"ends with"`→`"endsWith"`. Betrifft jede `<simple>`-Predicate in Blueprint-Routen.
- **YAML-Route-Templates — kebab-case entfernt (4.13):** nur noch camelCase (`setBody` statt `set-body`).
  Direkt relevant für `TemplateFileInstaller`-YAMLs im Deploy.
- **REST DSL:** `bearer`→`bearerToken` in XML/YAML (4.12); ab 4.18 erzeugt contract-first REST-DSL
  **einen Router pro API-Endpoint** statt einem gemeinsamen (Verhaltensänderung).
- **HeaderFilterStrategy:** ab 4.12 **case-insensitive by default**, und das Filtern von Keys mit
  Legacy-Präfix `org.apache.camel.` entfällt. `UndertowHeaderFilterStrategy` ist deprecated zugunsten
  `HttpHeaderFilterStrategy` (4.11) — relevant, da rahla seit 1.3.0 Undertow nutzt.
- **package-scan-Klassen verschoben (4.12):** von `camel-base-engine` nach `camel-support`,
  neues Package `org.apache.camel.support.scan` (falls eigener Code das referenziert).
- **`transform`-EIP:** Data-Type-Variante ausgelagert in neues `transformDataType`-EIP (4.17) —
  `transform(new DataType(...))` → `transformDataType(...)`.
- **camel-main Observability-Pfade (4.14):** `/q/health|metrics|info|jolokia` → `/observe/...`, und
  Management-Server muss via `camel.management.enabled=true` aktiviert werden (nur falls camel-main-
  HTTP genutzt wird — rahla bootet HTTP über Karaf/Pax-Web, daher ggf. nicht betroffen; prüfen).
- **Template-Komponenten (4.13):** `contentCache` defaultet jetzt `true`; Header-gelieferte Templates
  brauchen explizit `allowTemplateFromHeader=true`.

### 3.2 Vollständige Breaking-Changes je Minor (Upstream-belegt)

Quelle generell: Apache-Camel-4.x-Upgrade-Guide, je Minor eine Seite
`https://camel.apache.org/manual/camel-4x-upgrade-guide-4_NN.html` (4.14 und 4.18 nur als AsciiDoc-
Quelle unter `raw.githubusercontent.com/apache/camel/main/docs/.../camel-4x-upgrade-guide-4_NN.adoc`,
HTML-Render 404 Stand 2026-06).

- **4.10→4.11:** `camel-etcd3` entfernt; `AttachmentMap` + `getDelegateMessage()` entfernt;
  `Exchange.BEAN_METHOD_NAME`-Header entfernt (→ `method`-Option); `PlatformHttpHeaderFilterStrategy`
  → `HttpHeaderFilterStrategy`; `UndertowHeaderFilterStrategy` deprecated; camel-kafka `recordMetadata`
  default `true`→`false`; camel-sql Batch nun in Einzel-Transaktion (zurück via
  `batchAutoCommitDisabled=false`); Telemetrie `camel-opentelemetry`→`camel-opentelemetry2`;
  neue `DynamicPollingConsumer`-API bei file/ftp/smb/azure-files.
- **4.11→4.12:** package-scan nach `camel-support` (`org.apache.camel.support.scan`);
  `Exchange.BEAN_METHOD_NAME`-Konstante + `Johnzon` aus `JsonLibrary` entfernt; HeaderFilterStrategy
  case-insensitive + Legacy-`org.apache.camel.`-Filter raus; jackson default `HashMap`→`LinkedHashMap`;
  Rest-DSL `bearer`→`bearerToken`; camel-jetty-/undertow-starter deprecated → platform-http-starter;
  Java-DSL nested Choice braucht `end().endChoice()`.
- **4.12→4.13:** yaml-dsl **kebab-case entfernt** (camelCase); Template-Komponenten `contentCache`
  default `true` + `allowTemplateFromHeader` nötig; `eagerLimitMaxMessagesPerPoll` default `false`→`true`
  (file/ftp/smb/azure-files); `BackOffTimer` Klasse→Interface (`PluginHelper.getBackOffTimerFactory()`);
  http `BasicAuthenticationHttpClientConfigurer`→`DefaultAuthenticationHttpClientConfigurer`;
  Spring-Boot `camel.springboot.*`→`camel.main.*`; `camel-fury`→`camel-fory`.
- **4.13→4.14 (LTS):** camel-main HTTP-Server getrennt (`camel.server.*`→`camel.management.*`,
  `camel.management.enabled=true`), Pfade `/q/*`→`/observe/*`; `ThreadFactoryListener.onNewThreadFactory`
  bekommt `Object source`-Param; `shareUnitOfWork=true` teilt nun eine UnitOfWork; Header-Konstanten-
  Umbenennungen (`Camel`-Präfix, u.a. kafka/salesforce/cxf/dns); diverse String-statt-Collection-Params
  (google `scopes`, consul `tags`, weather `ids`, …). (camel-jbang Default-Java→21, nur jbang.)
- **4.14→4.15:** Data-Format-Options umbenannt (crypto/csv/jaxb/soap/xmlSecurity/swiftMx/flatpack);
  CSV-Header + YAML-`typeFilter` von Liste → kommagetrennt; `tidyMarkup`-DataFormat entfernt;
  netty `keyStoreFile`/`trustStoreFile` entfernt; AI-`*.Headers`-Klassen ausgelagert.
  ⚠️ Bekanntes Problem: **camel-spring-boot lief in 4.15.0 nicht mit JDK 17** (in 4.16.0 gefixt).
- **4.15→4.16:** `validate`-EIP ohne `.end()`; `tryConvertTo` markiert „miss" anders;
  kamelet Raw-Mode-Parsing; graphql wirft `HttpOperationFailedException` bei non-2xx; jbang `edit`
  entfernt; milo/infinispan/flink mit eigenen API-Breaks.
- **4.16→4.17:** **`transform`-Data-Type → `transformDataType`-EIP**; `DefaultMessage`-Ableitungen mit
  `populateInitialHeaders` brauchen `isPopulateHeadersSupported()`; `camel-stomp` deprecated;
  TestContainers 2.0.2 (JUnit-4-Support raus); camel-undertow filtert ungültige Header-Zeichen nicht mehr.
- **4.17→4.18 (LTS, Zielversion):** **Simple-Wort-Operatoren** `"not contains"`/`"starts with"`/
  `"ends with"`/`"not regex"`/`"not range"` → `"!contains"`/`"startsWith"`/`"endsWith"`/`"!regex"`/
  `"!range"`; Header-Konstanten mit `Camel`-Präfix (cxf/dns/kafka/salesforce — Symbol-Refs ok, **String-
  Literale** anpassen); neue HeaderFilterStrategies (nats/xmpp/undertow `websocket.*`/aws2-sqs+sns
  bidirektional); contract-first REST-DSL: **ein Router pro Endpoint**; `camel-olingo2/4` deprecated;
  qdrant `Qdrant.Headers`→`QdrantHeaders`.

**Verbindliche Migrationsregel (aus `CLAUDE.md`):** nach jedem Camel-Bump
**`mvn --batch-mode clean package`**; `camel.version` **nie** über die neueste published Karaf-
`apache-camel`-Features-Release hinausziehen (sonst fehlt der Features-Deskriptor, den
`assembly/pom.xml` per `${camel.version}` ohne explizite Version zieht — genau deshalb steht 1.3.3 auf
4.18.2 statt 4.20.x).

### 3.3 Java / JDK

- **Camel 4.x Mindestversion: Java 17** — „Camel 4 supports Java 17. Support for Java 11 is dropped."
  Quelle: `https://camel.apache.org/manual/camel-4-migration-guide.html`. In den Minor-Notes 4.11→4.18
  ist **keine** Anhebung der Camel-Mindest-Java-Version dokumentiert (Java 17 bleibt die Baseline; das
  4.15-„JDK 17"-Problem war spring-boot-spezifisch und in 4.16 behoben — rahla nutzt kein spring-boot).
- **Rahla baut/läuft auf JDK 21** (`CLAUDE.md`) — erfüllt die ≥17-Anforderung problemlos.
- Der OSGi-Execution-Environment-Header der Assembly steht auf **`<javase>17</javase>`** trotz
  Kompilierung gegen 21 (`CLAUDE.md`). Das ist konsistent mit der Camel-4-Baseline (Java 17) und
  bewusst gesetzt; bei einer künftigen Java-Migration prüfen, ob anzuheben (vgl. Abschnitt 5, Punkt 6).

---

## 4. Querschnitt: Deployment / Base-Image (linuxserver.io)

Zeitliche Abfolge der Base-Image-/Deployment-Brüche (alle mit Quelle):

| Version | Base-Image                                   | Quelle |
|---------|----------------------------------------------|--------|
| 1.2.1   | `eclipse-temurin:21-jre-alpine`              | Dockerfile @ v1.2.1 |
| 1.2.2   | `eclipse-temurin:21-jre` (glibc)             | Commit `c18deab` |
| 1.2.3   | `eclipse-temurin:21-jre` (useradd-Fix, uid 1001) | Commit `d303c62` |
| 1.2.4   | `linuxserver/baseimage-debian:trixie`        | Commit `89c91ad` |
| 1.3.0+  | `linuxserver/baseimage-alpine:3.23`          | Commit `fb10df6` |

### Was sich für Betrieb/Migration ändert

**(a) Übergang in die LSIO-Welt (ab 1.2.4, vollständig ab 1.3.0)**
- Container läuft als **PUID/PGID** (Default `911`, LSIO-User `abc`) — kein fixer App-User mehr.
  Volume-Ownerships entsprechend (`911:911`). Quelle: `README.md` LSIO-Hinweis; `fb10df6`,
  `89c91ad` Dockerfile (`COPY --chown=911:911`, `chown abc:911`).
- **s6-overlay** als Init-System: `COPY root /` bringt die s6-rc-Services. Vorhanden (Stand HEAD):
  `init-rahla` (Legacy-Pfad-Warnungen), `init-rahla-certs` (Cert-Import, ab 1.3.3), `svc-rahla`
  (`s6-setuidgid abc /app/rahla/bin/karaf run`). Quelle: `git ls-tree root` @ HEAD;
  `svc-rahla/run`, `init-rahla/run`.
- Eigene Image-Erweiterungen müssen LSIO-konform sein (eigene s6-Services unter
  `/etc/s6-overlay/s6-rc.d/...`, nicht ein eigenes `CMD`/`ENTRYPOINT` setzen).

**(b) Pfad-/Volume-Umzug (Breaking, 1.3.0)** — Quelle `CHANGELOG.md` 1.3.0; `fb10df6`, `56ed454`
- App: `/rahla` → **`/app/rahla`**.
- Deploy-Mount: **`/config/deploy`** (Symlink `/app/rahla/deploy` → `/config/deploy`).
- Etc-Mount: **`/config/etc`** (Symlink `/app/rahla/etc` → `/config/etc`).
- `/rahla/deploy` und `/deploy` werden **nicht** mehr gescannt; `init-rahla` warnt bei Funden.
- Container-Tag-abhängige Deploy-Pfade (vgl. `rahla-developer/SKILL.md`): 1.0.x → `/deploy`,
  1.2.x → `/rahla/deploy`, ≥ 1.3 → `/config/deploy`. Absolute Pfade in Blueprint-XML/Groovy-URLs
  entsprechend anpassen.

**(c) `RAHLA_DEPLOY_PATH` entfernt (Breaking, 1.3.0)** — Quelle `CHANGELOG.md` 1.3.0; `84cf846`
- Env-Var weg. Zweites Watch-Verzeichnis nur noch via
  `org.apache.felix.fileinstall-<name>.cfg` im Deploy.

**(d) HTTP-Runtime Undertow statt Jetty (Breaking, 1.3.0)** — Quelle `CHANGELOG.md` 1.3.0; `e600d75`
- Boot-Feature `pax-web-http-undertow`; `<restConfiguration component="undertow" .../>`. Jetty
  weiterhin installierbar, aber bekannte Karaf-4.4.x-Jetty-CVEs → bei Jetty-Nutzung Version pinnen;
  nur eine Pax-Web-HTTP-Feature gleichzeitig. Quelle: `README.md` HTTP/REST; `rahla-developer/SKILL.md`.

**(e) pax-logging via `rahla-logging` (1.3.0)** — Quelle `CHANGELOG.md` 1.3.0; `e600d75`
- Karaf-Default-pax-logging ersetzt; Loki-Appender (`Fragment-Host: pax-logging-log4j2`) hängt daran.
  pax-logging nicht von anderswo re-pinnen.

**(f) Kubernetes (1.3.0)** — Quelle `CHANGELOG.md` 1.3.0; `fb10df6`/`b357d08`
- `manifests/rahla.yaml` als Sample (Ports SSH 8101, HTTP 8181, Prometheus 9001; Volumes).
  Routen nach `/config/deploy` mounten (ConfigMap/PVC).

**(g) Cert-Import & pax-url-Repo-Pin (1.3.3)** — Quelle `CHANGELOG.md` 1.3.3; `ee97ef7`, `36e8844`
- `/config/certs` für CA-Zerts (Auto-Import via `init-rahla-certs`).
- pax-url `localRepository=/config/.m2` (im Dockerfile gesetzt, lokal unberührt) → ermöglicht
  Pre-Seeding für Offline-Images.

**(h) Manuell gekoppelte Versionsstrings (dauerhafter Stolperstein)** — Quelle `CLAUDE.md`;
Bugfixes in 1.2.4 (OTel-Drift) und 1.3.3 (`92d32ae`)
- **Adoptium-JRE-Block im Dockerfile** wird von Hand aus
  `adoptium/containers/21/jre/alpine/3.23` kopiert. Renovate **überwacht** nur (kann ESUMs nicht neu
  berechnen). Bei einem JRE-Bump **alle** synchron ändern: `JAVA_VERSION`, beide `ESUM`
  (aarch64 + x86_64), beide `BINARY_URL`, die `apk add`-Liste, und den Alpine-Tag (`3.23`) an der
  Adoptium-Variante ausgerichtet halten.
- **`KARAF_SYSTEM_OPTS` hardcodet Agent-Jar-Dateinamen+Versionen** (`jmx_prometheus_javaagent-*.jar`,
  `opentelemetry-javaagent-*.jar`). Müssen exakt den Versionen entsprechen, die die Assembly (Parent-
  `pom.xml`) shippt — sonst existiert der `-javaagent:`-Pfad nicht und die JVM bootet nicht (genau
  das brach 1.3.3 vor `92d32ae`). Immer zusammen bumpen.
- **README-Versionsstrings** (`Karaf … + Camel …`) und ein `CHANGELOG.md`-Eintrag sind bei jedem
  Bump von Hand nachzuziehen (`CLAUDE.md`).

---

## 5. Offene / zu-verifizierende Punkte

1. ~~Camel-4.10→4.18-API-Breaks für eigene Routen~~ **ERLEDIGT** (2026-06-23): die Upstream-Breaking-
   Changes je Minor sind jetzt in Abschnitt 3.2 belegt (Quelle: offizielle Camel-4.x-Upgrade-Guides),
   die rahla-wahrscheinlich-relevanten in 3.1 vorgezogen. Vor einer realen Routen-Migration trotzdem
   die jeweilige Upstream-Seite gegenlesen (Komponenten-spezifische Punkte je nach genutzten Routen).
2. **Jedis-Major-Sprung 5.2.0 → 7.1.0 (1.2.4)** — kein Repo-Migrationshinweis; Kompatibilität
   eigener `JedisSource`-Nutzungen mit Jedis-7-API verifizieren. (Jedis ab 1.3.1 `@Deprecated`.)
3. ~~Warntext-Versionsangabe in `init-rahla/run`~~ **ERLEDIGT** (2026-07-06): „1.3.5" → „1.3.0"
   korrigiert (Entfernung von `RAHLA_DEPLOY_PATH` war 1.3.0, Commit `84cf846`); dabei auch den
   Tippfehler `org.apachle.felix` → `org.apache.felix` im Hinweistext gefixt.
4. ~~Changelog-Header 1.3.3 „(unreleased)"~~ **ERLEDIGT** (2026-07-06): Header auf das Tag-Datum
   `(2026-06-10)` gesetzt, neue Sektion `# 1.3.4 (unreleased)` ergänzt.
5. **Tag-Rollbacks** (`maven-release-plugin`) bei v1.2.2, v1.2.4, v1.3.1 — bei
   Checkout/Diff den finalen Tag verwenden, nicht die Rollback-Zwischen-Commits.
6. **`<javase>17</javase>`** als OSGi-Execution-Environment-Header der Assembly trotz Kompilierung
   auf 21 (`CLAUDE.md`). **Geklärt** (s. 3.3): konsistent mit der Camel-4-Baseline (min. Java 17,
   Quelle: Camel-4-Migration-Guide); rahla läuft auf JDK 21 (≥17, ok). Absicht; bei einer Java-
   Migration prüfen, ob anzuheben.
