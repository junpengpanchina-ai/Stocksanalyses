FROM eclipse-temurin:21-jre
WORKDIR /app
ARG JAR=target/kline-analytics-0.0.1-SNAPSHOT.jar
COPY ${JAR} app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dspring.profiles.active=prod -jar app.jar"]


