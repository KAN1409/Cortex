package com.kareem.cortex.prime

import android.app.Application

class CortexPrimeApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.kickJobs() // resume anything left from a previous run (expired leases are reclaimed)
    }
}

val android.content.Context.graph: AppGraph get() = (applicationContext as CortexPrimeApp).graph
