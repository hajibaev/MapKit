package com.example.yandexmap.ui


import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.map.PolylineMapObject
import com.yandex.mapkit.map.VisibleRegion

const val EMPTY_STRING = ""

fun VisibleRegion.toBoundingBox() = BoundingBox(bottomLeft, topRight)

fun View.showToast(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_SHORT).show()
}

fun PolylineMapObject.styleMainRoute(context: Context) {
    zIndex = 10f
    setStrokeColor(ContextCompat.getColor(context, android.R.color.darker_gray))
    strokeWidth = 5f
    outlineColor = ContextCompat.getColor(context, android.R.color.black)
    outlineWidth = 3f
}

fun PolylineMapObject.styleAlternativeRoute(context: Context) {
    zIndex = 5f
    setStrokeColor(ContextCompat.getColor(context, android.R.color.holo_blue_light))
    strokeWidth = 4f
    outlineColor = ContextCompat.getColor(context, android.R.color.black)
    outlineWidth = 2f
}

fun Activity?.hideKeyboard(view: View) {
    this ?: return
    view.clearFocus()
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}