package co.median.android

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

class LocationServiceHelper(val activity: Activity) {
    private val defaultRequestLocationInterval = 1000L // 1 sec
    var callback: Callback? = null

    private val requestLocationPermissionsLauncher =
        (activity as ComponentActivity).registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineLocationGranted && coarseLocationGranted) {
                promptLocationService()
            } else {
                callback?.onResult(false)
            }
        }

    private val requestEnableLocationLauncher =
        (activity as ComponentActivity).registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                callback?.onResult(true)
            } else {
                callback?.onResult(false)
            }
        }

    fun promptLocationService(callback: Callback) {
        this.callback = callback
        promptLocationService()
    }

    fun promptLocationService() {
        // check location permissions
        if (!isLocationPermissionGranted()) {

            // shows rationale message if needed
            showRequestPermissionRationale()

            // prompt permission request
            requestLocationPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        // request location service
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, defaultRequestLocationInterval)
            .build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client = LocationServices.getSettingsClient(activity)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // already enabled
            callback?.onResult(true)
        }

        task.addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                // prompt user to enable location service
                val intentSenderRequest = IntentSenderRequest.Builder(e.resolution).build()
                requestEnableLocationLauncher.launch(intentSenderRequest)
            }
        }
    }

    fun isLocationServiceEnabled(): Boolean {
        if (!isLocationPermissionGranted()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lm: LocationManager = activity.getSystemService(LocationManager::class.java)
            return lm.isLocationEnabled
        } else {
            // This is Deprecated in API 28
            val mode = Settings.Secure.getInt(
                activity.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            return (mode != Settings.Secure.LOCATION_MODE_OFF)
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        val checkFine =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        val checkCoarse =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        return checkFine == PackageManager.PERMISSION_GRANTED && checkCoarse == PackageManager.PERMISSION_GRANTED
    }

    private fun showRequestPermissionRationale() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            || ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            Toast.makeText(
                activity,
                R.string.request_permission_explanation_geolocation,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    interface Callback {
        fun onResult(enabled: Boolean)
    }
}