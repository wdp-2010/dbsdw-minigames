// Shared setup for every DBSDW Paper plugin module: the Java conventions, the Paper API,
// ${version} expansion in plugin.yml, and a `bundle` configuration whose jars are merged
// into the plugin jar (for our own plain-Java modules such as :travel).

plugins {
    id("dbsdw.java-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

val bundle = configurations.create("bundle") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
configurations.named("implementation") { extendsFrom(bundle) }

dependencies {
    compileOnly(lib("paper-api"))
    testImplementation(lib("paper-api"))
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

// The bundled jars are unpacked with an injected ArchiveOperations held in locals: a lambda that
// touches a script-level property or method captures the script object, which the configuration
// cache cannot store.
interface ArchiveServices {
    @get:Inject
    val archives: ArchiveOperations
}

tasks.jar {
    val archives = objects.newInstance<ArchiveServices>().archives
    val bundledJars = bundle.elements
    from(bundledJars.map { jars -> jars.map { archives.zipTree(it.asFile) } }) {
        exclude("META-INF/**")
    }
}
