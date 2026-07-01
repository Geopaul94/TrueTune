// Top-level build file. Plugins are declared here with `apply false` so the
// versions resolve once for the whole project; each module opts in via `alias`.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
