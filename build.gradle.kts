import org.gradle.api.tasks.JavaExec

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Test-only dedicated-server harness. It is never packaged into the mod jar;
// `./gradlew runServer -PflHarness=true` puts this source set on the server
// classpath and boots the server with the harness mod enabled.
val harnessSourceSet = sourceSets.create("harness") {
    java.srcDir("src/harness/java")
    resources.srcDir("src/harness/resources")
    compileClasspath += sourceSets["main"].output
    compileClasspath += sourceSets["main"].compileClasspath.filter { !it.name.startsWith("jabel-javac-plugin") }
    runtimeClasspath += output + sourceSets["main"].output + sourceSets["main"].runtimeClasspath
}

val serverHarnessDirectory = layout.projectDirectory.dir("run/server").asFile

val prepareServerHarness = tasks.register("prepareServerHarness") {
    notCompatibleWithConfigurationCache("Writes the harness server.properties and eula.txt")
    outputs.upToDateWhen { false }
    doLast {
        serverHarnessDirectory.mkdirs()

        val eula = serverHarnessDirectory.resolve("eula.txt")
        if (!eula.exists()) eula.writeText("eula=true\n")

        val properties = serverHarnessDirectory.resolve("server.properties")
        if (!properties.exists()) {
            properties.writeText(
                """
                online-mode=false
                level-name=harness
                level-type=FLAT
                generate-structures=false
                spawn-protection=0
                max-tick-time=-1
                view-distance=4
                difficulty=0
                gamemode=1
                allow-nether=false
                motd=Funky Locomotion harness
                """.trimIndent() + "\n"
            )
        }
    }
}

tasks.named<JavaExec>("runServer") {
    if (providers.gradleProperty("flHarness").isPresent) {
        dependsOn(prepareServerHarness, harnessSourceSet.classesTaskName)
        classpath(harnessSourceSet.output)
        systemProperty("fl.harness", "true")
    }
}
