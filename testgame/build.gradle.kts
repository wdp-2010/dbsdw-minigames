// Test-only game for MinigameCore's e2e checks. Never deploy MgTestGame.
plugins {
    id("dbsdw.paper-plugin")
}

dependencies {
    compileOnly(project(":core"))
    testImplementation(project(":core"))
}

tasks.jar {
    archiveBaseName = "MgTestGame"
}
