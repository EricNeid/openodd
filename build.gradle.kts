plugins {
    `java-library`
    `signing`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

import org.gradle.external.javadoc.StandardJavadocDocletOptions

group = "io.github.ericneid"
version = "0.2.1"

// The Java version used to compile and run the build. CI raises this
// through the toolchainJavaVersion property to test against newer JDKs.
val toolchainJavaVersion = (findProperty("toolchainJavaVersion") as String?)?.toInt() ?: 21

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(toolchainJavaVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.yaml:snakeyaml:2.7")
    api("com.google.code.gson:gson:2.15.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.javadoc {
    // Doclint runs by default; treat its warnings as errors so that
    // incomplete Javadoc breaks the build. The value of the string
    // option is ignored by javadoc and only placates the option writer.
    (options as StandardJavadocDocletOptions).addStringOption("Xwerror", "-quiet")
}

// The build lifecycle does not run javadoc; hook it into check so that
// CI reports Javadoc problems.
tasks.check {
    dependsOn(tasks.javadoc)
}

signing {
    // Sign with the local GnuPG installation (gpg-agent handles the
    // passphrase). CI skips this and injects the key through the
    // plugin's in-memory signing properties instead.
    if (!project.hasProperty("signingInMemoryKey")) {
        useGpgCmd()
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("openodd")
        description.set("Java implementation of the ASAM OpenODD 1.0.0 standard: "
                + "loading and validating ODD specifications, parsing COD/OD tables "
                + "and evaluating records against an ODD.")
        inceptionYear.set("2026")
        url.set("https://github.com/EricNeid/openodd")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("EricNeid")
                name.set("Eric Neidhardt")
                email.set("eric.neidhardt@gmail.com")
                url.set("https://github.com/EricNeid")
            }
        }
        scm {
            url.set("https://github.com/EricNeid/openodd")
            connection.set("scm:git:git://github.com/EricNeid/openodd.git")
            developerConnection.set("scm:git:ssh://git@github.com/EricNeid/openodd.git")
        }
    }
}
