// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.vksp) apply false
    alias(libs.plugins.vprotobuf) apply false
}

// NOTE: detekt/ktlint were scaffolded in Phase 7.1 but the aggregate quality run
// could not be completed reliably (it stalled while analysing the full source
// tree under the constrained build memory settings). The plugins were removed
// so the regular build stays predictable; see docs/refactor/PHASE7_DECISIONS.md
// for the follow-up plan. Lint (:app:lint) remains the enforced static-analysis
// gate and CI runs it as a blocking step.
