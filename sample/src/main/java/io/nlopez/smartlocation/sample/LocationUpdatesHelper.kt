package io.nlopez.smartlocation.sample

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.location.Location
import android.os.CountDownTimer
import android.util.Log
import com.google.android.gms.location.DetectedActivity
import io.nlopez.smartlocation.SmartLocation
import io.nlopez.smartlocation.location.config.LocationAccuracy
import io.nlopez.smartlocation.location.config.LocationParams
import io.nlopez.smartlocation.location.providers.LocationBasedOnActivityProvider
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesProvider
import io.nlopez.smartlocation.rx.ObservableFactory
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import java.util.concurrent.TimeUnit

object LocationUpdatesHelper : LocationBasedOnActivityProvider.LocationBasedOnActivityListener {

    private val TAG = "LocationUpdatesHelper"

    override fun locationParamsForActivity(detectedActivity: DetectedActivity?): LocationParams {
        if (detectedActivity != null) {
            val detected = handleDetectedActivities(detectedActivity)
            Log.d(TAG, "Activity: $detected")
        }
        return LocationParams.BEST_EFFORT
    }

    //Location Updates
    private var fusedDisposable: Disposable? = null
    private var activityDisposable: Disposable? = null
    private var locationObservableFused: Observable<Location>? = null
    private var locationProblemCount = 0

    /**
     * This wil start location update subscription.
     */
    fun fetchLocation(activity: Activity) {

        if (!activity.isFinishing && !activity.isDestroyed) {
            startLocationUpdates(activity)
            startActivityUpdates(activity)
        }
    }

    private fun startLocationUpdates(context: Context) {
        //Log.d(TAG, "startLocationUpdates", "Location updates started!", LogLevel.WARNING)

        //Set Location Providers
        locationObservableFused = ObservableFactory.from(SmartLocation.with(context)
                .location(LocationGooglePlayServicesProvider())
                .continuous()
                .config(LocationParams.Builder().setAccuracy(LocationAccuracy.MEDIUM).setDistance(0.0f).setInterval(5000L).build()))

        subscribeToFusedLocationUpdates(context)
    }

    private fun startActivityUpdates(activity: Activity) {
        //Set up user's Activity listener
        activityDisposable = ObservableFactory.from(SmartLocation.with(activity).activity())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onNext = { detectedActivity ->
                            try {
                                if (detectedActivity != null) {
                                    val detected = handleDetectedActivities(detectedActivity)
                                    Log.d(TAG, detected)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onError = {
                            it.printStackTrace()
                        },
                        onComplete = { }
                )
    }

    private fun subscribeToFusedLocationUpdates(context: Context) {

        fusedDisposable = locationObservableFused!!
                .timeout(15, TimeUnit.SECONDS) {

                    locationProblemCount += 1
                    subscribeToLegacyLocationUpdates(context)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onNext = {
                            Log.d(TAG, "Location: ${it}")
                        },
                        onError = {
                            it.printStackTrace()
                        },
                        onComplete = { }
                )
    }

    private fun subscribeToLegacyLocationUpdates(context: Context) {

        if (fusedDisposable != null)
            fusedDisposable!!.dispose()

        GeoLocationService.start(context)

        if (context is Activity) {
            context.runOnUiThread {

                //Run for 1 Minute, then switch back to fused provider
                object : CountDownTimer(60000, 1000) {
                    override fun onTick(l: Long) {

                    }

                    override fun onFinish() {

                        //Only Switch back If problem with Fused locations have occurred less than 5 times
                        //if (locationProblemCount < 5) {
                        GeoLocationService.stop(context)

                        subscribeToFusedLocationUpdates(context)
                        //}
                    }
                }.start()
            }
        }
    }

    /**
     * This will stop location updates.
     */
    fun stopLocationUpdates(activity: Activity) {
        try {

            SmartLocation.with(activity).location().stop()
            SmartLocation.with(activity).activity().stop()

            //Stop Location Listeners
            if (fusedDisposable != null)
                fusedDisposable!!.dispose()

            if (activityDisposable != null)
                activityDisposable!!.dispose()

            //Stop Service if running
            GeoLocationService.stop(activity)
            Log.d(TAG, "Location services are stopped!")

        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }


    /**
     * This will provide an Observable listener for listening to location updates.
     */
    fun locationListener(): Observable<Location>? {
        return locationObservableFused
    }

    /**
     * Check if location services are running.
     */
    fun areLocationServicesRunning(context: Context): Boolean {

        if (fusedDisposable == null || fusedDisposable?.isDisposed!!) {
            return true
        }

        if (isMyServiceRunning(GeoLocationService::class.java, context)) {
            return true
        }

        return false
    }

    /**
     * This will start location updates if not already running.
     */
    fun startLocationServicesIfNotActive(context: Context) {
        if (!areLocationServicesRunning(context)) {
            startLocationUpdates(context)
        }
    }

    fun handleDetectedActivities(activity: DetectedActivity): String {
        var detectedActivity = "Unknown"

        when (activity.type) {
            DetectedActivity.IN_VEHICLE -> {
                detectedActivity = "In Vehicle, " + activity.confidence
            }
            DetectedActivity.ON_BICYCLE -> {
                detectedActivity = "On Bicycle, " + activity.confidence
            }
            DetectedActivity.ON_FOOT -> {
                detectedActivity = "On Foot, " + activity.confidence
            }
            DetectedActivity.RUNNING -> {
                detectedActivity = "Running, " + activity.confidence
            }
            DetectedActivity.STILL -> {
                detectedActivity = "Still, " + activity.confidence
            }
            DetectedActivity.TILTING -> {
                detectedActivity = "Tilting, " + activity.confidence
            }
            DetectedActivity.WALKING -> {
                detectedActivity = "Walking, " + activity.confidence
            }
            DetectedActivity.UNKNOWN -> {
                detectedActivity = "Unknown, " + activity.confidence
            }
        }

        return detectedActivity
    }

    fun isMyServiceRunning(serviceClass: Class<*>, context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager?
        manager?.let {
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
        }
        return false
    }
}