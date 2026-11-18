// Shared setup for every DBSDW Paper plugin module: the Java conventions, the Paper API,
// and ${version} expansion in plugin.yml.

plugins {
    id("dbsdw.java-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    compileOnly(lib("paper-api"))
    testImplementation(lib("paper-api"))
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}
