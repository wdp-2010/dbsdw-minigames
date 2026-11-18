plugins {
    id("dbsdw.paper-plugin")
}

dependencies {
    "bundle"(project(":travel"))
    compileOnly(libs.authme.core) { isTransitive = false }
    compileOnly(libs.viaversion.api)
    compileOnly(libs.floodgate.api)
}

tasks.jar {
    archiveBaseName = "DbsdwBridge"
}
