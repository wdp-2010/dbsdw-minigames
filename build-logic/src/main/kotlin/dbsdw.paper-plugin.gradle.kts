// Shared setup for every DBSDW Paper plugin module: Java 25, Paper API, JUnit 5,
// strict compiler warnings, and ${version} expansion in plugin.yml.

plugins {
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

group = "be.dbsdw.minigames"
version = "0.1.0-SNAPSHOT"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    compileOnly(lib("paper-api"))
    testImplementation(lib("paper-api"))
    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}
