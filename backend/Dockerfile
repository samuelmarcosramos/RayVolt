# Build stage: compile the jar with the Maven wrapper
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Download dependencies first so they are cached when only src changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline

COPY src src
# Tests run in CI, not in the image build
RUN ./mvnw -q package -DskipTests

# Run stage: only the JRE and the jar
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
