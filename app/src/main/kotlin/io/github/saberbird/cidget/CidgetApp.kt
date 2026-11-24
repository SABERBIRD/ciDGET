package io.github.saberbird.cidget

import android.app.Application
import io.github.saberbird.cidget.di.AppGraph

/** Creates the single [AppGraph] (manual DI, D5). */
class CidgetApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** The app's graph, from any [android.content.Context]. */
val android.content.Context.graph: AppGraph
    get() = (applicationContext as CidgetApp).graph
