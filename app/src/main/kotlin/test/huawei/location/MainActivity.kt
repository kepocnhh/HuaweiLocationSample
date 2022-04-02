package test.huawei.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.common.GoogleApiAvailability
import com.huawei.hms.api.HuaweiApiAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    companion object {
        private val scope = CoroutineScope(Dispatchers.Main + Job())
    }

    private var textViews: Map<LocationProvider, TextView>? = null
    private var button: View? = null

    private fun getCoordinate() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationManager.allProviders.any {
            locationManager.isProviderEnabled(it)
        }
        if (!isLocationEnabled) {
            showToast("Location is not enabled!")
            return
        }
        requireNotNull(button).isEnabled = false
        val finished = mutableMapOf<LocationProvider, Boolean>()
        fun finish(type: LocationProvider) {
            finished[type] = true
            val end = LocationProvider.values().all {
                finished[it] == true
            }
            if (end) {
                requireNotNull(button).isEnabled = true
            }
        }
        val time = System.currentTimeMillis()
        LocationProvider.values().forEach { type ->
            when (type) {
                LocationProvider.HUAWEI -> {
                    val textView = requireNotNull(textViews)[type]!!
                    val api = HuaweiApiAvailability.getInstance()
                    if (api.isHuaweiMobileServicesAvailable(this) == com.huawei.hms.api.ConnectionResult.SUCCESS) {
                        val provider: CoordinateProvider = HuaweiCoordinateProvider(this)
                        scope.launch {
                            try {
                                textView.text = "${type.name} loading..."
                                val coordinate = withContext(Dispatchers.IO) {
                                    provider.getCoordinate()
                                }
                                val dTime = System.currentTimeMillis() - time
                                textView.text = "${type.name} $coordinate ${dTime}ms"
                            } catch (e: Throwable) {
                                textView.text = "${type.name} error $e"
                            }
                            finish(type)
                        }
                    } else {
                        textView.text = "${type.name} api is not available."
                        finish(type)
                    }
                }
                LocationProvider.GOOGLE -> {
                    val api = GoogleApiAvailability.getInstance()
                    if (api.isGooglePlayServicesAvailable(this) == com.google.android.gms.common.ConnectionResult.SUCCESS) {
                        TODO()
                        finish(type)
                    } else {
                        requireNotNull(textViews)[type]!!.text =
                            "${type.name} api is not available."
                        finish(type)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).also { root ->
            root.orientation = LinearLayout.VERTICAL
            root.addView(Button(this).also {
                button = it
                it.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                it.text = "get coordinate"
                it.setOnClickListener {
                    getCoordinate()
                }
            })
            val views = mutableMapOf<LocationProvider, TextView>()
            LocationProvider.values().forEach { provider ->
                root.addView(TextView(this).also { views[provider] = it })
            }
            textViews = views.toMap()
        })
        val permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val isNotGranted = permissions.any {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (isNotGranted) {
            requestPermissions(permissions, 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                for (i in permissions.indices) {
                    val permission = permissions[i]
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        showToast("Permission $permission is not granted!")
                        finish()
                        return
                    }
                }
            }
        }
    }
}
