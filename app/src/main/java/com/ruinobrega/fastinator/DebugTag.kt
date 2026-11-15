package com.ruinobrega.fastinator

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Test tags are applied only in debug builds (where the Compose UI tests run),
 * so release APKs don't expose internal structure — including session IDs via
 * tags like "session_row_<id>" — through the semantics/a11y tree.
 */
fun Modifier.debugTag(tag: String): Modifier =
    if (BuildConfig.DEBUG) testTag(tag) else this
