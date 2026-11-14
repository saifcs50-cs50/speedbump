package com.speedbump.core.data.di

import javax.inject.Qualifier

/**
 * A CoroutineScope that lives as long as the process.
 *
 * Used for state that must stay hot regardless of whether any UI is bound -
 * the armed set in particular, which the accessibility service reads on a
 * latency budget and cannot afford to have cold-start behind a
 * WhileSubscribed timeout.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
