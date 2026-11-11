FROM eclipse-temurin:21-jre-alpine

WORKDIR /hermes-worker

COPY build/libs/worker.jar worker.jar

ENTRYPOINT ["java", "-jar", "worker.jar"]