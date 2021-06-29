# Rahla: Convenient Camel Routes in k8s

Rahla is a karaf based dockerized kubernetes native application for camel based integration
services.

| Component | Version|
|:---|:---|
|karaf|4.3.2|
|osgi|7.0.0|
|camel|3.10.0|
|groovy|3.0.8|


It comes with prometheus metrics for camel routes build in as well as json logging, and the
possibility of managing (add/remove/start/stop) routes during runtime over remote shell or jmx.

***!!! Important Hint !!!***
```id```s of routes and contexts are added to the prometheus metric labels.
## Dev mode
You can locally run an example with:
```
```shell
$ docker run --network host --rm -v ./example/local:/deploy -d --name rahla dttctcs/rahla:latest debug
```
If you have a custom settings.xml mount it to `/rahla/etc/settings.xml`
## Connect to a running instance

You can connect via  ```docker/podman/kunectl exec -it rahla client``` to the gogo shell. Or via
ssh on port ``8102``. Default login is ```admin:admin```. You can change admin password
via ``ADMIN_PASS`` environment variable.

### usefull commands:

- ```camel --help```
- ```display```
- ```list```
- ```diag #bundleId```

## Example kustomize project structure

When starting a new integration project we recommend the following project structure. You can find an example project in the examples folter. If its more
simple you could merge ```common``` into ```oneintegration/contexts```. But config files stay the same.

```
├── common
│     ├── environment.properties
│     ├── features.xml
│     ├── keystore.jks
│     ├── kustomization.yaml
│     ├── settings.xml
│     └── patch.yaml
├── oneintegration
│     ├── contexts
│     │    ├── routes.xml
│     │    ├── kustomization.yaml
│     │    └── aGroovyProcessor.groovy
│     ├── int
│     │    ├── settings.cfg
│     │    └── kustomization.yaml
│     └── prod
│          ├── settings.cfg
│          └── kustomization.yaml
└── anotherintegration
│     ├── ...



```

### common/kustomization.yaml

***!!! Important Hint !!!***
When using runtime updates of mounted config maps in kubernetes it can take up to one minute until
the files are re-mounted inside the running container.

```yaml
commonLabels:
  key: value

configMapGenerator:
  - name: env-cm
    envs:
      - environment.properties
  # environmental variales

  - name: deploy-cm
    behavior: merge
    files:
      - features.xml
  # all routes, features, configs go to deploy-cm; it ismouted to /deploy

  - name: data-cm
    behavior: replace
    files:
      - keystore.jks
    # additional needed files go to data-cm; it ismouted to /data

  - name: settings-xml-cm
    files:
      - settings.xml
# optional maven settings if you run in a closed env


generatorOptions:
  disableNameSuffixHash: false
# in base kustomization disableNameSuffixHash is true for deploy-cm and data-cm sp that change does not restart the pod

patchesStrategicMerge:
  - patch.yaml

resources:
  - ssh://git@github.com:dttctcs/rahla.git//base?ref=v4.0.0
# we use the base kustomization from this repo in a specific released version
```

### common/environment.properties

