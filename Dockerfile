FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /repo
COPY . .
RUN cd backend && mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /repo/backend/target/backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
