package eu.sampa.happyPlaces.adapters

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.sampa.happyPlaces.R
import eu.sampa.happyPlaces.activities.AddHappyPlaceActivity
import eu.sampa.happyPlaces.activities.MainActivity
import eu.sampa.happyPlaces.database.DatabaseHandler
import eu.sampa.happyPlaces.models.HappyPlaceModel
import kotlinx.android.synthetic.main.item_happy_place.view.*

// https://www.raywenderlich.com/1560485-android-recyclerview-tutorial-with-kotlin
open class HappyPlacesAdapter(
    private val context: Context,
    private var list: ArrayList<HappyPlaceModel>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var onClickListener: OnClickListener? = null

    // Inflates the item views which is designed in xml layout file
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MyViewHolder(LayoutInflater.from(context).inflate(R.layout.item_happy_place, parent, false))
    }

    /*
     * Binds each item in the ArrayList to a view
     * This new ViewHolder should be constructed with a new View that can represent the items
     * of the given type. You can either create a new View manually or inflate it from an XML
     * layout file.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val model = list[position]
        if (holder is MyViewHolder) {
            holder.itemView.iv_place_image.setImageURI(Uri.parse(model.image))
            holder.itemView.tvTitle.text = model.title
            holder.itemView.tvDescription.text = model.description
            // To assign a onClickListener to each individual record
            // https://antonioleiva.com/recyclerview-listener/
            holder.itemView.setOnClickListener {
                if(onClickListener != null) {
                    onClickListener!!.onClick(position, model)
                }
            }
        }
    }

    // Get's the number of items in the list
    override fun getItemCount(): Int {
        return list.size
    }


    // A ViewHolder describes an item view and metadata about its place within the RecyclerView
    private class MyViewHolder(view: View) : RecyclerView.ViewHolder(view)

    // https://antonioleiva.com/recyclerview-listener/
    interface OnClickListener {
        fun onClick(position: Int, model: HappyPlaceModel)
    }
    // https://antonioleiva.com/recyclerview-listener/
    fun setOnClickListener(onClickListener: OnClickListener) {
        this.onClickListener = onClickListener
    }
    // For the swipe edit functionality
    fun notifyEditItem(activity: Activity, position : Int, requestCode : Int) {
        val intent = Intent(context, AddHappyPlaceActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_PLACE_DETAILS, list[position])
        activity.startActivityForResult(intent, requestCode)
        notifyItemChanged(position)
    }
    fun removeAt(position: Int){
        val dbHandler = DatabaseHandler(context)
        val isDeleted = dbHandler.deleteHappyPlace(list[position])
        if(isDeleted > 0) {
            list.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}