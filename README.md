# Rahla - An Open-Source Apache Camel Appliance for Apache Karaf

[![Build Status](https://github.com/dttctcs/rahla/actions/workflows/build.yaml/badge.svg)](https://github.com/dttctcs/rahla/actions/workflows/build.yaml)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](https://github.com/dttctcs/rahla/blob/main/LICENSE)


**Rahla** is an open-source Apache Camel container designed for easy deployment within Apache Karaf. It simplifies the process of building and deploying robust integration solutions by leveraging Camel’s powerful routing and mediation engine in an OSGi environment.

## Key Features

- **Containerized:** Rahla is packaged as a Docker container, making it easy to deploy and manage in various environments.
- **OSGi-Enabled**: Leverages OSGi services for modularity and seamless integration with Apache Karaf.
- **Dynamic Deployment**: Automatically detects and loads resources (jars, configurations, Camel contexts) on-the-fly from the specified deployment directory.
- **Advanced Monitoring**: Built-in support for Prometheus and OpenTelemetry to monitor application performance.
    * **SmartURL:**  Dynamically add URL handlers to files, MinIO, Redis, and other file-based structures.
    * **Groovy Bean Factory:**  Compile and use Groovy beans in your Camel routes at runtime.
    * **Template File Installer:**  Define reusable route templates in YAML files for easy configuration management.
    * **GraphSource:**  Seamlessly interact with graph databases (e.g., JanusGraph) using Gremlin queries.
    * **JedisSource:**  Simplify interactions with Redis databases using the Jedis library.
    * **Siddhi Integration (Fradi Camel Component):**  Integrate Siddhi, a complex event processing engine, into your Camel routes for stream processing and SQL-like operations.
* **Monitoring:** Rahla integrates Prometheus JMX Exporter and OpenTelemetry Agent for comprehensive monitoring and observability.


## Getting Started

### Prerequisites

- **Container Runtime (e.g., Docker)**: Basic knowledge of starting containers, mounting files, and managing directories.

### Steps to start Rahla with Docker

1. **Start Rahla Distribution:**  ```mkdir deploy && docker run --rm -p 8101 -v ./deploy:/deploy datatactics/rahla:latest```
2. **Deploy to Rahla** Copy your jars, configs, and Camel contexts into the deploy directory. Rahla automatically monitors this directory for changes and dynamically loads new resources. To customize the deployment path, set the ```RAHLA_DEPLOY_PATH``` environment variable.
3. **Access Rahla Console:** Connect to the Rahla console via SSH: ```ssh -p 8101 admin@localhost``` **Note:** Default password is ```admin``` and  can be changed via environment variable ```ADMIN_PASS```


### Kubernetes Deployment
Due to the cloud readiness of the application an simple deployment is given here:
```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: rahla
  labels:
    app: rahla
spec:
  selector:
    matchLabels:
      app: rahla
  replicas: 1
  template:
    metadata:
      labels:
        app: rahla
      annotations:
        prometheus.io/path: /metrics
        prometheus.io/port: "9001"
        prometheus.io/scrape: "true"
    spec:
      containers:
      - name: rahla
        image: datatactics/rahla:latest
        resources:
          requests:
            cpu: 100m
            memory: 768Mi
        ports:
        - name: ssh
          containerPort: 8101
          protocol: TCP
        - name: osgi
          containerPort: 8181
          protocol: TCP
        volumeMounts:
        - name: data-cache
          mountPath: "/rahla/data"
      volumes:
      - name: data-cache
        emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: rahla
  labels:
    app: rahla
spec:
  ports:
  - name: osgi
    port: 8181
    protocol: TCP
    targetPort: osgi
  - name: ssh
    port: 8101
    protocol: TCP
    targetPort: ssh
  selector:
    app: rahla
  type: ClusterIP

```

### Build your own
You can checkout the repo and do maven ```mvn clean package``` of the project. Inside ```assembly/target/assembly/``` is the unpacked karaf application 


### JVM Configration
Rahla relies on karaf so use  ```EXTRA_JAVA_OPTS``` to add additional parameters for the java virtual machine e.g. Truststore, Xmx and so on.

### Console
After successfully connected to the Rahla (Karaf) Console. You have access to a standard karaf envorinment. We addeed one command ``log:logs`` in case you mount the ```log4j2.xml``` read-only.

- **List Installed Bundles:** Use the following command to verify bundles are installed and active:

  ```bash
  bundle:list 
  ```
- **Check for Problems:** Use the following command to diagnose bundles:

  ```bash
  bundle:diag <num> 
  ```


### Additional Feature Overview

Rahla leverages OSGi services for dependency management and modularity, ensuring seamless integration with Apache Karaf. It additionally adds some components wich are noit 

####  SmartURL:

Rahla's `smarturl` can dynamically add url handlers to files, minio or any other file based structure.
Smarturl currently existis for file, minio and redis backends.

E.g.: Using a ```rahla.smarturl.minio.cfg`` file:

```ini
url.handler.protocol: minio
endpoint: http://minio
access.key: admin
secret.key: admin
```

Access streaming data from an oject
```groovy
def inputStream = new URL("minio://bucket/object").openConnection().getGZInputStream()

```

#### Groovy Bean Factory 

Compile beans with Groovy for your camel contexts during runtime:
```xml
<reference id="beanFactory" interface="rahla.api.GroovyBeanFactory"/>

<bean id="myInstance" factory-ref="beanFactory" factory-method="createBean">
   <argument value="resource:deploy:MyClass.groovy"/>
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


### Configuration Files

Rahla adds or modifies a set of configuration files for customization and fine-tuning:

- **`branding.properties`:** Contains properties used by the Karaf shell to customize the shell prompt and other display elements.
- **`branding-ssh.properties`:** Similar to `branding.properties`, but specifically for the SSH console.
- **`custom.properties`:**  A placeholder file for your custom system properties. This allows you to override or extend Rahla's default configuration. We added base package sun.nio.ch. If you require packages from inside your jvm implemenation which are not standard.
- **`org.apache.karaf.log.cfg`:** Configures Karaf's console logging system, including log levels and output formatting..
- **`org.apache.karaf.management.cfg`:** Defines settings related to JMX management for Karaf, enabling remote monitoring and control.
- **`org.ops4j.pax.logging.cfg`:** Just configures pax to use log4j2.xml.
- **`org.ops4j.pax.url.mvn.cfg`:** Configures Pax URL's Maven resolver, allowing Karaf to download dependencies from Maven repositories. We added local repository to package lookup  (+)
- **`users.properties`:** Defines user accounts and roles for accessing the Karaf console and managing the container. You can add more users here.
- **`config.yaml`:** Configures the Prometheus JMX Exporter to expose Camel metrics for monitoring. You can add custom metric mappings to this file. For example, if you require to 
- **`log4j2.xml`:** Configures Rahla's logging behavior. The default configuration is for container usage, including console output in JSON format. You can modify or replace the configuration file to meet your requirements. e.g. special naming for your ```org.apache.camel.metrics``.
- **`org.apache.felix.fileinstall-rahla.cfg`:** Configures the File Install bundle to monitor a directory (by default, `/deploy` can be replaced by the environment variable ```RAHLA_DEPLOY_PATH``` ) to automatically deploy or update configruations, budles or packages in the Karaf container. 


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

- **File Issues:** Use the [GitHub issue tracker](https://github.com/dttctcs/rahla/issues) to report bugs or request new features.
- **Submit Pull Requests:**  Contribute code changes through pull requests. Please follow the project's coding standards.
- **License:** Rahla is licensed under the Apache 2.0 License.
