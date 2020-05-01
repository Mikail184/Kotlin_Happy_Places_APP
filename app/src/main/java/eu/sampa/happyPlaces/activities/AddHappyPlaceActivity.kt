package eu.sampa.happyPlaces.activities

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import eu.sampa.happyPlaces.R
import eu.sampa.happyPlaces.database.DatabaseHandler
import eu.sampa.happyPlaces.models.HappyPlaceModel
import eu.sampa.happyPlaces.utils.GetAddressFromLatLng
import kotlinx.android.synthetic.main.activity_add_happy_place.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class AddHappyPlaceActivity : AppCompatActivity(), View.OnClickListener {

    // Creates a variable for GALLERY Selection which will be later used in the onActivityResult method.
    companion object {
        private const val GALLERY = 1
        private const val CAMERA = 2
        private const val IMAGE_DIRECTORY = "HappyPlacesImages"
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 3
    }

    private var saveImageToInternalStorage : Uri? = null
    private var mLatitude : Double = 0.0
    private var mLongitude : Double = 0.0
    // For the swipe feature
    private var mHappyPlaceDetails : HappyPlaceModel? = null
    // Will be initialized later for the get current position functionality
    // https://medium.com/@droidbyme/get-current-location-using-fusedlocationproviderclient-in-android-cb7ebf5ab88e
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    // Creating the variables of Calender Instance and DatePickerDialog listener to use it for date selection
    // A variable to get an instance calendar using the default time zone and locale.
    private var cal = Calendar.getInstance()

    /* A variable for DatePickerDialog OnDateSetListener.
    * The listener used to indicate the user has finished selecting a date. It will be initialized later. */
    private lateinit var dateSetListener : DatePickerDialog.OnDateSetListener

    // Used to increment when someone clicks on the Add Photo button see below in onClick function
    private var addButtonClicked = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_happy_place)

        // Adds the back button on the ActionBar
        setSupportActionBar(toolbar_add_place)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar_add_place.setNavigationOnClickListener {
            onBackPressed()
        }
        // For the Places API
        if(!Places.isInitialized()) {
            Places.initialize(this@AddHappyPlaceActivity, resources.getString(R.string.google_maps_key))
        }

        if(intent.hasExtra(MainActivity.EXTRA_PLACE_DETAILS)) {
            mHappyPlaceDetails = intent.getParcelableExtra(MainActivity.EXTRA_PLACE_DETAILS) as HappyPlaceModel
        }

        // Initialize the DatePicker and sets the selected date
        // https://www.tutorialkart.com/kotlin-android/android-datepicker-kotlin-example/
        dateSetListener = DatePickerDialog.OnDateSetListener{
                _, year, month, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateInView()
        }
        // Automatically sets the current date
        updateDateInView()
        // Uses functionality in the onClick function below
        et_date.setOnClickListener(this)
        tv_add_image.setOnClickListener(this)
        btn_save.setOnClickListener(this)
        et_location.setOnClickListener(this)
        tv_select_current_location.setOnClickListener(this)

        if(mHappyPlaceDetails != null) {
            supportActionBar?.title = "Edit Happy PLace"
            et_title.setText(mHappyPlaceDetails!!.title)
            et_description.setText(mHappyPlaceDetails!!.description)
            et_date.setText(mHappyPlaceDetails!!.date)
            et_location.setText(mHappyPlaceDetails!!.location)
            mLatitude = mHappyPlaceDetails!!.latitude
            mLongitude = mHappyPlaceDetails!!.longitude
            saveImageToInternalStorage = Uri.parse(mHappyPlaceDetails!!.image)
            iv_place_image.setImageURI(saveImageToInternalStorage)
            btn_save.text = "UPDATE"
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    }

    // This is a override method after extending the onclick listener interface (gets created automatically)
    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.et_date -> {
                DatePickerDialog(this@AddHappyPlaceActivity, dateSetListener,
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            R.id.tv_add_image -> {
                val pictureDialog = AlertDialog.Builder(this)
                pictureDialog.setTitle("Select Action")
                val pictureDialogItems = arrayOf("Select photo from gallery", "Capture photo from camera")
                pictureDialog.setItems(pictureDialogItems) {
                        _, which ->
                    when(which) {
                        0 -> choosePhotoFromGallery()
                        1 -> takePhotoFromCamera()
                    }
                }
                pictureDialog.show()
                /* Used to display the Dialog to get to the menu after the user
                *  denied access 2 or more times */
                addButtonClicked += 1
                if (addButtonClicked > 2) {
                    if (ContextCompat.checkSelfPermission(this@AddHappyPlaceActivity,
                            Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                            showRationalDialogForPermissions()
                        }
                    if (ContextCompat.checkSelfPermission(this@AddHappyPlaceActivity,
                            Manifest.permission.READ_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                            showRationalDialogForPermissions()
                        }
                    if (ContextCompat.checkSelfPermission(this@AddHappyPlaceActivity,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                            showRationalDialogForPermissions()
                        }
                }
            }
            R.id.btn_save -> {
                when {
                    et_title.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter title", Toast.LENGTH_SHORT).show()
                    }
                    et_description.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT)
                            .show()
                    }
                    et_location.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please select location", Toast.LENGTH_SHORT)
                            .show()
                    }
                    saveImageToInternalStorage == null -> {
                        Toast.makeText(this, "Please add image", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        // Assigning all the values to data model class.
                        val happyPlaceModel = HappyPlaceModel(
                            if(mHappyPlaceDetails == null) 0 else mHappyPlaceDetails!!.id,
                            et_title.text.toString(),
                            saveImageToInternalStorage.toString(),
                            et_description.text.toString(),
                            et_date.text.toString(),
                            et_location.text.toString(),
                            mLatitude,
                            mLongitude
                        )
                        // Here we initialize the database handler class.
                        val dbHandler = DatabaseHandler(this)
                        if (mHappyPlaceDetails == null) {
                            val addHappyPlace = dbHandler.addHappyPlace(happyPlaceModel)
                            if (addHappyPlace > 0) {
                                setResult(Activity.RESULT_OK)
                                finish() // Gets us back to MainActivity
                            }
                        } else{
                            val updateHappyPlace = dbHandler.updateHappyPlace(happyPlaceModel)
                            // greater than zero indicates that everything worked out
                            if (updateHappyPlace > 0) {
                                setResult(Activity.RESULT_OK)
                                finish() // Gets us back to MainActivity
                            }
                        }

                    }
                }
            }
            // For the Places API
            R.id.et_location -> {
                try{
                    // This is the list of fields that need to be passed
                    val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
                    // Start the autocomplete intent with a unique request code.
                    val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields).build(this@AddHappyPlaceActivity)
                    startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)
            }catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Couldnt load it", Toast.LENGTH_SHORT).show()}
            }
            R.id.tv_select_current_location -> {
                if (!isLocationEnabled()) {
                    Toast.makeText(this, "Your location provider is turned off. Please turn it on.", Toast.LENGTH_SHORT).show()
                    // This will redirect the user to settings from where you need to turn on the location provider.
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                } else {
                    // https://www.androdocs.com/kotlin/getting-current-location-latitude-longitude-in-android-using-kotlin.html
                    Dexter.withActivity(this)
                        .withPermissions(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION).withListener(object : MultiplePermissionsListener {
                            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                                if (report!!.areAllPermissionsGranted()) {
                                    requestNewLocationData()
                                }
                            }
                            override fun onPermissionRationaleShouldBeShown(
                                permissions: MutableList<PermissionRequest>?,
                                token: PermissionToken?
                            ) {
                                showRationalDialogForPermissions()
                            }
                        }).onSameThread()
                        .check()
                }
            }
        }
    }

    // https://stackoverflow.com/questions/51313359/get-current-location-android-kotlin
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER    )
    }

    // Method used for taking pictures with the Camera
    private fun takePhotoFromCamera() {
        // Asking for permissions using DEXTER Library
        Dexter.withContext(this).withPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                // Here after all the permission are granted launch the Camera to capture an image
                val galleryIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(galleryIntent, CAMERA    )
            }
            override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) {
                token?.continuePermissionRequest()
            }
        }).onSameThread().check()
    }

    // Method used for image selection from GALLERY/PHOTOS
    private fun choosePhotoFromGallery() {
        // Asking for permissions using DEXTER Library
        Dexter.withContext(this).withPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                // Here after all the permission are granted, launch the gallery to select and image.
                val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(galleryIntent,
                    GALLERY
                )
            }
            override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) {
                token?.continuePermissionRequest()
            }
        }).onSameThread().check()
    }

    // Message to be shown if user denies access and possibly send him to the settings
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage("It looks like you have turned off " +
                "permissions required for this feature").setPositiveButton("GO TO SETTINGS")
        { _, _ ->
            try{
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }.show()
    }

    // Handles the chosen Image from the startActivityResult from choosePhotoFromGallery and takePhotoFromCamera
    @RequiresApi(Build.VERSION_CODES.P)
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK) {
            if(requestCode == GALLERY) {
                if(data != null) {
                    val contentURI = data.data
                    // For more info go to https://stackoverflow.com/questions/56651444/deprecated-getbitmap-with-api-29-any-alternative-codes
                    try {
                        if(Build.VERSION.SDK_INT < 28) {
                            // Here this is used to get an bitmap from URI
                            val selectedImageBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, contentURI)
                            // Saving an image which is selected from GALLERY. And printed the path in logcat
                            saveImageToInternalStorage = saveImageToInternalStorage(selectedImageBitmap)
                            Log.e("Saved image: ", "Path :: $saveImageToInternalStorage")
                            iv_place_image!!.setImageBitmap(selectedImageBitmap) // Set the selected image from GALLERY to imageView
                        } else {
                            val selectedImageBitmapSource = contentURI?.let { ImageDecoder.createSource(this.contentResolver, it) }
                            val selectedImageBitmap = selectedImageBitmapSource?.let { ImageDecoder.decodeBitmap(it) }
                            // Saving an image which is selected from GALLERY. And printed the path in logcat
                            saveImageToInternalStorage = selectedImageBitmap?.let { saveImageToInternalStorage(it) }
                            Log.e("Saved image: ", "Path :: $saveImageToInternalStorage")
                            iv_place_image.setImageBitmap(selectedImageBitmap)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        Toast.makeText(this@AddHappyPlaceActivity, "Failed to load the Image!", Toast.LENGTH_SHORT).show()
                    }
                }
                // Camera result will be received here
            } else if(requestCode == CAMERA){
                val thumbNail : Bitmap = data!!.extras!!.get("data") as Bitmap // Bitmap from camera
                // Saving an image which is selected from CAMERA. And printed the path in logcat
                saveImageToInternalStorage = saveImageToInternalStorage(thumbNail)
                Log.e("Saved image: ", "Path :: $saveImageToInternalStorage")
                iv_place_image.setImageBitmap(thumbNail) // Set to the imageView
                // For the Places API
            } else if(requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
                val place : Place = Autocomplete.getPlaceFromIntent(data!!)
                et_location.setText(place.address)
                mLatitude = place.latLng!!.latitude
                mLongitude = place.latLng!!.longitude
            }
        }
    }

    // A function to update the selected date in the UI with selected format.
    private fun updateDateInView() {
        val myFormat = "dd.MM.yyyy"
        val sdf = SimpleDateFormat(myFormat, Locale.getDefault())
        et_date.setText(sdf.format(cal.time).toString())
    }

    /* https://android--code.blogspot.com/20 18/04/android-kotlin-save-image-to-internal.html
    Uri gives us the location back */
    private fun saveImageToInternalStorage(bitmap: Bitmap):Uri {
        // Get the context wrapper instance
        val wrapper = ContextWrapper(applicationContext)
        // This line returns a directory in the internal storage
        var file = wrapper.getDir(IMAGE_DIRECTORY, Context.MODE_PRIVATE)
        // First we give the location and then we generate a random Name for the Image
        file = File(file, "${UUID.randomUUID()}.jpg")
        //
        try {
            val stream : OutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG,100, stream)
            stream.flush()
            stream.close()
        }catch (e: IOException) {
            e.printStackTrace()
        }
        // Return the saved image uri
        return Uri.parse(file.absolutePath)
    }

    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 1000
        mLocationRequest.numUpdates = 1

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            mLatitude = mLastLocation.latitude
            Log.e("Current Latitude", "$mLatitude")
            mLongitude = mLastLocation.longitude
            Log.e("Current Longitude", "$mLongitude")


            val addressTask = GetAddressFromLatLng(this@AddHappyPlaceActivity, mLatitude, mLongitude)

            addressTask.setAddressListener(object : GetAddressFromLatLng.AddressListener {
                override fun onAddressFound(address: String?) {
                    Log.e("Address ::", "" + address)
                    et_location.setText(address) // Address is set to the edittext
                }

                override fun onError() {
                    Log.e("Get Address ::", "Something is wrong...")
                }
            })
            addressTask.executeGetAddress()
        }
    }
}
