#!/bin/bash

echo "Setting up Android Notification Breakthrough project..."

# Create settings.gradle.kts
cat > settings.gradle.kts << 'EOF'
rootProject.name = "RegexAlerts"
include(":app")
EOF

# Create root build.gradle.kts
cat > build.gradle.kts << 'EOF'
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
EOF

# Create gradle.properties
cat > gradle.properties << 'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
EOF

# Create local.properties (will need to be edited with correct SDK path)
if [ ! -f local.properties ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        echo "sdk.dir=$HOME/Android/Sdk" > local.properties
    else
        echo "sdk.dir=/path/to/your/Android/Sdk" > local.properties
        echo "⚠️  Please edit local.properties with your Android SDK path!"
    fi
fi

# Download gradle wrapper if not present
if [ ! -f gradlew ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p gradle/wrapper

    cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

    # Create gradlew script
    curl -L https://raw.githubusercontent.com/gradle/gradle/master/gradlew -o gradlew
    chmod +x gradlew

    # Create gradlew.bat for Windows
    curl -L https://raw.githubusercontent.com/gradle/gradle/master/gradlew.bat -o gradlew.bat
fi

echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "1. Ensure Android SDK is installed"
echo "2. Edit local.properties if needed to point to your SDK"
echo "3. Run: ./gradlew assembleDebug"
echo "4. Find APK at: app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "Or open this folder in Android Studio for easier building!"