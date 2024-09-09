# Rahla - An Open-Source Apache Camel Appliance for Apache Karaf

[![Build Status](https://github.com/dttctcs/rahla/actions/workflows/build.yaml/badge.svg)](https://github.com/dttctcs/rahla/actions/workflows/build.yaml)
[![Version](https://img.shields.io/badge/version-1.0.21--SNAPSHOT-blue)](https://github.com/dttctcs/rahla)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](https://github.com/dttctcs/rahla/blob/main/LICENSE)

Rahla is an open-source Apache Camel implementation meticulously crafted for deployment within Apache Karaf. It simplifies the process of building and deploying robust integration solutions leveraging the power of Camel's routing and mediation engine in an OSGi environment. 

Containerized appliance availabe under ```docker.io/datatactics/rahla```

## Installation and Configuration

### Prerequisites

- **Java 17 or higher:** Ensure you have a compatible JDK installed.
- **Apache Karaf 4.4.6:**  Download and install Apache Karaf.

### Steps

1. **Download Rahla Distribution:** 
   - Download the latest Rahla distribution from [Releases](https://github.com/dttctcs/rahla/releases).
2. **Deploy Rahla in Karaf:**
   - Copy the downloaded Rahla Karaf assembly (e.g., `rahla-1.0.21-SNAPSHOT.kar`) to the `deploy` directory of your Karaf installation.
3. **Start Karaf:** 
   - Navigate to your Karaf installation directory and start Karaf. 
4. **Install Rahla Features:**
   - Once Karaf is started, use the following command to install the Rahla features:

     ```bash
     feature:install rahla 
     ```
   - The `rahla` feature includes the core Rahla functionality. Optionally, you can install additional features like `fradi` for stream processing capabilities.

### Verification

- **List Installed Bundles:** Use the following command to verify Rahla bundles are installed and active:

  ```bash
  bundle:list | grep rahla
  ```
- **Access Rahla Console:** Connect to the Rahla console using SSH:

  ```bash
  ssh -p 8101 karaf@localhost 
  ```

## Usage and Architecture

### Architecture Overview

Rahla seamlessly integrates with Apache Karaf, utilizing OSGi services for dependency management and modularity. Its core features include:

- **SmartURL Handling:** Rahla's `smarturl` component provides a flexible way to route messages to various destinations based on URL schemes, including `file`, `redis`, and `minio`.
- **Groovy Bean Factory:** Rahla offers a `GroovyBeanFactory` extension, allowing you to easily create and manage Camel beans written in Groovy. This enhances the scripting capabilities within your routes.
- **Template File Installer:** Easily deploy and manage Camel route templates using YAML files, streamlining route configuration and deployment.
- **Simplified Graph Access:** Rahla provides a `GraphSource` API to simplify access to graph databases like JanusGraph, making it easier to integrate graph operations into your integration flows. 
- **Siddhi Integration (Fradi Feature):** The optional `fradi` feature integrates Siddhi, a powerful stream processing engine, into Camel routes. This allows for real-time data processing within your Camel integrations.

### Project Structure

- **rahla/commands/:** Contains Karaf commands to manage and interact with Rahla.
- **rahla/extensions/:** Houses extensions that provide additional functionality to Rahla, such as `GroovyBeanFactory`, `GraphSource`, and custom processors.
- **rahla/smarturl/:**  Implements the SmartURL mechanism, allowing for dynamic routing based on URL schemes.

### Configuration

The `etc/` directory contains important configuration files:

- **`config.yaml`:**  Configures the Prometheus JMX Exporter to expose Camel metrics for monitoring.
- **`log4j2.xml`:** Configures Rahla's logging behavior, including console output and optional Loki integration.

## Deployment and Monitoring

Deploying Rahla typically involves copying the Karaf assembly to the Karaf `deploy` directory. For monitoring, Rahla comes equipped with Prometheus JMX Exporter integration.  

- **Start JMX Exporter:**  
   - The Rahla Karaf assembly includes the necessary libraries. Add `-javaagent:./jmx_prometheus_javaagent-1.0.1.jar=8080:./jmx.yaml -javaagent:./opentelemetry-javaagent-2.4.0.jar` to `bin/karaf` startup script.
- **Access Prometheus Metrics:**  
   - Rahla will expose Prometheus metrics at `http://localhost:8080/metrics`. 

## Contributing

Contributions to Rahla are highly encouraged! 

- **File Issues:** Use the [GitHub issue tracker](https://github.com/dttctcs/rahla/issues) to report bugs or request new features.
- **Submit Pull Requests:**  Contribute code changes through pull requests. Please follow the project's coding standards.
- **License:** Rahla is licensed under the Apache 2.0 License.
