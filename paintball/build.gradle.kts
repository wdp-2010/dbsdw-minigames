plugins {
    id("dbsdw.paper-plugin")
}

dependencies {
    compileOnly(project(":core"))
    testImplementation(project(":core"))
}

tasks.jar {
    archiveBaseName = "Paintball"
}
