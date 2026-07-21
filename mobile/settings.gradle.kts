import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application", "com.android.library" ->
                    useModule("com.android.tools.build:gradle:8.5.2-fixed")
            }
        }
    }
    repositories {
        maven {
            url = uri("$rootDir/local-plugin-repo")
            metadataSources {
                mavenPom()
                artifact()
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("$rootDir/local-plugin-repo")
            metadataSources {
                mavenPom()
                artifact()
            }
        }
    }
}

rootProject.name = "sms-fraud-detector"
include(":app")
