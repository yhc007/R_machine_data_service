## Running the sample code

1. Start a first node:

    ```
    sbt -Dconfig.resource=local1.conf run
    ```

2. (Optional) Start another node with different ports:

    ```
    sbt -Dconfig.resource=local2.conf run
    ```

3. Check for service readiness

    ```
    curl http://localhost:9101/ready
    ```
<<<<<<< Updated upstream
--------------
.env
```
CASSANDRA_USERNAME="cassandra"
CASSANDRA_PASSWORD="cassandra"
SECURE_CONNECT_DB=

CASSANDRA_HOST_UNOMIC="127.0.0.1:9042"
CASSANDRA_USERNAME_UNOMIC="cassandra"
CASSANDRA_PASSWORD_UNOMIC="cassandra"
CASSANDRA_KEYSPACE="sirjin"

KAFKA_TOPIC="machine-data-event"
KAFKA_BROKER="http://127.0.0.1:9092"
KAFKA_BROKER_UNOMIC="http://127.0.0.1:9092"
KAFKA_SSL_CONFIG=

QUILL_CTX_URL="postgresql://localhost:5432/sirjin?user=elfin&password=gksrkd"

RESET_HOUR=8
RESET_MINUTE=0
MODE=dev
```
-------------

# Machine Data Service

Machine data collection and processing service using Akka, Cassandra, and Kafka.

## Prerequisites
- Java 8 or higher
- SBT
- Cassandra (3x 버전, only java 8)
- Kafka (Docker로 실행)

## Setup
1. Clone the repository
2. Configure .env file
3. Start Cassandra and Kafka
4. Run `sbt run`

## Configuration
Environment variables in .env:
- CASSANDRA_HOST_UNOMIC
- CASSANDRA_USERNAME_UNOMIC
- CASSANDRA_PASSWORD_UNOMIC
- KAFKA_BROKER_UNOMIC
=======


sudo update-alternatives --config java

## Make image and Use kubernetes

### Dockerfile
```
FROM openjdk:11-jre-slim

WORKDIR /app
COPY target/scala-2.13/sirjin-data-service.jar /app/
COPY .env /app/

EXPOSE 8080
EXPOSE 2551

CMD ["java", "-jar", "sirjin-data-service.jar"]
```
### JAR Create
```
sbt clean assembly
```

### Build image
```
docker build -t sirjin-data-service .
```

### Run image
```
docker run -p 8080:8080 -p 2551:2551 sirjin-data-service:latest
```

### minikube install & start
```
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
minikube start
```

### 순서대로 실행
#### 1. 네임스페이스 생성 (아직 없다면)
```
kubectl create namespace sirjin
```

#### 2. 시크릿 생성
```
kubectl create secret generic default \
  --namespace sirjin \
  --from-file=.env
```

#### 3. 시크릿 확인
```
kubectl get secrets -n sirjin
kubectl describe secret default -n sirjin
```

### Akka Operator 설치
#### 1. Akka Operator CRD 설치 
#### 이건 설치 안됨됨
```
kubectl apply -f https://raw.githubusercontent.com/lightbend/akka-operator/main/deploy/crds/akka.lightbend.com_akkamicroservices.yaml
```

#### 2. Akka Operator 설치
```
kubectl apply -f https://raw.githubusercontent.com/lightbend/akka-operator/main/deploy/operator.yaml
```

#### 3. 설치 확인
```
kubectl get crds | grep akka
kubectl get pods -n lightbend
```

### 그래서 이렇게 했음
#### 1. CRD 로컬에 생성
```
mkdir -p kubernetes/crds
akka-microservice-crds.yaml 생성
```
#### 2. CRD 적용
```
kubectl apply -f kubernetes/crds/akka-microservices-crd.yaml
```


#### 3. 이제 서비스 배포 시도
```
kubectl apply -f kubernetes/local-data-service.yml
```

#### 4. 서비스 확인
```
kubectl get pods -n sirjin
```

#### 5. 서비스 접속
```
kubectl port-forward svc/local-data-service 8080:8080 -n sirjin
```

#### 6. 서비스 접속 확인
```
curl http://localhost:8080/health
```


>>>>>>> Stashed changes
