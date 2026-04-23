# Dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/orders-service-*.jar app.jar

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

USER appuser

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]