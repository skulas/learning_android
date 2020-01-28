package com.example.androidfirrstapp

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.lang.Exception
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class MainActivity : AppCompatActivity() {
    private val logger: Logger = Logger.getLogger("name.of.the.cabron")
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null
    private val gpsInterval: Long = 750
    private val gpsFastestInterval: Long = 500
    private lateinit var mLastLocation: Location
    private lateinit var mLocationRequest: LocationRequest
    private val requestPermissionLocation = 10
    private val db = FirebaseFirestore.getInstance()

    private lateinit var btnStartupdate: Button
    private lateinit var btnStopUpdates: Button
    private lateinit var txtLat: TextView
    private lateinit var txtLong: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtDetail: TextView

    private lateinit var btnSignIn: SignInButton
    private lateinit var btnSignOut: Button
    private lateinit var btnDisconnect: Button

    // Sign IN - Google Sign-in Options
    private var mGoogleSignInClient: GoogleSignInClient? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var gso : GoogleSignInOptions


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logger.level = Level.ALL

        btnStartupdate = findViewById(R.id.btn_start_upds)
        btnStopUpdates = findViewById(R.id.btn_stop_upds)
        txtLat = findViewById(R.id.txtLat)
        txtLong = findViewById(R.id.txtLong)
        txtTime = findViewById(R.id.txtTime)
        txtStatus = findViewById(R.id.txtStatus)
        txtDetail = findViewById(R.id.txtDetail)

        // [START initialize_auth]
        // Initialize Firebase Auth
        gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        auth = FirebaseAuth.getInstance()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps()
        }

        btnStartupdate.setOnClickListener {
            if (checkPermissionForLocation(this)) {
                startLocationUpdates()
                btnStartupdate.isEnabled = false
                btnStopUpdates.isEnabled = true
            }
        }

        btnStopUpdates.setOnClickListener {
            stoplocationUpdates()
            txtTime.text = getString(R.string.updtes_stopped)
            btnStartupdate.isEnabled = true
            btnStopUpdates.isEnabled = false
        }

        btnSignIn = findViewById(R.id.sign_in_button)
        btnSignIn.setSize(SignInButton.SIZE_WIDE)
        btnSignIn.setOnClickListener { signIn() }
        btnSignOut = findViewById(R.id.btnSignOut)
        btnSignOut.setOnClickListener { signOut() }
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnDisconnect.setOnClickListener { revokeAccess() }
    }

    public override fun onStart() {
        super.onStart()

//        val account = GoogleSignIn.getLastSignedInAccount(this)
//        updateUI(account)
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        updateUI(currentUser)

        /// HARD CODED SIGN IN ///
        val email = "skulaslaw@hotmail.com"
        val password = "123456"
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
            }

    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account!!)
            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e)
                // [START_EXCLUDE]
                updateUI(null)
                // [END_EXCLUDE]
            }
        }
