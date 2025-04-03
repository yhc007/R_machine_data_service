













```markdown:readmeAI.md
# Machine Data Service - Kubernetes Deployment Guide

## Prerequisites
- Minikube
- kubectl
- Docker
- SBT
- Java 11

## Project Setup

### 1. Build the Application
```bash
# Build JAR file
sbt clean assembly
```

### 2. Docker Setup
Create Dockerfile:
```dockerfile
FROM openjdk:11-jre-slim

WORKDIR /app
COPY target/scala-2.13/sirjin-data-service.jar /app/
COPY .env /app/

# Add entrypoint script
COPY docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080
EXPOSE 2551

ENTRYPOINT ["/app/docker-entrypoint.sh"]
```

Create docker-entrypoint.sh:
```bash
#!/bin/sh

# Load environment variables
set -a
. /app/.env
set +a

# Run application
exec java -jar /app/sirjin-data-service.jar
```

### 3. Kubernetes Configuration

#### Create CRD (Custom Resource Definition)
```yaml
# kubernetes/crds/akka-microservices-crd.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: akkamicroservices.akka.lightbend.com
spec:
  group: akka.lightbend.com
  names:
    kind: AkkaMicroservice
    listKind: AkkaMicroserviceList
    plural: akkamicroservices
    singular: akkamicroservice
  scope: Namespaced
  versions:
    - name: v1
      served: true
      storage: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              properties:
                # ... (other properties)
```

#### Create Deployment
```yaml
# kubernetes/local-data-service-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sirjin-data-service
  namespace: sirjin
spec:
  replicas: 1
  selector:
    matchLabels:
      app: sirjin-data-service
  template:
    metadata:
      labels:
        app: sirjin-data-service
    spec:
      containers:
        - name: sirjin-data-service
          image: sirjin-data-service:latest
          imagePullPolicy: Never
          ports:
            - containerPort: 8080
            - containerPort: 2551
          env:
            - name: CASSANDRA_KEYSPACE
              valueFrom:
                secretKeyRef:
                  name: default
                  key: CASSANDRA_KEYSPACE
            # ... (other environment variables)
```

## Deployment Steps

### 1. Build and Load Image
```bash
# Switch to minikube docker daemon
eval $(minikube docker-env)

# Build docker image
docker build -t sirjin-data-service:latest .
```

### 2. Create Kubernetes Resources
```bash
# Create namespace
kubectl create namespace sirjin

# Create secrets
kubectl create secret generic default \
  --namespace sirjin \
  --from-literal=CASSANDRA_KEYSPACE=sirjin \
  --from-literal=CASSANDRA_HOST_UNOMIC=127.0.0.1:9042 \
  --from-literal=CASSANDRA_USERNAME_UNOMIC=cassandra \
  --from-literal=CASSANDRA_PASSWORD_UNOMIC=cassandra

# Apply deployment
kubectl apply -f kubernetes/local-data-service-deployment.yml
```

### 3. Verify Deployment
```bash
# Check pod status
kubectl get pods -n sirjin

# Check logs
kubectl logs -n sirjin <pod-name>

# Port forward for local access
kubectl port-forward -n sirjin <pod-name> 8080:8080
```

## Troubleshooting

### Common Issues
1. Image Pull Error
   - Ensure image is built in minikube docker daemon
   - Check image exists: `docker images`

2. Secret Not Found
   - Verify secrets are created
   - Check secret values: `kubectl describe secret default -n sirjin`

3. Pod Not Starting
   - Check pod events: `kubectl describe pod -n sirjin <pod-name>`
   - Check logs: `kubectl logs -n sirjin <pod-name>`

## Environment Variables
Required environment variables in .env file:
- CASSANDRA_KEYSPACE
- CASSANDRA_HOST_UNOMIC
- CASSANDRA_USERNAME_UNOMIC
- CASSANDRA_PASSWORD_UNOMIC

## Additional Notes
- The service uses Akka Cluster for distributed processing
- Cassandra is used for persistence
- The application exposes HTTP port 8080 and Akka remoting port 2551
```
