FROM eclipse-temurin:21-jre-alpine

WORKDIR /hermes-gateway

COPY build/libs/gateway.jar gateway.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "gateway.jar"]