
# Rahla: Lightweight EIP Runtime

Rahla is a karaf based dockerized kubernetes native application for camel based integration
services.

| Component | Version |
|-----------|---------|
| karaf     | 4.4.3   |
| camel     | 3.20.5  |
## Usage

You can run an example with:

```
$ docker run --network host --rm -v $PWD/example:/deploy -d --name rahla datatactics/rahla:latest
```
or
```
mvn package
export RAHLA_DEPLOY_PATH=$(pwd)/examples
assembly/target/assembly/bin/karaf
```
## Configuration
All rahla specific settings can be set via environment variables
### Environment Variables
| Name                        | value  | description                                                                      |
|-----------------------------|--------|----------------------------------------------------------------------------------|
| ADMIN_PASS                  | string | Set the admin pass                                                               |
| HEALTH_MODE                 | string | strict mode let the heahtlc check fail if a bundle is not active, default: empty |                                 
| LOG_FORMAT                  | string | json set the log output to json, default: empty                                  |         
| RAHLA_DEPLOY_PATH           | string | sets the rahla deploy path, default: /deploy                                     |
## Concepts 
Rahla extends default karaf and camel runtime with 

### Metrics
It comes with prometheus metrics for java and camel routes build in 
***!!! Important Hint !!!***
```id```s of routes and ```id```scontexts are added to the prometheus metric labels.
tbd: custom metrics
### RahlaLogFormatter
tbd
### Templates
tbd
### Processors
tbd
### Tracing
tbd
### Jolokia
tbd
### Health Checks
tbd

# kingstone-k8s