//        super.onActivityResult(requestCode, resultCode, data)
//
//        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
//        if (requestCode == RC_SIGN_IN) {
//            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
//            try {
//                // Google Sign In was successful, authenticate with Firebase
//                val account = task.getResult(ApiException::class.java)
//                firebaseAuthWithGoogle(account!!)
//            } catch (e: ApiException) {
//                // Google Sign In failed, update UI appropriately
//                Log.w(TAG, "Google sign in failed", e)
//                // ...
//            }
//        }
    }

    ///////////
    // LOGIN //
    //    LINKS
    //    https://firebase.google.com/docs/auth/android/google-signin
    //    https://developers.google.com/identity/sign-in/android/sign-in
    //    https://console.developers.google.com/apis/credentials?project=test-apis-190311&folder=&organizationId=
    ///////////

    private fun signIn() {
        val signInIntent = mGoogleSignInClient!!.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun signOut() {
        // Firebase sign out
        auth.signOut()

        // Google sign out
        mGoogleSignInClient!!.signOut().addOnCompleteListener(this) {
            updateUI(null)
        }
    }

    private fun revokeAccess() {
        // Firebase sign out
        auth.signOut()

        // Google revoke access
        mGoogleSignInClient!!.revokeAccess().addOnCompleteListener(this) {
            updateUI(null)
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.id!!)

        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    updateUI(null)
                    val builder = AlertDialog.Builder(this)
                    builder.setMessage(R.string.auth_failed)
                        .setCancelable(true)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            startActivityForResult(Intent(ACTION_LOCATION_SOURCE_SETTINGS), 11)
                        }
                        .setNegativeButton(R.string.no) { dialog, _ ->
                            dialog.cancel()
                            finish()
                        }
                    val alert: AlertDialog  = builder.create()
                    alert.show()

                }
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            txtStatus.text = getString(R.string.google_status_fmt, user.email)
            txtDetail.text = getString(R.string.firebase_status_fmt, user.uid)

            btnSignIn.visibility = View.GONE
            btnDisconnect.visibility = View.VISIBLE
        } else {
            txtStatus.setText(R.string.signed_out)
            txtDetail.text = null

            btnSignIn.visibility = View.VISIBLE
            btnDisconnect.visibility = View.GONE
        }
    }


    /////////
    // GPS //
    /////////

    private fun buildAlertMessageNoGps() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_msg_gps_disabled)
            .setCancelable(false)
            .setPositiveButton(R.string.yes) { dialog, id ->
                startActivityForResult(Intent(ACTION_LOCATION_SOURCE_SETTINGS), 11)
            }
            .setNegativeButton(R.string.no) { dialog, id ->
                dialog.cancel()
                finish()
            }
        val alert: AlertDialog  = builder.create()
        alert.show()
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            // do work here
            //            locationResult.lastLocation
            onLocationChanged(locationResult.lastLocation)
        }
    }

    private fun stoplocationUpdates() {
        mFusedLocationProviderClient!!.removeLocationUpdates(mLocationCallback)

        txtLat.text = getString(R.string.updtes_stopped)
        txtLong.text = getString(R.string.updtes_stopped)
    }

    private fun startLocationUpdates() {
        // Update UI
        txtTime.text = ""
        txtLat.text = getString(R.string.warming_gps)
        txtLong.text = getString(R.string.warming_gps)

        // Create the location request to start receiving updates
        mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = gpsInterval
        mLocationRequest.fastestInterval = gpsFastestInterval

        // Create LocationSettingsRequest object using location request
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest)
        val locationSettingsRequest = builder.build()

        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(locationSettingsRequest)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        // new Google API SDK v11 uses getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        mFusedLocationProviderClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback,
            Looper.myLooper())
    }

    private fun saveLocationInServer(newLocation: Location) {
        /// Each Club has a Collection of Users
        /// Each User in the club has a Collection of trainings
        /// Each training has a Collection of Locations
        val now = Instant.now()
        val locReport = hashMapOf(
            "location" to newLocation,
            "user_id" to "eze123",
            "time" to "we need to find a solution for sync"
        )
        var ex:Exception?
        db.collection("locations_eze").document("loc_" + now)
            .set(locReport)
            .addOnSuccessListener { logger.info("DocumentSnapshot successfully written!") }
            .addOnFailureListener { e -> logger.severe("Error writing document: $e") }

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == requestPermissionLocation) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //We have to add startlocationUpdate() method later instead of Toast
                Toast.makeText(this,R.string.permission_granted,Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun checkPermissionForLocation(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                // Show the permission request
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    requestPermissionLocation)
                false
            }
        } else {
            true
        }
    }

    fun onLocationChanged(location: Location) {
        // New location has now been determined
        // New location has now been determined

        mLastLocation = location
        val date: Date = Calendar.getInstance().time
        val sdf = SimpleDateFormat("hh:mm:ss a")
        val dateStr = sdf.format(date)
        txtTime.text = getString(R.string.update_time, dateStr)
        txtLat.text = getString(R.string.tmplt_lat, mLastLocation.latitude)
        txtLong.text = getString(R.string.tmplt_long, mLastLocation.longitude)
        // You can now create a LatLng Object for use with maps
        saveLocationInServer(mLastLocation)


        mLastLocation = location
    }

    companion object {
        private const val TAG = "EZE Ass Activity"
        private const val RC_SIGN_IN = 9001
    }

}
