// Aligns the versions of the library modules, so apps can declare the version once.
plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints {
        api(project(":latex"))
        api(project(":text"))
        api(project(":markdown"))
        api(project(":highlight"))
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["javaPlatform"])
        }
    }
}
