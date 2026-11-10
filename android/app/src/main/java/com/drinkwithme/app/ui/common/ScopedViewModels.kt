package com.drinkwithme.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Eigene ViewModel-Speicher für kurzlebige Bildschirme (z. B. je geöffnetem Ereignis). Liegt selbst im Speicher der
 * Activity und übersteht so Drehungen; [retainOnly] leert die Speicher geschlossener Bildschirme (beendet deren
 * ViewModels), statt sie bis zum Ende der Activity anzusammeln.
 */
class ScopedViewModelStores : ViewModel() {
    private val stores = mutableMapOf<Long, ViewModelStore>()

    fun storeFor(key: Long): ViewModelStore = stores.getOrPut(key) { ViewModelStore() }

    /** Alle Speicher außer dem für [key] leeren (`null` = alle). */
    fun retainOnly(key: Long?) {
        stores.keys.filter { it != key }.forEach { stores.remove(it)?.clear() }
    }

    override fun onCleared() = retainOnly(null)
}

/** ViewModels in [content] leben im Speicher [key] von [stores] statt im Speicher der Activity. */
@Composable
fun ScopedViewModels(stores: ScopedViewModelStores, key: Long, content: @Composable () -> Unit) {
    val owner = remember(stores, key) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = stores.storeFor(key)
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
