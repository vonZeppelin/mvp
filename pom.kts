project("MVP") {

    id("org.lbogdanov.mvp:mvp:0.1:jar")

    properties {
        "kotlin.compiler.incremental" to true
        "project.build.sourceEncoding" to "UTF-8"
    }

    val mainClass = "mvp.AppKt"

    val javafxVersion = "14.0.1"
    val kotlinVersion = "1.3.72"

    dependencies {
        compile("org.openjfx:javafx-controls:$javafxVersion")
        compile("org.openjfx:javafx-fxml:$javafxVersion")
        compile("net.java.dev.jna:jna:5.5.0")
        compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
        compile("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    }

    build {
        plugins {
            plugin("org.apache.maven.plugins:maven-assembly-plugin:3.3.0") {
                configuration {
                    "descriptorRefs" {
                        "descriptorRef" to "jar-with-dependencies"
                    }
                    "archive" {
                        "manifest" {
                            "mainClass" to mainClass
                        }
                    }
                }
            }
            plugin("org.jetbrains.kotlin:kotlin-maven-plugin:$kotlinVersion") {
                configuration {
                    "jvmTarget" to 11
                }
                execution(goals = listOf("compile"))
            }
            plugin("org.openjfx:javafx-maven-plugin:0.0.4") {
                configuration {
                    "mainClass" to mainClass
                }
            }
        }

        sourceDirectory("src/main/kotlin")
    }
}
