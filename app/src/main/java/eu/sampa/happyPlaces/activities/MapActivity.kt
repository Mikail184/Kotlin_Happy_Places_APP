package eu.sampa.happyPlaces.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import eu.sampa.happyPlaces.R
import eu.sampa.happyPlaces.database.DatabaseHandler
import eu.sampa.happyPlaces.models.HappyPlaceModel
import kotlinx.android.synthetic.main.activity_add_happy_place.*
import kotlinx.android.synthetic.main.activity_map.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private var mHappyPlaceDetails: HappyPlaceModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)



        // Checking if the intent carried extra info
        if(intent.hasExtra(MainActivity.EXTRA_PLACE_DETAILS)) {
            mHappyPlaceDetails = intent.getParcelableExtra(MainActivity.EXTRA_PLACE_DETAILS) as HappyPlaceModel
        }

        if(mHappyPlaceDetails != null){
            // Adds the back button on the ActionBar
            setSupportActionBar(toolbar_map)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = mHappyPlaceDetails?.title
            toolbar_map.setNavigationOnClickListener {
                onBackPressed()
            }
            // https://developers.google.com/android/reference/com/google/android/gms/maps/SupportMapFragment
            val supportMapFragment : SupportMapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            supportMapFragment.getMapAsync(this)
        }
    }

    // Created after extending the MapActivity and gets called as soon as the map is ready
    override fun onMapReady(googleMap: GoogleMap?) {
        // Gets the latitude and longitude from the that was given by the intent
        val position = mHappyPlaceDetails?.latitude?.let { mHappyPlaceDetails?.longitude?.let { it1 -> LatLng(it, it1) } }
        // Adds the typical google maps marker on the map
        googleMap!!.addMarker(position?.let { MarkerOptions().position(it).title(mHappyPlaceDetails?.location) })
        // Zooming functionality
        val zoomingInMap = CameraUpdateFactory.newLatLngZoom(position, 15f)
        googleMap.animateCamera(zoomingInMap)
    }
}
