# Local example: Groovy classes as a bundle + a Camel REST service

Runs Rahla **without Docker**, straight from the Maven build. Three files in the deploy directory,
no rebuild in between. Verified against Rahla 1.3.6-SNAPSHOT / Camel 4.18.4 / Karaf 4.4.12.

| File | Purpose |
|------|---------|
| `00-features.xml` | installs `camel-blueprint`, `camel-jetty`, `camel-jackson` — Camel is **not** preinstalled in Rahla |
| `Toolbox.groovy`  | four classes in one file; Rahla compiles them into the bundle `rahla.groovy.Toolbox` |
| `routes.xml`      | Blueprint context: REST endpoint plus a timer route, wiring the Groovy classes as plain `<bean class="…"/>` |

## Run it

```sh
mvn --batch-mode clean package                 # produces assembly/target/assembly/
A=assembly/target/assembly

$A/bin/start                                   # daemon; $A/bin/karaf runs in the foreground instead
cp examples/local/* $A/deploy/                 # order does not matter, see "Deploy order" below
```

Give it a moment on the first run — the Camel features are downloaded into `~/.m2`. Then:

```sh
curl -s localhost:8283/example/orders
# {"orderId":"A-1","total":24.48,"by":"Toolbox v1"}

curl -s -XPOST localhost:8283/example/orders -H 'Content-Type: application/json' \
     -d '{"id":"B-2","items":[{"price":1.50,"qty":4}]}'
# {"orderId":"B-2","total":6.0,"by":"Toolbox v1"}

curl -s -XPOST localhost:8283/example/orders -H 'Content-Type: application/json' \
     -d '{"id":"C-3","items":[]}'
# {"error": "order C-3 has no items"}      HTTP 400, produced by the <onException> block
```

The `heartbeat` route logs one line every 10 seconds without any HTTP traffic.

Shut down with `$A/bin/stop`.

## Watch it work

```sh
$A/bin/client                      # SSH shell, user admin, password admin
```

```
karaf@root()> bundle:list -s | grep rahla.groovy
108 | Active | 80 | 1.0.0.929e0ce4c748 | rahla.groovy.Toolbox

karaf@root()> bundle:headers rahla.groovy.Toolbox
Rahla-Groovy-Source = file:/…/deploy/Toolbox.groovy
Rahla-Groovy-Status = compiled
Export-Package      = example

karaf@root()> bundle:classes rahla.groovy.Toolbox | grep example/
example/Enricher.class              | exported: true
example/Orders$_total_closure1.class| exported: true
example/Orders.class                | exported: true
example/Summarizer.class            | exported: true
example/Validator.class             | exported: true

karaf@root()> camel:route-list
```

## Hot reload

Edit `Toolbox.groovy` in the deploy directory, e.g. change `Toolbox v1` to `Toolbox v2`. Within
a second or two the bundle is rebuilt, the Blueprint context restarts, and the endpoint answers with
the new value. **`routes.xml` is not touched.**

```sh
sed -i 's/Toolbox v1/Toolbox v2/' $A/deploy/Toolbox.groovy
curl -s localhost:8283/example/orders
# {"orderId":"A-1","total":24.48,"by":"Toolbox v2"}
```

The version qualifier in `bundle:list` changes with the source, so you can always tell which revision
is live. Deleting the file uninstalls the bundle; `routes.xml` then parks with a clear
`missing requirement … osgi.wiring.package=example`.

## Several classes in one file

Yes — `Toolbox.groovy` holds `Validator`, `Enricher`, `Summarizer` and `Orders`, and they all end up
in the same bundle. Groovy allows one `package` declaration per file, so all classes of a file share
a package; that package is what the bundle exports.

Two rules:

* **A package declaration is mandatory.** Classes in the default package cannot be exported by a
  bundle, so nothing else could use them. Rahla refuses such a file at deploy time with an explicit
  message instead of installing something unusable.
* **The file name determines the bundle name**, not the class names. `Toolbox.groovy` becomes
  `rahla.groovy.Toolbox` whether it contains one class or ten. Two files of the same name with
  identical content in different directories therefore collide.

## Deploy order

Irrelevant. If `Toolbox.groovy` lands before Camel is installed, Rahla installs a class-less
placeholder bundle that waits for `org.apache.camel` (visible in `bundle:diag`) and compiles the
source as soon as the package shows up. The same holds for `routes.xml`, which simply waits for the
`example` package.

While that settles you will see a few `WARN` lines from `fileinstall` (`Error while starting
bundle: …`) and one from the deployer saying it installed a placeholder. They are transient — on a
cold start the whole thing was up 8 seconds after the files were copied. Only worry if they are
still there once `camel:route-list` shows no routes.

## Notes

* Logs go to the console only. As a daemon that means `/dev/null` unless you set
  `KARAF_REDIRECT=/tmp/karaf.out` before `bin/start`, or use `bin/karaf` in the foreground.
* For scripting, `bin/client` needs batch mode: `printf 'bundle:list\n' | bin/client -b -u admin -p admin`.
  Karaf's built-in `grep` takes a Java regex, so alternation is `grep -i 'a|b'`, not `grep -E`.
* Port `8283` is the example's own; change it in `routes.xml` if it collides. Karaf itself uses
  8101 (SSH), 8181 (HTTP/Jolokia), 1099 and 44444 (JMX).
