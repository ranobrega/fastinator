# Fastinator Makefile
# Uses Android Studio's bundled JDK
# Target device: a physical phone (e.g. Google Pixel 8) with USB debugging enabled,
# visible to adb. No emulator is used.

JAVA_HOME ?= /home/run/android-studio/jbr
ADB ?= /home/run/Android/Sdk/platform-tools/adb
PACKAGE_NAME ?= com.ruinobrega.fastinator

export JAVA_HOME
export PATH := $(JAVA_HOME)/bin:$(PATH)

.PHONY: compile compileDebugSources assemble assembleDebug test testDebugUnitTest verify devices install run clean help

compile: compileDebugSources

compileDebugSources:
	@echo "Compiling debug sources..."
	./gradlew compileDebugSources --no-daemon

assemble: assembleDebug

assembleDebug:
	@echo "Assembling debug APK..."
	./gradlew assembleDebug --no-daemon

test: testDebugUnitTest

testDebugUnitTest:
	@echo "Running unit tests..."
	./gradlew testDebugUnitTest --no-daemon

# Full verification: unit tests must pass before anything ships
verify: testDebugUnitTest assembleDebug

# Show adb-attached devices (connect your phone with USB debugging enabled)
devices:
	$(ADB) devices -l

install: testDebugUnitTest assembleDebug
	@echo "Installing APK on device..."
	$(ADB) wait-for-device
	$(ADB) install -r app/build/outputs/apk/debug/app-debug.apk

run: install
	@echo "Launching app on device..."
	$(ADB) shell am start -n $(PACKAGE_NAME)/.MainActivity

clean:
	./gradlew clean --no-daemon

help:
	@echo "Fastinator Build System"
	@echo ""
	@echo "Available targets:"
	@echo "  compile     - Compile debug sources"
	@echo "  assemble    - Build debug APK"
	@echo "  test        - Run unit tests"
	@echo "  verify      - Run tests and build the APK"
	@echo "  devices     - List adb-attached devices (phone with USB debugging)"
	@echo "  install     - Test, build, and install APK on the connected phone"
	@echo "  run         - Install and launch the app on the phone"
	@echo "  clean       - Clean build files"
	@echo ""
	@echo "Environment:"
	@echo "  JAVA_HOME=$(JAVA_HOME)"
	@echo "  ADB=$(ADB)"
	@echo ""
	@echo "Usage: make <target>"
