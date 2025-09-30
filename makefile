.PHONEY: pipeline

pipeline:
	./gradlew build
	./gradlew run

clean:
	./gradlew clean

test:
	./gradlew test

run:
	./gradlew run