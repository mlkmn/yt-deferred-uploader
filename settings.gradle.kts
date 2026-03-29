pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.owasp.dependencycheck") {
                useModule("org.owasp:dependency-check-gradle:${requested.version}")
            }
        }
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "yt-deferred-uploader"
