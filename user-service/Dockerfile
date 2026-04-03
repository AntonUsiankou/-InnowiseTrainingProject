FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu
WORKDIR /app

# Важно: JAR ищется относительно корня контекста (сейчас это корень проекта)
COPY user-service/target/user-service-*.jar app.jar

# RUN обязательно с новой строки
RUN groupadd --system --gid 1001 spring && \
    useradd --system --uid 1001 --gid 1001 spring && \
    chown -R spring:spring /app

USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]