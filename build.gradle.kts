plugins {
    `java-library`
    `signing`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "io.github.ericneid"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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

signing {
    // Sign with the local GnuPG installation (gpg-agent handles the
    // passphrase), instead of a secring file.
    useGpgCmd()
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
