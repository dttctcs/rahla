
# Rahla: Lightweight EIP Runtime

Rahla is a karaf based dockerized kubernetes native application for camel based integration
services.

| Component | Version |
|-----------|---------|
| karaf     | 4.3.6   |
| osgi      | 7.0.0   |
| camel     | 3.14.2  |
| groovy    | 3.0.8   |
## Usage

You can locally run an example with:

```
$ docker run --network host --rm -v $PWD/example:/deploy -d --name rahla datatactics/rahla:latest
```
### Connect to a running instance
You can connect via  ```docker/podman/kunectl exec -it rahla client``` to the gogo shell. Or via
ssh on port ``8101``. Default login is ```admin:admin```. You can change admin password
via ``ADMIN_PASS`` environment variable.
### usefull commands:
- ```camel --help```
- ```display```
- ```list```
- ```diag #bundleId```
## Configuration
All rahla specific settings can be set via environment variables
### Environment Variables
| Name                        | value     | description                                                                      |
|-----------------------------|-----------|----------------------------------------------------------------------------------|
| ADMIN_PASS                  | string  | Set the admin pass                                                               |
| HEALTH_MODE                 | string  | strict mode let the heahtlc check fail if a bundle is not active, default: empty |                                 
| LOG_FORMAT                  | string  | json set the log output to json, default: empty                                  |
| OTEL_EXPORTER_OTLP_ENDPOINT | string  | sets the otel attributes e.g.: service.name=rahla, default: empty                |
| OTEL_RESOURCE_ATTRIBUTES    | string  | sets the otel endpoint e.g.: http://tempo:4317, default: empty                   |
## Concepts (optional)
Rahla extends default karaf and camel runtime with 

### Metrics
It comes with prometheus metrics for camel routes build in 
***!!! Important Hint !!!***
```id```s of routes and ```id```scontexts are added to the prometheus metric labels.

### RahlaLogFormatter
tbd
### Templates
tbd
### Processors
tbd
### Tracing
tbd
### Jolokia


