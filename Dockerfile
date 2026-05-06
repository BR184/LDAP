FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

ARG JAR_FILE=target/corp-idm-platform-0.1.0-SNAPSHOT.jar

COPY ${JAR_FILE} app.jar

RUN mkdir -p /app/imports

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
