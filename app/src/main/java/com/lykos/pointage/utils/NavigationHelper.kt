package com.lykos.pointage.utils

import android.content.Context
import android.content.Intent

object NavigationHelper {

    fun navigateTo(context: Context, activityClassName: String) {
        try {
            val targetClass = Class.forName("com.lykos.pointage.ui.view.$activityClassName")
            val intent = Intent(context, targetClass)
            context.startActivity(intent)
        } catch (_: ClassNotFoundException) {
            // Handle error - activity not found
        }
    }
}