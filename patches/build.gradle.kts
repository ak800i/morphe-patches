group = "app.ak800i"

patches {
    about {
        name = "ak800i Waze patches"
        description = "Waze: import and export custom voices as zipped MP3 voice packs."
        source = "git@github.com:ak800i/waze-morphe-patches.git"
        author = "ak800i"
        contact = "https://github.com/ak800i"
        website = "https://github.com/ak800i/waze-morphe-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
