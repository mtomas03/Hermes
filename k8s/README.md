# Hermes deployment

## What is deployed

All resources are deployed inside the `hermes-namespace` namespace:

- **gateway-deployment**: Hermes Gateway (3 replicas), exposed through NGINX Ingress.
- **postgres-statefulset**: PostgreSQL database (`hermes-postgres`) for accounts. Deliberately a **single instance** (ACID integrity, no failover); probes use `pg_isready`.
- **redis-deployment**: Redis (`hermes-redis`) for user presence and message routing state.
- **hermes-kafka**: Single-node Kafka broker (`hermes-kafka`) operating in KRaft mode (single point of failure, replication factor 1).
- **cassandra-statefulset**: Cassandra cluster of 3 nodes (one PVC each, governed by the headless Service `hermes-cassandra-headless`; clients use the ClusterIP Service `hermes-cassandra`). Keyspace `hermes` uses `NetworkTopologyStrategy` with 3 replicas in `datacenter1`; the services use `LOCAL_QUORUM` (2 of 3), so one node can be down. A PodDisruptionBudget allows at most one voluntary disruption.
- **worker-deployment**: 3 replicas of the worker, in the same Kafka consumer group `hermes-worker`, which consumes messages from Kafka and writes them to Cassandra. Replicas are stateless; correctness under concurrent processing relies on Cassandra lightweight transactions, not on Kafka partition ownership. Kafka topics are created with 3 partitions (`KAFKA_NUM_PARTITIONS`), so each replica can own one partition; topics that already exist keep their partition count (see below).
- **cassandra-schema Job**: One-off Job (`cassandra-schema`) executing `schema.cql` to initialise the `hermes` keyspace and tables.
- **NGINX Ingress**: Routes external traffic from `hermes.local` to `hermes-gateway` on port `80` while maintaining persistent WebSocket connections.

## Prerequisites

- Docker
- Minikube
- kubectl
- Java 21
- Bash shell environment

## Deploy

Navigate to the scripts directory and execute `deploy.sh`:

```bash
cd k8s/scripts
./deploy.sh
```

The execution flow of `deploy.sh` performs the following steps:

1. Starts Minikube if it is not already running;
2. Enables the NGINX Ingress addon (`minikube addons enable ingress`);
3. Executes `build-images.sh` to build the application executable Spring Boot artefacts (`:bootJar`) and compile Docker images directly inside Minikube's Docker daemon;
4. Prepares the `cassandra-schema` ConfigMap and resets any previous initialisation Job;
5. Applies all manifests via Kustomize (`kubectl apply -k`);
6. Waits for core infrastructure components (`postgres-statefulset`, `cassandra-statefulset`, `hermes-kafka`, `redis-deployment`);
7. Waits for the `cassandra-schema` Job to complete execution;
8. Waits for application deployments (`gateway-deployment`, `worker-deployment`) to reach `Ready` status.

`build-images.sh` can also be run independently after Minikube is started:

```bash
./build-images.sh
```

## Host Entry & Network Access

To route local requests to the NGINX Ingress Controller inside Minikube, start the network tunnel in a separate terminal (with administrator/sudo privileges):

```bash
minikube tunnel
```

Add `127.0.0.1` mapping to `hermes.local` in your host system's resolution file:

```text
127.0.0.1 hermes.local
```

* **Linux / macOS**: `/etc/hosts`
* **Windows**: `C:\Windows\System32\drivers\etc\hosts`

## Useful Commands

Check cluster status in hermes-namespace
```bash
./status.sh
```

Tear down all resources of the cluster
```bash
./undeploy.sh
```
