FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu
LABEL authors="MoJOS"
WORKDIR /app
COPY target/auth-service-*.jar app.jar
RUN groupadd --system --gid 1001 spring && \
    useradd --system --uid 1001 --gid 1001 spring && \
    chown -R spring:spring /app
USER spring:spring
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]