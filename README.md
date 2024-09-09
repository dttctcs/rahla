# Rahla - An Open-Source Apache Camel Appliance for Apache Karaf

[![Build Status](https://github.com/dttctcs/rahla/actions/workflows/build.yaml/badge.svg)](https://github.com/dttctcs/rahla/actions/workflows/build.yaml)
[![Version](https://img.shields.io/badge/version-1.0.21--SNAPSHOT-blue)](https://github.com/dttctcs/rahla)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](https://github.com/dttctcs/rahla/blob/main/LICENSE)

Rahla is an open-source Apache Camel implementation meticulously crafted for deployment within Apache Karaf. It simplifies the process of building and deploying robust integration solutions leveraging the power of Camel's routing and mediation engine in an OSGi environment. 



## Installation and Configuration

### TODO Download or Container 

### Prerequisites

- **Java 17 or higher:** Ensure you have a compatible JDK installed.

### Steps


1. **Download Rahla Distribution:** 
   - Download the latest Rahla distribution from [Releases](https://github.com/dttctcs/rahla/releases).
2. **Deploy Rahla in Karaf:**
   - Extract the downloaded Rahla Karaf assembly (e.g., `rahla-1.0.21-SNAPSHOT.kar`) to any directory.
3. **Start Karaf:** 
   - TODO: Navigate to your Karaf installation directory and start Karaf.  
4. **Install Rahla Features:**
   - Once Karaf is started, use the following command to install the Rahla features:

     ```bash
     feature:install rahla 
     ```
   - The `rahla` feature includes the core Rahla functionality. Optionally, you can install additional features like `fradi` for stream processing capabilities.
5. Deploy to rahla
    TODO deploy to RAHLA_DEPLOY_PATH or rahla/deploy (TODO mention that deploy in camel xmls is configured via this env varialkle)


### Verification

- **List Installed Bundles:** Use the following command to verify Rahla bundles are installed and active:

  ```bash
  bundle:list 
  ```
- **Access Rahla Console:** Connect to the Rahla console using SSH:

  ```bash
  ssh -p 8101 admin@localhost 
  ```

  Default password is admin can be configured via ENV ADMIN_PASS or change content of *file TODO

## Usage and Architecture

### Architecture Overview

TODO add short examples for the configuration files and groovy bean factors
Rahla seamlessly integrates with Apache Karaf, utilizing OSGi services for dependency management and modularity. Its core features include: 

- **SmartURL Handling:** Rahla's `smarturl` component provides a flexible way to route messages to various destinations based on URL schemes, including `file`, `redis`, and `minio`.
- **Groovy Bean Factory:** Rahla offers a `GroovyBeanFactory` extension, allowing you to easily create and manage Camel beans written in Groovy. This enhances the scripting capabilities within your routes.
- **Template File Installer:** Easily deploy and manage Camel route templates using YAML files, streamlining route configuration and deployment.
- **Simplified Graph Access:** Rahla provides a `GraphSource` API to simplify access to graph databases like JanusGraph, making it easier to integrate graph operations into your integration flows. 
- **Siddhi Integration (Fradi Feature):** The optional `fradi` feature integrates Siddhi, a powerful complex event processing engine, into Camel routes. This allows for real-time data processing within your Camel integrations. small example
- **Jedis source**


### Configuration

The `etc/` directory contains important configuration files:
branding.properties
branding-ssh.properties
config.yaml
custom.properties
log4j2.xml
org.apache.karaf.log.cfg
org.apache.karaf.management.cfg
org.ops4j.pax.logging.cfg
org.ops4j.pax.url.mvn.cfg
users.properties
- **`config.yaml`:**  Configures the Prometheus JMX Exporter to expose Camel metrics for monitoring you can also add metrics mappings
- **`log4j2.xml`:** Configures Rahla's logging behavior, default configuration is for container usage including console output and optional Loki integration.

## Monitoring

For monitoring, Rahla comes equipped with Prometheus JMX Exporter integration. we recommend to start java agent 

- **Start JMX Exporter:**  
   - The Rahla Karaf assembly includes the necessary libraries. Add `-javaagent:./jmx_prometheus_javaagent-1.0.1.jar=9001:./jmx.yaml -javaagent:./opentelemetry-javaagent-2.4.0.jar` to `bin/karaf` startup script.
- **Access Prometheus Metrics:**  
   - Rahla will expose Prometheus metrics at `http://localhost:9001/metrics`. 



## Contributing

Contributions to Rahla are highly encouraged! 

- **File Issues:** Use the [GitHub issue tracker](https://github.com/dttctcs/rahla/issues) to report bugs or request new features.
- **Submit Pull Requests:**  Contribute code changes through pull requests. Please follow the project's coding standards.
- **License:** Rahla is licensed under the Apache 2.0 License.
