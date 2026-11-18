plugins {
    id("dbsdw.paper-plugin")
}

tasks.jar {
    archiveBaseName = "MinigameCore"
}

dependencies {
    "bundle"(project(":travel"))
}
