package com.tamerslab

interface ActivityLifecycleListener {
    fun onPause()
    fun onResume()

    companion object {
        fun noOpInstance(): ActivityLifecycleListener {
            return object: ActivityLifecycleListener {
                override fun onPause() {}

                override fun onResume() {}
            }
        }
    }
}