See [Karaf documentation](https://karaf.apache.org/manual/latest/#_environment_variables_system_properties)
for detailed usage on env variables and how to overwrite java properties.

***!!! Important Hint !!!*** Changing EXTRA_JAVA_OPTS requires a manual pod restart.

```properties
JAVA_MAX_MEM=768M
EXTRA_JAVA_OPTS=-Dcom.ibm.mq.cfg.useIBMCipherMappings=false -Djavax.net.ssl.trustStore=/data/keystore.jks -Djavax.net.ssl.trustStorePassword=123456 -Djavax.net.ssl.keyStore=/data/keystore.jks -Djavax.net.ssl.keyStorePassword=123456
```

### common/patch.yaml

We patch limits and add our settings.xml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: rahla
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: rahla
    spec:
      containers:
        - name: rahla
          envFrom:
            - configMapRef:
                name: env-cm
          resources:
            limits:
              cpu: "1"
              memory: 1024Mi
            requests:
              cpu: "0.1"
              memory: 768M
          volumeMounts:
            - name: settings-xml-volume
              subPath: "settings.xml"
              mountPath: "/rahla/etc/settings.xml"
              readOnly: true
      volumes:
        - name: settings-xml-volume
          configMap:
            name: settings-xml-cm
```

### common/features.xml

This feature is automatically installed on startup and adds all features and bundles even from maven
central (our local Maven repo)

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<features xmlns="http://karaf.apache.org/xmlns/features/v1.6.0" name="runtime">

  <feature name="runtime" version="1" install="auto">
    <feature>camel-kafka</feature>
    <bundle>wrap:mvn:com.ibm/mq.allclient/9.0.1.0</bundle>
  </feature>

</features>

```

### oneintegration/contexts/routes.xml
***!!! Important Hint !!!***
Remember url encoding for &```&amp;```

***!!! Important Hint !!!***
Take note of the property placeholder configuration. It loads the osgi config element from
settings.cfg during runtime and updates the context duriing rutime

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0"
  xmlns:cm="http://aries.apache.org/blueprint/xmlns/blueprint-cm/v1.1.0">
  
  <cm:property-placeholder id="settings.props" persistent-id="settings" update-strategy="reload"/>

  <camelContext xmlns="http://camel.apache.org/schema/blueprint/camel-blueprint-3.10.0.xsd">

    <propertyPlaceholder id="properties" location="blueprint:settings.props"/>

    ...

  </camelContext>

</blueprint>

```

### oneintegration/came/kustomization.yaml

Adds the routes/camel contexts to the deploy-cm

```yaml
configMapGenerator:
  - name: deploy-cm
    behavior: merge
    files:
      - routes.xml

resources:
  - ../../common

```

### oneintegration/contexts/aGroovyProcessor.groovy

You can add groovy processors during runtime which are registred as osgi services with the
property ```rahla.camel.processor=<filename>```

#### Example:

```groovy
import org.apache.camel.Exchange;
import org.apache.camel.Processor;


public class MyProcessor implements Processor {
    void process(Exchange exchange) throws Exception {
        //do something or not
    }
}
```

Outside of the camel context you can inject the service with

```xml

<reference id="myProcessor" interface="org.apache.camel.Processor"
  filter="(rahla.camel.processor=aGroovyProcessor)"/>
```

In a route you can then use the processor with

```xml

<process ref="myProcessor"/>
```

### oneintegration/[int/prod]/settings.cfg

You can add configurable variables with ```{{EXAMPLE_VAR}}``` to your camel contexts which are
replaced with the config from here. (If you configured property placeholder correctly)

```properties
EXAMPLE_VAR=value
```

### oneintegration/[int/prod]/settings.cfg

The final kustomization only merges the settings into the deploy config map.

```yaml
configMapGenerator:
  - name: deploy-cm
    behavior: merge
    files:
      - settings.cfg

resources:
  - ../contexts
```

## Advanced examples:

This section contains several more advanced examples which should make your life easier

### Rest Server with Swagger docu

```xml

<camelContext>
  <restConfiguration component="jetty" bindingMode="json"
    contextPath="/base"
    port="8183"
    apiHost="localhost"
    apiContextListing="true"
    apiContextPath="api-docs"
    enableCORS="true">
    <dataFormatProperty key="prettyPrint" value="true"/>
    <apiProperty key="api.version" value="1.0.0"/>
    <apiProperty key="api.title" value="title"/>
    <apiProperty key="api.description" value="description"/>
    <apiProperty key="api.contact.name" value="contact.name"/>
    <apiProperty key="api.contact.email" value="contact.email"/>
    <apiProperty key="api.contact.url" value="contact.url"/>
  </restConfiguration>

  <rest path="/api/v0/">
    <get uri="/example" id="rest-get-test-service" produces="text/plan">
      <to uri="mock:foo"/>
    </get>
  </rest>
</camelContext>

```

### ErrorHandler

```xml

<camelContext errorHandlerRef="myErrorHandler"
  useMDCLogging="true">
  <errorHandler id="myErrorHandler" type="DefaultErrorHandler">
    <redeliveryPolicy maximumRedeliveries="-1" retryAttemptedLogLevel="WARN" backOffMultiplier="2"
      useExponentialBackOff="true"/>
  </errorHandler>
  ...
</camelContext>
```

### IBM MQ
Add to your feature.xml:
```xml
<bundle>wrap:mvn:com.ibm/mq.allclient/9.0.1.0</bundle>
<feature>pax-jms-pool</feature>
<feature>pax-jms-ibmmq</feature>
``` 
```xml
<camelConext>
  <route>
    <from uri="ibmmq:queue:FROM.QUEUE" />
    <to uri="ibmmq:queue:TO.QUEUE" />
  </route>
</camelConext>

<bean id="mqConnectionFactory" class="com.ibm.mq.jms.MQConnectionFactory">
  <property name="hostName" value="{{ibmmq.host}}"/>
  <property name="port" value="{{ibmmq.port}}"/>
  <property name="queueManager" value="{{ibmmq.manager}}"/>
  <property name="channel" value="{{ibmmq.channel}}"/>
  <property name="transportType" value="1"/>
  <property name="shareConvAllowed" value="0"/>
  <property name="SSLCipherSuite" value="TLS_RSA_WITH_AES_128_CBC_SHA256"/>
</bean>
<bean id="mqcredential" class="org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter">
  <property name="targetConnectionFactory" ref="mqConnectionFactory"/>
  <property name="username" value="mqm"/>
  <property name="password" value="mqm"/>
</bean>
<bean id="ibmmq" class="org.apache.camel.component.jms.JmsComponent">
  <property name="connectionFactory" ref="mqConnectionFactory"/>
  <property name="maxConcurrentConsumers" value="1"/>
  <property name="cacheLevelName" value="CACHE_CONSUMER"/>
  <property name="lazyCreateTransactionManager" value="false"/>
  <property name="transacted" value="true"/>
</bean>
```
### Active MQ
Add to your feature.xml:

```xml
<feature>pax-jms-activemq</feature>
<feature>pax-jms-pool</feature>
``` 
```xml
<camelConext>
  <route>
    <from uri="activemq:queue:FROM/QUEUE" />
    <to uri="activemq:queue:TO/QUEUE" />
  </route>
</camelConext>

<bean id="activemq" class="org.apache.camel.component.activemq.ActiveMQComponent">
  <property name="brokerURL" value="${activemq.url}?randomize=false&amp;timeout=3000"/>
  <property name="userName" value="${activemq.user}"/>
  <property name="password" value="${activemq.password}"/>
</bean>
```
### Artemis MQ
```xml
TODO
```

### Oracle AQ
```xml
TODO
```

### Http Proxy
```xml
<to uri="https://your.internet.address?proxyAuthHost={{PROXY_HOST}}&amp;proxyAuthPort={{PROXY_PORT}}&amp;proxyAuthUsername={{PROXY_USER}}&amp;proxyAuthPassword={{PROX_PASS}}&amp;proxyAuthScheme={{PROXY_SCHEME}}"/>
```
### JDBC Query

add pax jdbc config
```properties
osgi.jdbc.driver.class: org.postgresql.Driver
url: jdbcUrl
user: userName
password: userPass
dataSourceName: aDataSource
```
```xml
TODO
```
### Message Logging
All ```logName``` which start with ```rahla.msg.``` are not logged into ```log:display``` appender but are viewable in central log management via loggerName filtering.

```xml
<route>
  <log message="${body}" loggingLevel="INFO" logName="rahla.msg."/>
</route>
```
### Kafka
Add feature
```xml
<feature>camel-kafka</feature>
```

```xml
<from uri="kafka:topic?brokers=localhost:9092&amp;clientId=aClient&amp;groupId=aGroup&amp;maxRequestSize=10000000"/>
```