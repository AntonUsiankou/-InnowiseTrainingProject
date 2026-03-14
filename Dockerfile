FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu
WORKDIR /app
COPY target/InnowiseTraining-0.0.1-SNAPSHOT.jar app.jar
RUN groupadd --system --gid 1001 spring && \
    useradd --system --uid 1001 --gid 1001 spring && \
    chown -R spring:spring /app
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]