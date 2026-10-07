package com.tbutman.tilde

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/**
 * Full-screen crop step after picking a photo: move and scale it inside the round frame, rotate if
 * needed, then use it. Saves the result as the profile photo and finishes with RESULT_OK.
 */
class CropActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        val cropView = findViewById<CropView>(R.id.crop_view)
        val uri = intent.data ?: return finish()

        worker.execute {
            val bitmap = Photo.decode(this, uri)
            runOnUiThread {
                if (bitmap == null) {
                    Toast.makeText(this, R.string.photo_unreadable, Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    cropView.bitmap = bitmap
                    findViewById<View>(R.id.crop_progress).visibility = View.GONE
                }
            }
        }

        findViewById<View>(R.id.crop_cancel).setOnClickListener { finish() }
        findViewById<View>(R.id.crop_rotate).setOnClickListener { cropView.rotate() }
        findViewById<View>(R.id.crop_done).setOnClickListener {
            val cropped = cropView.crop() ?: return@setOnClickListener
            worker.execute {
                Photo.save(this, Prefs(this).activeCardId, cropped)
                runOnUiThread {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
