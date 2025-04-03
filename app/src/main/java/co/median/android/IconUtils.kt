package co.median.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.graphics.drawable.StateListDrawableCompat
import androidx.core.graphics.drawable.toBitmap
import co.median.android.icons.Icon

object IconUtils {

    @JvmStatic
    fun generateMenuIconStates(
        activity: MainActivity,
        activeIcon: String,
        inactiveIcon: String,
        iconSize: Int,
        iconColor: Int
    ): StateListDrawableCompat {
        val drawableStates = StateListDrawableCompat()

        val activeIconDrawable = Icon(activity, activeIcon, iconSize, iconColor).getDrawable()
        val inactiveIconDrawable =
            Icon(activity, inactiveIcon, iconSize, iconColor).getDrawable()

        drawableStates.addState(intArrayOf(android.R.attr.state_checked), activeIconDrawable)
        drawableStates.addState(intArrayOf(-android.R.attr.state_checked), inactiveIconDrawable)

        return drawableStates
    }

    @JvmStatic
    fun generateMenuIconStatesWithEqualSides(
        activity: MainActivity,
        activeIcon: String,
        inactiveIcon: String,
        iconSize: Int,
        iconColor: Int
    ): StateListDrawableCompat {
        val drawableStates = StateListDrawableCompat()

        val activeIconDrawable = Icon(activity, activeIcon, iconSize, iconColor).getDrawable()

        val inactiveIconDrawable =
            Icon(activity, inactiveIcon, iconSize, iconColor).getDrawable()

        val squareActiveIconDrawable = resizeDrawableToSquare(activity, activeIconDrawable)
        val squareInactiveIconDrawable = resizeDrawableToSquare(activity, inactiveIconDrawable)

        drawableStates.addState(intArrayOf(android.R.attr.state_checked), squareActiveIconDrawable)
        drawableStates.addState(
            intArrayOf(-android.R.attr.state_checked),
            squareInactiveIconDrawable
        )

        return drawableStates
    }

    // This function resizes any drawable into a square while preserving its aspect ratio
    private fun resizeDrawableToSquare(activity: MainActivity, drawable: Drawable?): Drawable? {
        if (drawable == null) return null

        // Convert to Bitmap first
        val bitmap = drawable.toBitmap()

        // Use the maximum dimension as the square size
        val size = maxOf(bitmap.width, bitmap.height)

        // Create a new bitmap to hold the square drawable
        val squareBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        // Create a canvas to draw onto the new bitmap
        val canvas = Canvas(squareBitmap)

        // Set a transparent background
        canvas.drawColor(Color.TRANSPARENT)

        // Calculate the center position to draw the original bitmap
        val left = (size - bitmap.width) / 2
        val top = (size - bitmap.height) / 2

        // Draw the original bitmap centered in the square canvas
        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), null)

        // Return the square drawable as a BitmapDrawable
        return BitmapDrawable(activity.resources, squareBitmap)
    }
}