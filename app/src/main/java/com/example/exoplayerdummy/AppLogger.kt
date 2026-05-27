package com.example.exoplayerdummy

import android.util.Log

object AppLogger {
    private const val GLOBAL_TAG = "StreamPlayerApp"

    fun v(component: String, message: String) {
        Log.v(GLOBAL_TAG, format(component, message))
    }

    fun d(component: String, message: String) {
        Log.d(GLOBAL_TAG, format(component, message))
    }

    fun i(component: String, message: String) {
        Log.i(GLOBAL_TAG, format(component, message))
    }

    fun w(component: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(GLOBAL_TAG, format(component, message))
        } else {
            Log.w(GLOBAL_TAG, format(component, message), throwable)
        }
    }

    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(GLOBAL_TAG, format(component, message))
        } else {
            Log.e(GLOBAL_TAG, format(component, message), throwable)
        }
    }

    private fun format(component: String, message: String): String = "[$component] $message"
}
