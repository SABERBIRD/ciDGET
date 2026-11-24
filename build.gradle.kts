// Plugins are declared once here (with versions) so each Gradle plugin loads a single time;
// the modules apply them without versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
