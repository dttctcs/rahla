# Rahla - An Open-Source Apache Camel Appliance for Apache Karaf

[![Build Status](https://github.com/dttctcs/rahla/actions/workflows/build.yaml/badge.svg)](https://github.com/dttctcs/rahla/actions/workflows/build.yaml)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](https://github.com/dttctcs/rahla/blob/main/LICENSE)

Rahla is an open-source Apache Camel container meticulously crafted for deployment within Apache Karaf. It simplifies the process of building and deploying robust integration solutions leveraging the power of Camel's routing and mediation engine in an OSGi environment. 



## Installation and Configuration

### Prerequisites

- **Container Runtime e.g. Docker:** Ensure you have basic knowledge about starting containers mounting files and directories

### Steps to start Rahla with Docker

1. **Download and start Rahla Distribution:**  ```mkdir deploy && docker run --rm -p 8101 -v ./deploy:/deploy datatactics/rahla:1.0.20 ```
2. **Deploy to Rahla** Deploy your jars, configs and camel context to you newly created deploy directory. Inside the container rahla watches ```/deploy``` for any changes and loads the resources dynamically. You can change this behaviour with the env variable ```RAHLA_DEPLOY_PATH``` to monitor a different directory. **Note:** this environment variable is also used for ```resource:deploy``` within camel contexts to relatively access resources.
3. **Connect Rahla Console:** Connect to the Rahla console using SSH: ```ssh -p 8101 admin@localhost```  **Note:** Default password is ```admin``` and  can be changed via environment variable ADMIN_PASS


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
You can checkout the repo and do maven build of the project. Inside ```assembly/target/assembly/``` is the unpacked karaf application 


### JVM Configration
Rahla relies on karaf so use  ```EXTRA_JAVA_OPTS``` to add additional parameters to the java virtual machine e.g. Truststore, Xmx and so on.


### Verification
After successfully connected to the Rahla (Karaf) Console.

- **List Installed Bundles:** Use the following command to verify Rahla bundles are installed and active:

  ```bash
  bundle:list 
  ```
- **Check for Problems:** Use the following command to diagnose bundles:

  ```bash
  bundle:diag <num> 
  ```
TODO: add more usefull commands/add link to karaf

## Usage and Architecture

### Architecture Overview

Rahla seamlessly integrates with Apache Karaf, utilizing OSGi services for dependency management and modularity. Its extra features include: 

####  SmartURL Handling:

Rahla's `smarturl` component provides a flexible way to route messages to various destinations based on URL schemes, including `file`, `redis`, and `minio`.

#### Groovy Bean Factory 
Rahla offers a `GroovyBeanFactory` extension, allowing you to easily create and manage Camel beans written in Groovy. This enhances the scripting capabilities within your routes.

#### Template File Installer 
Easily deploy and manage Camel route templates using YAML files, streamlining route configuration and deployment.

#### Simplified Graph Access 
Rahla provides a `GraphSource` API to simplify access to graph databases like JanusGraph, making it easier to integrate graph operations into your integration flows. 

#### Siddhi Integration (Fradi Feature)
The optional `fradi` feature integrates Siddhi, a powerful complex event processing engine, into Camel routes. This allows for real-time data processing within your Camel integrations. small example

#### Jedis source
Lorem ipsum

#### Loki Appender
Lorem ipsum

### Config files
Rahla ships and modified a few karaf configration files inside the `etc/` directory:
- **`branding.properties`:** BLA
- **`branding-ssh.properties`:** BLA
- **`custom.properties`:** BLA
- **`org.apache.karaf.log.cfg`:** BLA
- **`org.apache.karaf.management.cfg`:** BLA
- **`org.ops4j.pax.logging.cfg`:** BLA
- **`org.ops4j.pax.url.mvn.cfg`:** BLA
- **`users.properties`:** BLA
- **`config.yaml`:**  Configures the Prometheus JMX Exporter to expose Camel metrics for monitoring you can also add metrics mappings
- **`log4j2.xml`:** Configures Rahla's logging behavior, default configuration is for container usage including console output.

## Monitoring

For monitoring, Rahla comes equipped with Prometheus JMX Exporter integration and OpenTelemety Agent

- **Start JMX Exporter Agent:**  
   - The Rahla Karaf assembly includes the necessary libraries. Add `-javaagent:./jmx_prometheus_javaagent-1.0.1.jar=9001:./jmx.yaml -javaagent:./opentelemetry-javaagent-2.4.0.jar` to `bin/karaf` startup script.
- **Access Prometheus Metrics:**  
   - Rahla will expose Prometheus metrics at `http://localhost:9001/metrics`. 
- **OpenTelemetry Agent:** 
- The Rahla Karaf assembly includes the necessary libraries. Add `-javaagent:./jmx_prometheus_javaagent-1.0.1.jar=9001:./jmx.yaml -javaagent:./opentelemetry-javaagent-2.4.0.jar` to `bin/karaf` startup script.

## Contributing

Contributions to Rahla are highly encouraged! 

- **File Issues:** Use the [GitHub issue tracker](https://github.com/dttctcs/rahla/issues) to report bugs or request new features.
- **Submit Pull Requests:**  Contribute code changes through pull requests. Please follow the project's coding standards.
- **License:** Rahla is licensed under the Apache 2.0 License.
