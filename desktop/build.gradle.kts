import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// The entry font is shared with the Android app.
sourceSets.main {
    resources.srcDir("../app/src/main/res/font")
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.compose.desktop.icons)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(libs.kotlin.test)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

tasks.test {
    useJUnitPlatform()
    // UI tests can save screenshots here (see JournalScreenTest).
    System.getenv("JOURNAL_SCREENSHOTS")?.let { environment("JOURNAL_SCREENSHOTS", it) }
}

compose.desktop {
    application {
        mainClass = "nl.markbroeders.journal.desktop.MainKt"
        // Run and bundle the JetBrains Runtime: it has a native Wayland toolkit, so the window is sharp
        // at fractional scales (main() switches to it on Wayland). Plain JDKs run via XWayland.
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
            vendor = JvmVendorSpec.JETBRAINS
        }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm)
            modules("java.instrument", "java.prefs", "jdk.unsupported")
            packageName = "journal"
            packageVersion = "0.1.0"
            description = "Journal: write daily notes in an Obsidian vault"
            linux {
                shortcut = true
                menuGroup = "Office"
                iconFile.set(project.file("icons/journal.png"))
            }
        }
    }
}

// Java doesn't follow window resizes under tiling window managers (sway, i3) unless told the
// window manager doesn't reparent.
tasks.withType<JavaExec>().configureEach {
    environment("_JAVA_AWT_WM_NONREPARENTING", "1")
}
