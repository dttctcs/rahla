# 1.2.2 (2025-09-19)


### Changes 

* Switched base image from eclipse-temurin:21-jre-alpine to eclipse-temurin:21-jre


# 1.2.1 (2025-07-15)


### Changes 

* Camel Osgi Route Templates have been moved into a separate feature `<feature>camel-route-templates</feature>`


# 1.2.0 (2025-05-20)


### Changes (A lot breaking)

* Camel feature was removed from preinstalled feature list, so add `<feature>camel</feature>` to your features.xml or install manually (Improves startup speed)
* SmartUrl was removed without replacement
* Jedis and Janusgraph sources have been moved into separate bundles and features `<feature>graphsource</feature>` `<feature>jedissource</feature>`
* Interfaces GraphSource and JedisSource have been moved from rahla.api to rahla.jedissource and rahla.graphsource. For referencing: `interface="rahla.graphsource.GraphSource"`
* Base docker image was bumped to java temurin 21 on alpine 3
* Updated to camel 4.10.3
* Updated janusgraph client to 1.1.0
* We recommmend to use /rahla/deploy instead of /deploy. Hence, RAHLA_DEPLOY_PATH env variable and ```org.apache.felix.fileinstall-rahla.cfg ```will be removed in a future release. You can add you own file install handler via deploy.
    ```
    felix.fileinstall.dir = ${env:RAHLA_DEPLOY_PATH:-/deploy/}
    felix.fileinstall.filter = .*
    felix.fileinstall.poll = 1000
    felix.fileinstall.start.level = 80
    felix.fileinstall.active.level = 80
    felix.fileinstall.log.level = 3
   ```
* Siddhi was set to deprecated due to inactivity of the project. If db crud is required we recommend migrating to camel-mybatis
* resource:deploy for Groovy bean Factory is set to deprecated. Groovy bean Factory now accepts URLs like file:// http://
* Camel resource:deploy in context is set to deprecated. Use resource:file: with fully qualified path or relative to /rahla instead

#### Tips

If you build your own container from rahla base image you can start and stop karaf during building the images. This preinstalls all features and keeps container start times very low.
```
FROM datatactics/rahla:latest
USER rahla
ADD deploy /rahla/deploy
RUN (sleep 60; karaf stop ) & karaf server
```
  
  

# 1.1.2 (2025-02-26)


### Bug Fix

* Added Capabilities to handle SpiFly handling for ServiceLoader


# 1.1.1 (2025-02-12)


### Changes

* Shaded Groovy Libs into Rahla bundle until groovy 4 is implemented as an fragment host

# 1.1.0 (2025-02-07)


### Changes

* Camel Version : 4.9.0
* Karf Version: 4.4.7
* Mulitiple dependency updates


# 1.0.21 (2024-XX-XX)


### Changes

* Added documentaiton
* Camel Version : 3.22.2
* Karf Version: 4.4.6
