package com.tamerslab.diagnostics

import android.os.Trace

/**
 * atrace helpers for startup attribution.
 *
 * Sections are ordinary sync slices, visible in Perfetto and readable by the
 * macrobenchmark `TraceSectionMetric`, e.g. `startup:koin`, `startup:app-locale`,
 * `startup:activity-oncreate`, `startup:set-content`, `startup:deferred-init-dispatch`.
 * They are intentionally cheap and always enabled; the platform no-ops when tracing is
 * off.
 */
object StartupTrace {
    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }
}
