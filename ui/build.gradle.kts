import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
}

android {
    namespace = "fm.apakabar.lettergrid.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
}

dokka {
    moduleName.set("lettergrid-kotlin-ui")
}

mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        ),
    )
    coordinates("fm.apakabar", "lettergrid-kotlin-ui", version.toString())
    pom {
        name.set("LetterGrid UI for Kotlin")
        description.set("The Compose board and hidden word list of LetterGrid.")
    }
}
