package maurya.devansh.smsfetch

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage
import com.google.firebase.storage.FirebaseStorage
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter


class MainActivity : AppCompatActivity() {

    private val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sendSmsButton.disable()

        getSmsReadPermission {
            val smsList = getSmsList()
            numberTV.text = smsList.size.toString()
            descriptionTV.text = getString(R.string.sms_messages)

            val enSmsList = arrayListOf<String>()

            identifySmsLanguage(smsList, enSmsList).observe(this, Observer {
                if (it == smsList.size) {
                    numberEnTV.text = enSmsList.size.toString()
                    descriptionEnTV.text = getString(R.string.english_messages)
                    sendSmsButton.enable()
                    sendSmsButton.setOnClickListener {
                        uploadSmsCsv(enSmsList)
                        Toast.makeText(this@MainActivity, "Uploading messages",
                            Toast.LENGTH_SHORT).show()
                    }
                    Log.i("SMS", enSmsList.toString())
                }
            })
        }
    }

    private fun getSmsReadPermission(action: () -> Unit) {
        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted() {
                toast("Permission granted")
                action()
            }

            override fun onPermissionDenied(deniedPermissions: List<String>) {
                toast("Permission denied")
            }
        }

        TedPermission.with(this)
            .setPermissionListener(permissionListener)
            .setDeniedMessage("If you reject permission,you can not use this service\n\n" +
                    "Please turn on permissions at [Setting] > [Permission]")
            .setPermissions(Manifest.permission.READ_SMS)
            .check()
    }

    private fun getSmsList(): List<String> {

        val smsList = arrayListOf<String>()

        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, null, null, null, null)
        startManagingCursor(cursor)

        if (cursor?.moveToFirst() == true) {
            for (i in 0 until cursor.count) {
                smsList.add(cursor.getString(cursor.getColumnIndexOrThrow("body")).toString())
                cursor.moveToNext()
            }
        }
        cursor?.close()
        return smsList
    }

    @SuppressLint("HardwareIds")
    private fun uploadSmsCsv(smsList: List<String>) {

        val smsAsCsv = StringBuilder()
        val smsSet = HashSet(smsList)

        smsSet.forEach {
            smsAsCsv.append("\"${it.trim()}\"\n")
        }

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val fileName = "sms$androidId.csv"
        val file = File(filesDir, fileName)

        val out = FileWriter(file)
        out.write(smsAsCsv.toString().trim())
        out.close()

        val stream = FileInputStream(file)

        val storageRef = storage.reference
        val smsJsonRef = storageRef.child(fileName)
        smsJsonRef.putStream(stream)
            .addOnSuccessListener {
                Toast.makeText(this, "Upload success", Toast.LENGTH_SHORT).show()
                sendSmsButton.text = "Uploaded"
            }
            .addOnFailureListener {
                Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
            }
    }

    private fun identifySmsLanguage(smsList: List<String>, enSmsList: ArrayList<String>): LiveData<Int> {
        val smsCountLiveData = MutableLiveData<Int>()
        smsCountLiveData.value = 0

        val languageIdentifier = FirebaseNaturalLanguage.getInstance().languageIdentification
        smsList.forEach {
            languageIdentifier.identifyLanguage(it)
                .addOnSuccessListener { languageCode ->
                    synchronized (smsCountLiveData) {
                        if (languageCode == "en") {
                            Log.i("SMS", "Language: $languageCode")
                            enSmsList.add(it)
                        } else {
                            Log.i("SMS", "Message not in English")
                        }
                        smsCountLiveData.apply {
                            value = value?.plus(1)
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("SMS", "Failed in language identification: $it")
                    synchronized(smsCountLiveData) {
                        smsCountLiveData.apply {
                            value = value?.plus(1)
                        }
                    }
                }
        }

        return smsCountLiveData
    }

    private fun Button.disable() {
        background.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
        isEnabled = false
    }

    private fun Button.enable() {
        background.colorFilter = null
        isEnabled = true
    }
}
