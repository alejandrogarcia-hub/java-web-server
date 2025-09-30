.PHONY: pipeline clean test run-dev run-prod docker-build docker-run docker-up docker-down docker-logs docker-clean

pipeline:
	./gradlew build
	./gradlew run

clean:
	./gradlew clean

test:
	./gradlew test

run-dev:
	ENV=dev ./gradlew run --quiet

run-prod:
	ENV=production ./gradlew run --quiet

# Docker commands
docker-build:
	docker build -t java-web-server:latest .

docker-run:
	docker run -p 8080:8080 -e ENV=production --name java-web-server java-web-server:latest

docker-up:
	docker-compose up -d

docker-down:
	docker-compose down

docker-logs:
	docker-compose logs -f web-server

docker-clean:
	docker-compose down -v
	docker rmi java-web-server:latest || true