package io.nlopez.smartlocation.sample

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import io.nlopez.smartlocation.OnActivityUpdatedListener
import io.nlopez.smartlocation.OnGeofencingTransitionListener
import io.nlopez.smartlocation.OnLocationUpdatedListener
import io.nlopez.smartlocation.SmartLocation
import io.nlopez.smartlocation.geofencing.model.GeofenceModel
import io.nlopez.smartlocation.geofencing.utils.TransitionGeofence
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesProvider
import java.util.*

class MainActivity : Activity(), OnLocationUpdatedListener, OnActivityUpdatedListener, OnGeofencingTransitionListener {

    private var locationText: TextView? = null
    private var activityText: TextView? = null
    private var geofenceText: TextView? = null

    private var provider: LocationGooglePlayServicesProvider? = null

    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.activity_main)

        // Bind event clicks
        val startLocation = findViewById<View>(R.id.start_location) as Button
        startLocation.setOnClickListener(View.OnClickListener {
            // Location permission not granted
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_ID)
                return@OnClickListener
            }
            startLocation()
        })

        val stopLocation = findViewById<View>(R.id.stop_location) as Button
        stopLocation.setOnClickListener { stopLocation() }

        // bind textviews
        locationText = findViewById<View>(R.id.location_text) as TextView
        activityText = findViewById<View>(R.id.activity_text) as TextView
        geofenceText = findViewById<View>(R.id.geofence_text) as TextView

        // Keep the screen always on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        showLast()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == LOCATION_PERMISSION_ID && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocation()
        }
    }

    private fun showLast() {
        val lastLocation = SmartLocation.with(this).location().lastLocation
        if (lastLocation != null) {
            locationText!!.text = String.format("[From Cache] Latitude %.6f, Longitude %.6f",
                    lastLocation.latitude,
                    lastLocation.longitude)
        }

        val detectedActivity = SmartLocation.with(this).activity().lastActivity
        if (detectedActivity != null) {
            activityText!!.text = String.format("[From Cache] Activity %s with %d%% confidence",
                    getNameFromType(detectedActivity),
                    detectedActivity.confidence)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (provider != null) {
            provider!!.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun startLocation() {

        provider = LocationGooglePlayServicesProvider()
        provider!!.setCheckLocationSettings(true)

        val smartLocation = SmartLocation.Builder(this).logging(true).build()

        smartLocation.location(provider).start(this)
        smartLocation.activity().start(this)

        // Create some geofences
        val mestalla = GeofenceModel.Builder("1").setTransition(Geofence.GEOFENCE_TRANSITION_ENTER).setLatitude(39.47453120000001).setLongitude(-0.358065799999963).setRadius(500f).build()
        smartLocation.geofencing().add(mestalla).start(this)
    }

    private fun stopLocation() {
        SmartLocation.with(this).location().stop()
        locationText!!.text = "Location stopped!"

        SmartLocation.with(this).activity().stop()
        activityText!!.text = "Activity Recognition stopped!"

        SmartLocation.with(this).geofencing().stop()
        geofenceText!!.text = "Geofencing stopped!"
    }

    private fun showLocation(location: Location?) {
        if (location != null) {
            val text = String.format("Latitude %.6f, Longitude %.6f",
                    location.latitude,
                    location.longitude)
            locationText!!.text = text

            // We are going to get the address for the current position
            SmartLocation.with(this).geocoding().reverse(location) { original, results ->
                if (results.size > 0) {
                    val result = results[0]
                    val builder = StringBuilder(text)
                    builder.append("\n[Reverse Geocoding] ")
                    val addressElements = ArrayList<String>()
                    for (i in 0..result.maxAddressLineIndex) {
                        addressElements.add(result.getAddressLine(i))
                    }
                    builder.append(TextUtils.join(", ", addressElements))
                    locationText!!.text = builder.toString()
                }
            }
        } else {
            locationText!!.text = "Null location"
        }
    }

    private fun showActivity(detectedActivity: DetectedActivity?) {
        if (detectedActivity != null) {
            activityText!!.text = String.format("Activity %s with %d%% confidence",
                    getNameFromType(detectedActivity),
                    detectedActivity.confidence)
        } else {
            activityText!!.text = "Null activity"
        }
    }

    private fun showGeofence(geofence: Geofence?, transitionType: Int) {
        if (geofence != null) {
            geofenceText!!.text = "Transition " + getTransitionNameFromType(transitionType) + " for Geofence with id = " + geofence.requestId
        } else {
            geofenceText!!.text = "Null geofence"
        }
    }

    override fun onLocationUpdated(location: Location) {
        showLocation(location)
    }

    override fun onActivityUpdated(detectedActivity: DetectedActivity) {
        showActivity(detectedActivity)
    }

    override fun onGeofenceTransition(geofence: TransitionGeofence) {
        showGeofence(geofence.geofenceModel.toGeofence(), geofence.transitionType)
    }

    private fun getNameFromType(activityType: DetectedActivity): String {
        when (activityType.type) {
            DetectedActivity.IN_VEHICLE -> return "in_vehicle"
            DetectedActivity.ON_BICYCLE -> return "on_bicycle"
            DetectedActivity.ON_FOOT -> return "on_foot"
            DetectedActivity.STILL -> return "still"
            DetectedActivity.TILTING -> return "tilting"
            else -> return "unknown"
        }
    }

    private fun getTransitionNameFromType(transitionType: Int): String {
        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> return "enter"
            Geofence.GEOFENCE_TRANSITION_EXIT -> return "exit"
            else -> return "dwell"
        }
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {

    }

    override fun onStart() {
        super.onStart()

        // Location permission not granted
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_ID)
            return
        }

        if (!isFinishing && !isDestroyed) {
            LocationUpdatesHelper.fetchLocation(this)
        }
    }

    override fun onStop() {
        super.onStop()

        //Stop Location Updates
        LocationUpdatesHelper.stopLocationUpdates(this)
    }


    companion object {

        private val LOCATION_PERMISSION_ID = 1001
    }
}
