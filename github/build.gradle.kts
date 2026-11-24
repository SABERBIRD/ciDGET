plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":core"))
    testImplementation(libs.kotlin.test)
}
