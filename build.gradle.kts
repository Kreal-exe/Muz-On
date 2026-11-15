plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

// Builds can live outside the project, e.g. when it sits in a folder iCloud syncs: iCloud copies
// files it sees changing ("Foo 2.class"), and the build then finds every class twice. Set
// muzon.buildDir in ~/.gradle/gradle.properties to move them.
findProperty("muzon.buildDir")?.toString()?.let { dir ->
    allprojects { layout.buildDirectory.set(file("$dir/${project.name}")) }
}
