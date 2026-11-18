pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    // Repositories are declared here only; a module declaring its own fails the build.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}

rootProject.name = "dbsdw-minigames"

include("travel", "core", "paintball")
