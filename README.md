<a id="readme-top"></a>

[![Build Status][build-shield]][build-url]
[![License][license-shield]][license-url]


<br />
<div align="center">
  <h3 align="center">Rahla</h3>

  <p align="center">
    An Open-Source Apache Camel Appliance for Apache Karaf
    <br />
    <a href="https://codeberg.org/dataTactics/rahla/"><strong>Explore the docs »</strong></a>
    <br />
    <br />
    <a href="https://codeberg.org/dataTactics/rahla/issues">Report Bug</a>
    &middot;
    <a href="https://codeberg.org/dataTactics/rahla/issues">Request Feature</a>
  </p>
</div>


## About The Project

**Rahla** is an open-source Apache Karaf appliance designed for easy deployment with Apache Camel. It simplifies the process of building and deploying robust integration solutions by leveraging Camel’s powerful routing and mediation engine in an OSGi environment.

It is packaged as a Docker container, making it easy to deploy and manage in various environments, and automatically detects and loads resources (JARs, configurations, Camel contexts) on-the-fly.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

### Key Features

* **Containerized:** Packaged as a Docker container for easy deployment.
* **OSGi-Enabled:** Leverages OSGi services for modularity and integration with Apache Karaf.
* **Dynamic Deployment:** Monitors `/config/deploy` to load resources on-the-fly.
* **Groovy Bean Factory:** Compile and use Groovy beans in your Camel routes at runtime.
* **Template File Installer:** Define reusable route templates in YAML files.
* **GraphSource:** Seamlessly interact with graph databases (e.g., JanusGraph).
* **JedisSource:** Simplify interactions with Redis databases using the Jedis library.
* **Siddhi Integration:** Integrate complex event processing into Camel routes.
* **Advanced Monitoring:** Built-in Prometheus JMX Exporter and OpenTelemetry Agent.

<p align="right">(<a href="#readme-top">back to top</a>)</p>


## Getting Started

To get a local copy up and running follow these simple example steps.

### Prerequisites

* **Docker** or a compatible container runtime.
* Basic knowledge of mounting volumes and managing directories.

### Installation

1.  **Prepare Deployment Directory:**
    Create a local directory for your artifacts.
    ```sh
    mkdir deploy
    ```
2.  **Start Rahla:**
    Run the container, mounting your local deploy folder to `/config/deploy`.
    ```sh
    docker run --rm -p 8101:8101 -v ./deploy:/config/deploy datatactics/rahla:latest
    ```
3.  **Deploy Artifacts:**
    Copy your JARs, configurations (`.cfg`, `.groovy`), and Camel contexts (`.xml`) into your local `deploy` directory. Rahla automatically detects changes in `/config/deploy` and loads them.

4.  **Access Console:**
    Connect via SSH (Default password: `admin`):
    ```sh
    ssh -p 8101 admin@localhost
    ```

<p align="right">(<a href="#readme-top">back to top</a>)</p>


## Usage

### Kubernetes Deployment

Rahla is cloud-ready. A sample Kubernetes deployment manifest is available in the repository at `manifests/rahla.yaml`.

This manifest configures the necessary ports (SSH 8101, OSGi 8181, Prometheus 9001) and volume mounts. Ensure you configure your `PersistentVolumeClaim` or `ConfigMap` to mount to `/config/deploy` if you wish to inject routes dynamically.

### Configuration

#### JVM Configuration
Use the environment variable `EXTRA_JAVA_OPTS` to add additional parameters for the Java Virtual Machine (e.g., Truststore, Xmx).

#### Important Configuration Files
Rahla includes several pre-configured files. You can override these by mounting valid configurations:

* **`users.properties`:** Defines user accounts/roles.
* **`config.yaml`:** Configures the Prometheus JMX Exporter.
* **`log4j2.xml`:** Configures logging (default is JSON format for containers).
* **`org.apache.felix.fileinstall-rahla.cfg`:** Configures the File Install bundle to monitor `/config/deploy`.

### Advanced Capabilities

#### Groovy Bean Factory
Compile beans with Groovy for your Camel contexts during runtime using the `file://` protocol:

```xml
<reference id="beanFactory" interface="rahla.api.GroovyBeanFactory"/>

<bean id="myInstance" factory-ref="beanFactory" factory-method="createBean">
<argument value="file:///config/deploy/MyClass.groovy"/>
</bean>
```

#### Template File Installer 

Define your route templates in YAML files:

