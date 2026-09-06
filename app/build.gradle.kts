plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(libs.guava)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--enable-preview")
}

application {
    mainClass = "cli.App"
    applicationDefaultJvmArgs = listOf(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.named<JavaExec>("run") {
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes("Main-Class" to "cli.App")
    }
}

// OSネイティブ実行可能バイナリ (.exe) パッケージングタスク (必要に応じて使用)
tasks.register<Exec>("packageExe") {
    dependsOn(tasks.named("installDist"))
    group = "distribution"
    description = "Packages the application into a standalone Windows .exe with bundled runtime."

    val javaHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile.absolutePath
    val jpackageExe = "$javaHome/bin/jpackage.exe"
    val outputDir = layout.projectDirectory.dir("dist").asFile

    doFirst {
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
    }

    commandLine(
        jpackageExe,
        "--type", "app-image",
        "--name", "xsaw",
        "--input", layout.buildDirectory.dir("install/app/lib").get().asFile.absolutePath,
        "--main-jar", "app.jar",
        "--main-class", "cli.App",
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--win-console",
        "--dest", outputDir.absolutePath
    )
}

// OSネイティブ実行可能バイナリ パッケージングタスク (Windows & Linux 両対応)
tasks.register<Exec>("packageNative") {
    dependsOn(tasks.named("installDist"))
    group = "distribution"
    description = "Packages the application into a standalone OS-native binary."

    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
    val javaHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile.absolutePath
    val jpackageExe = if (isWindows) "$javaHome/bin/jpackage.exe" else "$javaHome/bin/jpackage"
    val outputDir = layout.projectDirectory.dir("dist").asFile

    doFirst {
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
    }

    val argsList = mutableListOf(
        jpackageExe,
        "--type", "app-image",
        "--name", "xsaw",
        "--input", layout.buildDirectory.dir("install/app/lib").get().asFile.absolutePath,
        "--main-jar", "app.jar",
        "--main-class", "cli.App",
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--dest", outputDir.absolutePath
    )

    if (isWindows) {
        argsList.add("--win-console")
    }

    commandLine(argsList)
}