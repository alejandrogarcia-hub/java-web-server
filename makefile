.PHONY: pipeline clean test run-dev run-prod

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