```yaml
templateId: my-route-template
sharedConfigPid: rahla.shared.config
routes:
  - id: my-route-1
    parameters:
      from: "direct:myRoute1Start"
      to: "log:myRoute1?level=INFO"
  - id: my-route-2
    parameters:
      from: "direct:myRoute2Start"
      to: "log:myRoute2?level=INFO"
```
Place these files in the configured `deploy` directory. Rahla automatically converts these YAML configurations into usable route templates. 

#### GraphSource

Easily interact with gremlin databases (e.g., JanusGraph) using the `GraphSource` Interface:

E.g.: Using a ```rahla.graphsource-jg.cfg`` file:
```ini
hosts=janusgraph
port=8182
user=
pass=
graphSourceName=aGraphSource
graphTraversalSourceName=graph_traversal
serializer=org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1
serializerConfig={"ioRegistries": ["org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry"]}
nioPoolSize=8
workerPoolSize=8
minConnectionPoolSize=2
maxConnectionPoolSize=8
minInProcessPerConnection=1
maxInProcessPerConnection=32
minSimultaneousUsagePerConnection=8
maxSimultaneousUsagePerConnection=16
```

An osgi service is registered base on the configuration above
```xml
<reference id="myGraphSource" interface="rahla.api.GraphSource" filter="(graphSourceName=aGraphSource)"/>

```
The GraphSource interface is quite simple:
```java
public interface GraphSource<T, V> {

  List<String> getHosts();

  String getUser();

  V getClient();

  T getGraphTraversalSource();
}
```

#### JedisSource

Easily interact with redis databases via java low level jedis library using the `JedisSource` Interface:

E.g.: Using a ```rahla.jedissource-redis.cfg`` file:

```
port=
host=
db=0
pass=
user=
jedisSourceName=myJedisSource

```


```xml
<reference id="myJedisSource" interface="jedissource.api.JedisSource" filter="(jedisSourceName=myJedisSource)"/>
```

With a simple interface for jedis
```java
package rahla.api;

import redis.clients.jedis.Jedis;

public interface JedisSource {

  Jedis getResource();

  void returnResource(Jedis jedis);

}


```


#### Siddhi Integration (Fradi Camel Component)

Rahla includes an custom component which brings siddhi to camel. Siddhi is a complex event processing for doing sql stuff on streams:
See [Siddhi Query Guide](https://siddhi.io/en/v4.x/docs/query-guide/) for detailed information on siddhi.

You can easyily create a component:

```xml
<bean id="siddhiPlan" class="rahla.components.fradi.FradiComponent">
  <property name="plan" value="resource:deploy:siddhiPlan.siddhi"/>
</bean>
```
An simple plan could look like
```sql
define stream foo(valueA int);

from foo select valueA as valueB insert into bar;

```
The plan just select valueA from stream foo renames it to valueB and sends it to the stream bar.

Inside your camel context the usage is as follows:
***Note:*** the component expects an a map with key value pairs conaining field names of the stream or an array with the correct layout. Besides single events also batches are supported. For this you need to send iterables to the component.
```xml

<route>
 <from uri="direct:foo"/>
 <from uri="siddhiPlan:foo"/>
</route>

<route>
 <from uri="siddhiPlan:bar"/>
 <to uri="log:bar"/>
</route>
```

#### Pax Logging Loki Appender
We added a pax compatible appender for Loki.
***Note*** keep in mind that you need to add ```<Configuration packages="pl.tkowalcz.tjahzi.log4j2">``` to get ```<Loki ...``` working.


## Monitoring

For monitoring, the Rahla appliance comes equipped with Prometheus JMX Exporter integration and OpenTelemety Agent. The java properties are  configured via environment variable ```KARAF_SYSTEM_OPTS```. The Rahla Karaf assembly includes the necessary libraries.

- **prometheus JMX Exporter Agent:**  
   - We added `-javaagent:./jmx_prometheus_javaagent-1.0.1.jar=9001:etc/config.yaml`
   - Rahla will expose Prometheus metrics at `http://localhost:9001/metrics`. 
- **OpenTelemetry Agent:** 
   - We added `-javaagent:./opentelemetry-javaagent-2.4.0.jar`
   - The environment variables ```OTEL_LOGS_EXPORTER``` ```OTEL_METRICS_EXPORTER``` ```OTEL_TRACES_EXPORTER``` are ```none``` by default and need to be configured


## Contributing

Contributions to Rahla are highly encouraged! 

- **License:** Rahla is licensed under the Apache 2.0 License.
