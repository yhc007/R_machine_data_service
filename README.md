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
--------------
.env
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
