# app/Dockerfile — multi-stage, pinned digests, non-root, no secrets in layers (DESIGN §18)
# Build: docker build -t weekend-assistant app/
FROM maven:3.9-eclipse-temurin-17-noble@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-noble@sha256:bb8d601e02cec0e22f49b6f5d803dadc7db58ab692fa77909fb604d58187c7ed
RUN groupadd --system app && useradd --system --gid app --home /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /src/target/weekend-assistant.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1" \
    WEEKEND_LLM_PROVIDER=vertex
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
