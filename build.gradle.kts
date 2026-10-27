import com.android.build.api.dsl.LibraryExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Library modules publish their release variant as com.github.re-ovo.bettertext:<module>, the
// coordinates JitPack serves them under; :bom publishes itself. JitPack sets VERSION to the tag or
// commit it builds.
subprojects {
    group = "com.github.re-ovo.bettertext"
    version = System.getenv("VERSION") ?: "dev"

    pluginManager.withPlugin("com.android.library") {
        apply(plugin = "maven-publish")

        extensions.configure<LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        from(components["release"])
                    }
                }
            }
        }
    }
}
