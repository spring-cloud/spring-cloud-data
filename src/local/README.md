# Local Development Support

The files in this folder are to support local testing and development.

These scripts are useful when you want to build the containers on a different architecture and publish to a private repo.

## `download-apps.sh`
Downloads all applications needed by `create-containers.sh` from Maven repository.

*If the timestamp of snapshots matches the download will be skipped.*

Usage: `download-apps.sh [version]`
* `version` is the dataflow-server version like `2.10.3`. Default is `3.0.0-SNAPSHOT`

## `launch-dataflow.sh`
Uses docker compose to launch a database, broker, skipper and dataflow server.

## `launch-with-keycloak.sh`
Uses docker compose to launch a database, broker, skipper and dataflow server and a Keycloak server that loads the `dataflow` realm from `./src/local/data` which define a single user named `joe` with password `password`

## `stop-dataflow.sh`
Stops docker-compose and all running Java instances based on the pid files.

## `launch-keycloak.sh`
Launches standalone Keycloak and loads the dataflow realm from `./src/local/data` which define a single user named `joe` with password `password`

## `tail-container-log.sh`
Finds a container with provided name and tails the stdout of the container.

## Testing with Keycloak
### Build with Java 17
```shell
./mvnw clean install -am -pl :spring-cloud-dataflow-server -DskipTests -Dmaven.javadoc.skip=true -T 1C
```
### Execute:
```shell
./src/local/launch-keycloak.sh
```

### Debug
Module: `spring-cloud-dataflow-server`
Class: `org.springframework.cloud.dataflow.server.single.DataFlowServerApplication`
Arguments: `--spring.cloud.dataflow.features.streams-enabled=false --spring.cloud.dataflow.features.tasks-enabled=true --spring.cloud.dataflow.features.schedules-enabled=false --spring.config.additional-location="$ProjectFileDir$/src/local/application-dataflow-keycloak.yaml"`

### Run

```shell
java -jar spring-cloud-dataflow-server/target/spring-cloud-dataflow-server-3.0.0-SNAPSHOT.jar --spring.cloud.dataflow.features.streams-enabled=false --spring.cloud.dataflow.features.tasks-enabled=true --spring.cloud.dataflow.features.schedules-enabled=false --spring.config.additional-location="src/local/application-dataflow-keycloak.yaml" 
```