FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
COPY java ./java

RUN mvn -DskipTests package

FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app
COPY --from=build /app /app
COPY web ./web

EXPOSE 8080

CMD ["mvn", "exec:java"]