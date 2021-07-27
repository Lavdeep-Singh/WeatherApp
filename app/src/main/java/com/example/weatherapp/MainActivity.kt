package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.models.WeaatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.DexterBuilder
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient //to get latitude and longitude of the user
    lateinit var dialog:AlertDialog
    private var mProgressDialog: Dialog?=null
    lateinit var mSharedPreferences:SharedPreferences //to store weather data

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mFusedLocationProviderClient=LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences=getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)
        setUpUI()

        if(!isLocationEnabled()){ //if location not enabled
            Toast.makeText(this, "Location is turned off. Please turn it on", Toast.LENGTH_LONG).show()
            //redirecting user to location settings
            val intent= Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            //using dexter to get permissions
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
                .withListener(object:MultiplePermissionsListener{
                    override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
                        if(p0!!.areAllPermissionsGranted()){ //if all permissions are granted
                            requestLocationData()
                        }
                        if(p0.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity, "You can enable permissions in settings", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown( //user denied permission but we need it anyway then show dialogue
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?
                    ) {
                        showRationalDialogForPermissions() //show dialog
                    }

                }).onSameThread().check()
        }
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if(Constants.isNetworkAvailable(this)){ //check if connected to internet
              val retrofit:Retrofit= Retrofit.Builder()
                  .baseUrl(Constants.BASE_URL)
                  .addConverterFactory(GsonConverterFactory.create()).build() //prepare the retrofit

            val service:WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)//create a service based on retrofit

            val listCall: Call<WeaatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID//make a list call based on the service
            )
            showCustomProgressDialog()//show progress dialog

            listCall.enqueue(object : Callback<WeaatherResponse>{ //add to queue
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeaatherResponse>,
                    response: Response<WeaatherResponse>
                ) {
                    if(response.isSuccessful){
                        dismissCustomDialog()//dismiss dialog
                        val weatherList: WeaatherResponse? =response.body()
                        //shared perfrences can only store generic data types
                        //This method serializes the specified object into its equivalent Json representation.
                        val weatherResponseJsonString= Gson().toJson(weatherList) //convert to Json string
                        val editor=mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()
                        if (weatherList != null) {
                            setUpUI()
                        }else{
                            Log.i("Null response","No weather list")
                        }
                        Log.i("Response result","$weatherList")
                    }else{
                        val rc= response.code()
                        when(rc){
                            400->{
                                Log.e("Error-400","Bad Connection")
                            }
                            404->{
                                Log.e("Error-404","Not found")
                            }
                            else->{
                                Log.e("Error","Generic error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeaatherResponse>, t: Throwable) {
                    Log.e("Errorrrrrrr", t.message.toString())
                    dismissCustomDialog()
                }

            })
        }else{
            Toast.makeText(this, "no internet connection", Toast.LENGTH_SHORT).show()
        }
    }
    //alert dialog when permission is denied
    private fun showRationalDialogForPermissions(){
         dialog=AlertDialog.Builder(this@MainActivity)
            .setMessage("It looks like you have turned off permissions required for the app to run. It can be enabled in settings")
            .setPositiveButton("Go to settings")
            { _,_ -> //go to application settings when pressed positive button
                try{
                    val intent=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri= Uri.fromParts("package",packageName,null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e:ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel"){
                    dialog,_->
                dialog.dismiss()

            }.show()
    }

    override fun onPause() {
        super.onPause()
        if(dialog!=null){
            dialog.dismiss()
        }

    }

    //to check if location enabled
    private fun isLocationEnabled():Boolean{
        //instance of LocationManager
        val locationManager:LocationManager= getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||    //if location is on
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)  //or if network is on
    }

    @SuppressLint("MissingPermission") //already taken care of it
    private fun requestLocationData(){ //getting data from location
        val mLocationRequest= LocationRequest()  //instance of LocationRequest class
        mLocationRequest.priority=LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest,mLocationCallBack,
            Looper.myLooper()
        )
    }

    private val mLocationCallBack=object:LocationCallback(){
        override fun onLocationResult(p0: LocationResult) {
            val mLastLocation:Location=p0.lastLocation
            val latitude=mLastLocation.latitude
            Log.i("Current Latitude","$latitude")
            val longitude=mLastLocation.longitude
            Log.i("Current latitude","$longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }

    private fun showCustomProgressDialog(){
        mProgressDialog= Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun dismissCustomDialog(){
        if(mProgressDialog !=null){
            mProgressDialog!!.dismiss()

        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setUpUI(){
        val weatherResponseJsonString=mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weaatherList= Gson().fromJson(weatherResponseJsonString,WeaatherResponse::class.java)
            //This method deserializes the specified Json into an object of the specified class.

            for(i in weaatherList.weather.indices){
                Log.i("Weather name",weaatherList.weather.toString())
                tv_main.text= weaatherList.weather[i].main
                tv_main_description.text=weaatherList.weather[i].description
                tv_temp.text= weaatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tv_sunrise_time.text=unixTime(weaatherList.sys.sunrise)
                tv_sunset_time.text=unixTime(weaatherList.sys.sunset)

                tv_humidity.text=weaatherList.main.humidity.toString()+"%"
                val min=weaatherList.main.temp_min
                val max=weaatherList.main.temp_max
                val df=DecimalFormat("##.#")
                tv_min.text=df.format(min).toString()+"min"
                tv_max.text=df.format(max).toString()+"max"
                tv_speed.text=weaatherList.wind.speed.toString()
                tv_name.text=weaatherList.name
                tv_country.text=weaatherList.sys.country

                when(weaatherList.weather[i].icon){
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                }
            }
        }


    }

    private fun getUnit(value:String):String?{
        var value= "°C" //°F
        if("US"==value || "LR"==value || "MM"== value){
            value= "°F"
        }
        return value
    }

    private fun unixTime(timex:Long):String?{
        val date = Date(timex *1000L)
        val sdf= SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone= TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
                true
            }else->{
                super.onOptionsItemSelected(item)
            }
        }
    }
}