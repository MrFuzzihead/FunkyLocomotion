import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

// Test-only dedicated-server harness. It is never packaged into the mod jar;
// `./gradlew runServer -PflHarness=true` puts this source set on the server
// classpath and boots the server with the harness mod enabled.
//
// This file is applied before the GTNH convention (and therefore before the
// Java plugin), so the source set and run task are configured lazily.
val runHarness = providers.gradleProperty("flHarness").isPresent

if (runHarness) {
    val dir = layout.projectDirectory.dir("run/server").asFile
    dir.mkdirs()

    val eula = dir.resolve("eula.txt")
    if (!eula.exists()) eula.writeText("eula=true\n")

    val properties = dir.resolve("server.properties")
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

pluginManager.withPlugin("java") {
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val mainSourceSet = sourceSets.getByName("main")

    val harnessSourceSet = sourceSets.create("harness") {
        java.srcDir("src/harness/java")
        resources.srcDir("src/harness/resources")
        compileClasspath += mainSourceSet.output
        compileClasspath += mainSourceSet.compileClasspath.filter { !it.name.startsWith("jabel-javac-plugin") }
        runtimeClasspath += output + mainSourceSet.output + mainSourceSet.runtimeClasspath
    }

    tasks.matching { it.name == "runServer" }.configureEach {
        if (this is JavaExec && runHarness) {
            dependsOn(harnessSourceSet.classesTaskName)
            classpath(harnessSourceSet.output)
            systemProperty("fl.harness", "true")
        }
    }
}
