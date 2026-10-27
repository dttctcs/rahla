# JanusGraph / Gremlin without GraphSource

Replacement for `rahla.graphsource.GraphSource` (removed in Rahla 1.3.6). Tested on Rahla
1.3.6-SNAPSHOT (Karaf 4.4.12, Camel 4.18.4) against `docker.io/janusgraph/janusgraph:1.1.0`.

| File | Purpose |
|------|---------|
| `00-features.xml` | feature `tinkerpop-janusgraph-client`: TinkerPop 3.7.3 driver + janusgraph-driver 1.1.0 as **one host bundle with fragments** (see below), netty 4.1, guava, commons-*, spatial4j/jts; feature `janusgraph-example` adds Camel for the demo |
| `GraphClient.groovy` | `example.graph.GraphClient`: builds `Cluster`/`Client` with GraphBinary + `JanusGraphIoRegistry`, hands out `g()` |
| `example.graph.cfg` | PID `example.graph`, same keys as the old `rahla.graphsource-<name>.cfg`: `hosts`, `port`, `user`, `pass`, `graphTraversalSourceName`, `serializer`, `ioRegistries` (was `serializerConfig`), pool sizes |
| `graph.xml` | `cm:property-placeholder` → `<bean class="example.graph.GraphClient" init-method="start" destroy-method="close">` → REST routes |

## Run

```sh
docker network create rahla-test
docker run -d --name janusgraph --network rahla-test docker.io/janusgraph/janusgraph:1.1.0
mkdir deploy && cp examples/janusgraph/*.xml examples/janusgraph/*.groovy examples/janusgraph/*.cfg deploy/ && sed -i 's/^hosts=localhost/hosts=janusgraph/' deploy/example.graph.cfg
docker run -d --name rahla --network rahla-test -p 8286:8286 -v ./deploy:/config/deploy datatactics/rahla:1.3.6

curl -s -XPOST localhost:8286/graph/person/jurij             # vertex id
curl -s -XPOST localhost:8286/graph/person/alana
curl -s localhost:8286/graph/person                          # [alana, jurij]
curl -s -XPOST localhost:8286/graph/knows/jurij/alana        # RelationIdentifier:…  (proves JanusGraphIoRegistry works)
curl -s localhost:8286/graph/count
```

## Why the features file looks like that

None of the TinkerPop/JanusGraph jars is an OSGi bundle, and they **split packages**:
`org.apache.tinkerpop.gremlin.util` is in `gremlin-core` *and* `gremlin-util`,
`…gremlin.language.grammar` in `gremlin-core` *and* `gremlin-language`. On top of that the driver
instantiates the serializer and the IoRegistry **by class name**, so the driver must be able to see
JanusGraph's classes. The old `graphsource` bundle solved both by copying everything into one jar
(`Private-Package: org.apache.tinkerpop*, org.janusgraph*, org.javatuples*, io.netty.*, …`).

The recipe builds the same single classloader at runtime with `wrap:` alone: `gremlin-core` becomes
the host bundle `tinkerpop.client`, and `gremlin-util`, `gremlin-language`, `gremlin-driver`,
`gremlin-shaded`, `janusgraph-driver`, `javatuples`, `hppc`, `noggit` are wrapped as **fragments**
(`Fragment-Host=tinkerpop.client`). In `bundle:list` the host is `Active`, the fragments `Resolved`.
Fragments do not re-export the split packages (`Export-Package=!…,org.apache.tinkerpop.*`), all
imports are optional with `DynamicImport-Package=*`, and netty is pinned to `[4.1,4.2)` so a Camel
netty 4.2 next to it does not get mixed in.

## What stays awkward

* **Optional imports hide missing jars until call time.** The first version of this recipe ran
  `count()`/`addV()` fine and failed on `g.V(v).addE(…)` with
  `NoClassDefFoundError: org/apache/commons/collections/IteratorUtils` — commons-collections 3 was
  missing. It is in the feature now, but anything else the driver touches rarely (e.g. `exp4j`
  for `math()` evaluated client-side, `gremlin-groovy` for script translation) is **not** included.
  Test the traversals you actually use.
* **Versions are hand-maintained.** The list mirrors `mvn dependency:tree` of
  `org.janusgraph:janusgraph-driver:1.1.0`; for another JanusGraph/TinkerPop version redo that.
  TinkerPop must match the server's major/minor (JanusGraph 1.1 → TinkerPop 3.7).
* **Serializer/registry are configured by class name** and loaded through the driver's classloader
  (`Cluster.classLoader`), not the Groovy bundle's — keep that if you change `GraphClient`.
* `start()` never fails the context: if the host does not resolve or the server is down it logs
  `Gremlin server … not reachable yet` and connects on first use (the old `GraphSourceImpl` failed
  activation instead, and Blueprint does not retry a failed bean). Verified: server down → HTTP 500,
  server back → requests succeed without touching Rahla.
