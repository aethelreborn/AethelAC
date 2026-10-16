/**
 *          AethelAC Build Configuration
 *
 * Build Flags:
 * -PshadePE=true   - Enables 'lite' mode
 * -Prelocate=false - Adds 'no_relocate' modifier
 * -Prelease=true   - Removes commit/modifiers for release build
 *
 * Logic in: buildSrc/versioning/BuildConfig.kt & VersionUtil.kt
 */

import versioning.BuildConfig
import versioning.VersionUtil

BuildConfig.init(project)

val baseVersion = System.getenv("AETHEL_VERSION") ?: "1.0.0"
group = "ac.aethel.aethelac"
version = VersionUtil.computeVersion(project, baseVersion)
description = "Asynchronous prediction-based anticheat for Minecraft 1.8–26.3, built on PacketEvents 2.0."

ext["timestamp"] = System.currentTimeMillis().toString()
ext["git_branch"] = VersionUtil.getGitBranch(project, true)
ext["git_commit"] = VersionUtil.getGitCommitHash(project, true)
ext["git_org"] = System.getenv("AETHEL_GIT_ORG") ?: VersionUtil.getGitUser(project)
ext["git_repo"] = System.getenv("AETHEL_GIT_REPO") ?: "AethelAC"

println("Build configuration:")
println("    shadePE            = ${BuildConfig.shadePE}")
println("    relocate           = ${BuildConfig.relocate}")
println("    mavenLocalOverride = ${BuildConfig.mavenLocalOverride}")
println("    release            = ${BuildConfig.release}")
println("    version            = $version")

tasks.register("printVersion") {
    group = "versioning"
    description = "Prints the computed project version"
    doLast {
        println("VERSION=$version")
    }
}

// ---------- Java Compile Optimization ----------
subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.isFork = true
        options.isIncremental = true
    }
}
