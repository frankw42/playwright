# Playwright Java with browsers (match your old base)
FROM mcr.microsoft.com/playwright/java:v1.54.0-noble

# Optional JVM limits like your old image
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx320m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# Install Clojure CLI
RUN apt-get update -y && apt-get install -y curl \
 && curl -L https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh | bash \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app