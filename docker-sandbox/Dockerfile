FROM eclipse-temurin:17-jre

# Create a non-privileged user
RUN useradd -m sandbox

# Set working directory
WORKDIR /home/sandbox

# Explicitly ensure we are running as the sandbox user
USER sandbox

# No additional setup needed, Main.class will be mounted or copied
CMD ["java", "-Xmx64m", "-Xms16m", "Main"]
