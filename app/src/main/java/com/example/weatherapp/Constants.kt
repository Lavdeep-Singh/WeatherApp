package com.example.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

//to check if connected to network
object Constants {
    //api key constant
    const val APP_ID:String="b0dd1da3f6d4a2e0bf00f397681f0c9a"
    const val BASE_URL:String="http://api.openweathermap.org/data/"
    const val METRIC_UNIT:String="metric"
    const val PREFERENCE_NAME="WeatherAppPrefrence"
    const val WEATHER_RESPONSE_DATA="weather_response_data"

    fun isNetworkAvailable(context: Context):Boolean{
        val connectivityManager=context.
                getSystemService(Context.CONNECTIVITY_SERVICE) as
                ConnectivityManager

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){ //new method
            val network=connectivityManager.activeNetwork?:return false
            val activeNetwork=connectivityManager.getNetworkCapabilities(network)?:return false

            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->  true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)-> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)-> true
                else -> false
            }
        }else{ //old way
            val networkInfo=connectivityManager.activeNetworkInfo
            return networkInfo!=null && networkInfo.isConnectedOrConnecting
        }
    }
}