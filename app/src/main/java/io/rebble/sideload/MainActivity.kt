package io.rebble.sideload


import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.lang.reflect.Method


class MainActivity : AppCompatActivity() {
    private val OPEN_REQUEST_CODE = 41;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pm: PackageManager = packageManager
        val pebbleIsInstalled: Boolean = isPackageInstalled("com.getpebble.android.basalt", pm)

        if (pebbleIsInstalled && (intent.data != null)) {
            handlePebbleFile(intent) // Handle pebble file being sent
            finish()
        } else if (!pebbleIsInstalled) {
            tellUserTheyNeedPebble()
        }

        val fileButton: Button = findViewById(R.id.file_select)
        fileButton.setOnClickListener {
            if (pebbleIsInstalled) {
                chooseFile()
            } else {
                tellUserTheyNeedPebble()
            }
        }
    }

    private fun chooseFile() {
        val type = "*/*"
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = type
        startActivityForResult(Intent.createChooser(i, "select file"), OPEN_REQUEST_CODE)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            handlePebbleFile(data)
        } else {
            tellUserCouldntOpenFile()
        }
    }

    private fun handlePebbleFile(intent: Intent) {
        if (isValidFile(intent))
            attemptForward(intent.data)
        else
            tellUserInvalidFile()
    }

    private fun attemptForward(fileURI: Uri?) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                val m: Method = StrictMode::class.java.getMethod("disableDeathOnFileUriExposure")
                m.invoke(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val sendIntent = Intent()
        sendIntent.component = ComponentName("com.getpebble.android.basalt", "com.getpebble.android.main.activity.MainActivity")
        sendIntent.setPackage("com.getpebble.android.basalt")
        sendIntent.action = "android.intent.action.VIEW"
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sendIntent.data = fileURI
        startActivity(sendIntent)
    }

    private fun tellUserCouldntOpenFile() {
        Toast.makeText(this, getString(R.string.could_not_open_file), Toast.LENGTH_SHORT).show()
    }
    private fun tellUserInvalidFile() {
        Toast.makeText(this, getString(R.string.invalid_file), Toast.LENGTH_SHORT).show()
    }
    private fun tellUserTheyNeedPebble() {
        Toast.makeText(this, getString(R.string.no_pebble), Toast.LENGTH_LONG).show()
    }

    private fun isPackageInstalled(
        packagename: String,
        packageManager: PackageManager
    ): Boolean {
        return try {
            packageManager.getPackageGids(packagename)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getExtension(intent: Intent): String? {
        intent.data?.let { returnUri ->
            contentResolver.query(returnUri, null, null, null, null)
        }?.use { cursor ->
            /*
             * Get the column indexes of the data in the Cursor,
             * move to the first row in the Cursor, get the data,
             * and return the last 4 characters
             */
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            return cursor.getString(nameIndex).takeLast(4)
        }
        return null
    }

    private fun isValidFile(intent: Intent): Boolean {
        val uri: Uri? = intent.data
        //Check first 4 bytes to make sure this is a valid file
        val bytes = uri?.let { contentResolver.openInputStream(it) }
        val buf = ByteArray(4)
        bytes?.read(buf, 0, buf.size)
        if (buf.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))) {
            // check if the extension is correct
            val extensions = setOf(".pbz", ".pbw", ".pbl")
            if (getExtension(intent) in extensions) {
                return true
            }
        }
        return false
    }
}

