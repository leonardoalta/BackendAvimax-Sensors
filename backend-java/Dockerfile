FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && apt-get clean && rm -rf /var/lib/apt/lists/*

# Copy project files
COPY pom.xml .
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Expose port
EXPOSE 8080

# Run application
CMD ["java", "-jar", "target/avimax-backend-0.0.1-SNAPSHOT.jar"]
