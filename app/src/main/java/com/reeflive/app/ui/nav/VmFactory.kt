package com.reeflive.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reeflive.app.appGraph
import com.reeflive.app.di.AppGraph

/**
 * `viewModel()` with a factory built from the [AppGraph] (ARCHITECTURE §5.1). The ViewModel is scoped to
 * the current NavBackStackEntry; [key] distinguishes instances (e.g. per parameter id).
 */
@Composable
inline fun <reified VM : ViewModel> reefViewModel(key: String? = null, crossinline create: (AppGraph) -> VM): VM {
    val graph = LocalContext.current.appGraph
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(graph) } },
    )
}
