# 1.2.0 (2025-05-20)


### Changes (Breaking)

* Camel feature was removed from preinstalled feature list, so add <feature>camel</feature> to your features.xml or install manually (Improves startup speed)
* SmartUrl was removed without replacement
* Jedis and Janusgraph sources have been moved into separate bundles and features <feature>graphsource</feature> <feature>jedissource</feature>
* Their interfaces have been moved from rahla.api to rahla.jedissource and rahla.graphsource ...interface="rahla.graphsource.GraphSource"...
* Base docker image was numped to java temurin 21 on alpine 3
* Updated to camel 4.10.3
* Updated janusgraph client to 1.1.0

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
