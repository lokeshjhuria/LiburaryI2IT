FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY . .
RUN javac --add-modules jdk.httpserver Main.java

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/*.class /app/
COPY --from=builder /app/*.csv /app/
COPY --from=builder /app/data/ /app/data/

ENV PORT=8080
EXPOSE 8080

CMD ["java", "--add-modules", "jdk.httpserver", "Main"]
