.DEFAULT_GOAL := build

.PHONY: install-tools comments lint lint-fix format test-build test docs build clean publish publish-local publish-check

install-tools:
	python3 -m pip install --quiet --upgrade git+https://github.com/botforge-pro/commentcensor.git

comments:
	commentcensor .

lint: comments
	./gradlew ktlintCheck

lint-fix:
	./gradlew ktlintFormat

format: lint-fix

# TEST names one class or method of the core, fully qualified, for a focused run:
# make test TEST=fm.apakabar.lettergrid.SeededRandomTests
test:
ifdef TEST
	./gradlew :core:test --tests "$(TEST)"
else
	./gradlew :core:test :ui:testDebugUnitTest
endif

test-build:
	./gradlew :core:compileTestKotlin :ui:compileDebugUnitTestKotlin

docs:
	./gradlew :dokkaGeneratePublicationHtml

build: lint test-build test docs
	./gradlew assemble

publish:
	@test -n "$(CI)" || { echo "publish runs in the release workflow, not locally" >&2; exit 1; }
	./gradlew publishAndReleaseToMavenCentral

publish-local:
	./gradlew publishToMavenLocal -PunsignedLocalPublish

publish-check:
	./gradlew publishToMavenLocal

clean:
	./gradlew clean
