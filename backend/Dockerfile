# 백엔드 운영 이미지. 저장소 루트에서 docker build -t keycheck-backend:local backend
# 테스트는 CI가 따로 돌리므로 여기서는 jar만 만든다

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon bootJar -x test

# 축소본 WebP 라이브러리가 glibc용 .so라 Alpine(musl)이 아닌 Ubuntu 기반 JRE를 쓴다
FROM eclipse-temurin:21-jre
# GHCR 패키지를 저장소에 연결한다
LABEL org.opencontainers.image.source="https://github.com/HongnamKim/keycheck"
RUN useradd --system --user-group --no-create-home --shell /usr/sbin/nologin keycheck
WORKDIR /app
COPY --from=build /src/build/libs/keycheck-backend-*.jar /app/app.jar
USER keycheck
# 2GB 서버 기준. 축소본 작업의 힙 피크(약 300MB)를 받고, OOM이면 프로세스를 끝내 Docker가 다시 띄우게 한다
# (축소본 작업은 프로세스가 죽은 횟수도 실패로 세어 같은 이미지에서 멈추지 않는다)
ENV JAVA_OPTS="-Xms256m -Xmx896m -XX:MaxMetaspaceSize=256m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# exec로 java가 PID 1이 되어 docker stop의 SIGTERM을 직접 받는다(server.shutdown: graceful로 처리 중인 요청을 마치고 내린다)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
