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
        exclusiveContent {
            forRepository { maven("https://repo.codemc.io/repository/maven-public/") { name = "codemc" } }
            filter { includeGroup("fr.xephi") }
        }
        exclusiveContent {
            forRepository { maven("https://repo.viaversion.com/") { name = "viaversion" } }
            filter { includeGroup("com.viaversion") }
        }
        exclusiveContent {
            forRepository { maven("https://maven.maxhenkel.de/repository/public/") { name = "maxhenkel" } }
            filter { includeGroup("de.maxhenkel.voicechat") }
        }
        exclusiveContent {
            forRepository { maven("https://repo.opencollab.dev/main/") { name = "opencollab" } }
            filter { includeGroupAndSubgroups("org.geysermc") }
        }
    }
}

rootProject.name = "dbsdw-minigames"

include("travel", "core", "paintball", "chess", "bridge", "testgame", "valvloer", "verstoppertje")
