plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.dokka)
}

val released =
    Regex("""^## (\d+\.\d+\.\d+)$""", RegexOption.MULTILINE)
        .find(file("CHANGELOG.md").readText())
        ?.groupValues
        ?.get(1)

allprojects {
    group = "fm.apakabar"
    version = released ?: "unreleased"
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(
                automaticRelease = true,
                validateDeployment = com.vanniktech.maven.publish.DeploymentValidation.PUBLISHED,
            )
            if (!providers.gradleProperty("unsignedLocalPublish").isPresent) {
                signAllPublications()
            }
            pom {
                url.set("https://github.com/apakabarlabs/lettergrid-kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("apakabarlabs")
                        name.set("Apakabar")
                        url.set("https://github.com/apakabarlabs")
                    }
                }
                scm {
                    url.set("https://github.com/apakabarlabs/lettergrid-kotlin")
                    connection.set("scm:git:https://github.com/apakabarlabs/lettergrid-kotlin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/apakabarlabs/lettergrid-kotlin.git")
                }
            }
        }
    }
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set("1.7.1")
            outputToConsole.set(true)
            ignoreFailures.set(false)
            filter {
                exclude("**/generated/**")
            }
        }
    }
}

dependencies {
    dokka(project(":core"))
    dokka(project(":ui"))
}

dokka {
    moduleName.set("LetterGrid for Kotlin")
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
}
