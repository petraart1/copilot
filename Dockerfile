FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache bash

WORKDIR /app
ENV GRADLE_USER_HOME=/cache/gradle

COPY gradlew ./
COPY gradle gradle/
COPY settings.gradle ./
COPY build.gradle ./

RUN chmod +x gradlew

RUN --mount=type=cache,target=/cache/gradle \
    ./gradlew dependencies --no-daemon

COPY src src/

RUN --mount=type=cache,target=/cache/gradle \
    ./gradlew bootJar -x test --no-daemon

RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination extracted

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /app/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/application/ ./

USER spring:spring

ENV JAVA_OPTS="-XX:InitialRAMPercentage=75.0 \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+UseStringDeduplication \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]