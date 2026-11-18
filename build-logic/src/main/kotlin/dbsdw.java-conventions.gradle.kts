// Shared setup for every DBSDW Java module: Java 25, strict compiler warnings, JUnit 5.

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
    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    // -classfile: Paper's JOML jar (display transformations) carries Java 8 type annotations in
    // Java 1.2-format class files, which javac reports as a warning about the library, not our code.
    options.compilerArgs.addAll(listOf("-Xlint:all,-classfile", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
