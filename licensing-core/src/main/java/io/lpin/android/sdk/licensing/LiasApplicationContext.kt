package io.lpin.android.sdk.licensing

import android.app.Application
import android.content.Context

object LiasApplicationContext {
    @JvmStatic
    fun requireApplicationContext(): Context {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val method = activityThreadClass.getMethod("currentApplication")
            val application = method.invoke(null) as? Application
                ?: throw LiasLicenseException("Unable to resolve current Application for license verification")
            application.applicationContext
        } catch (exception: LiasLicenseException) {
            throw exception
        } catch (exception: Exception) {
            throw LiasLicenseException("Unable to resolve current Application for license verification", exception)
        }
    }
}
