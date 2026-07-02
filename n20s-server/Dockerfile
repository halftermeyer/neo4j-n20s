# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY n20s-core/ n20s-core/
COPY n20s-server/ n20s-server/
RUN mvn clean package -pl n20s-core,n20s-server -am -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/n20s-server/target/n20s-server-*.jar n20s-server.jar
EXPOSE 7474
ENV PORT=7474
ENTRYPOINT ["java", "-jar", "n20s-server.jar"]
