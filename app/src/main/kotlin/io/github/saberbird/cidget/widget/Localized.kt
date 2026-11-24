package io.github.saberbird.cidget.widget

import android.content.Context
import io.github.saberbird.cidget.core.Language
import java.util.Locale

/**
 * A context whose resources use the widget's own language (D28), whatever the system language is. The widget
 * and the wide terminal read every string from it.
 */
internal fun Context.withLanguage(language: Language): Context {
    val config = android.content.res.Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(language.tag))
    return createConfigurationContext(config)
}
