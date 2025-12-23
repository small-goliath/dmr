FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S dmr && adduser -S dmr -G dmr

RUN apk add --no-cache \
    tzdata \
    curl

ENV TZ=Asia/Seoul
RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

COPY build/libs/*.jar app.jar

RUN chown -R dmr:dmr /app

USER dmr

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
