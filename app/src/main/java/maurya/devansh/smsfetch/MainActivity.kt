package maurya.devansh.smsfetch

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.GsonBuilder
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter


class MainActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private val SMS_STORED = "sms_stored"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs = getSharedPreferences("maurya.devansh.smsfetch", Context.MODE_PRIVATE)

        getSmsReadPermission {
            val smsList = getSmsList()
            numberTV.text = smsList.size.toString()
            descriptionTV.text = getString(R.string.sms_messages)

            val enSmsList = arrayListOf<String>()

            identifySmsLanguage(smsList, enSmsList).observe(this, Observer {
                if (it == smsList.size) {
                    numberEnTV.text = enSmsList.size.toString()
                    descriptionEnTV.text = getString(R.string.english_messages)

                    if (!prefs.getBoolean(SMS_STORED, false)) {
                        sendSmsButton.isEnabled = true
                        sendSmsButton.setOnClickListener {
                            sendSmsJson(enSmsList)
                            Toast.makeText(this@MainActivity, "Sending sms", Toast.LENGTH_SHORT).show()
                        }
                        Log.i("SMS", enSmsList.toString())
                    }
                    else
                        toast("All messages are already stored in the database!")
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
    private fun sendSmsJson(smsList: List<String>) {

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val fileName = "sms$androidId.json"
        val file = File(filesDir, fileName)
        GsonBuilder().setPrettyPrinting().create().toJson(smsList, FileWriter(file))
        val stream = FileInputStream(file)

        val storageRef = storage.reference
        val smsJsonRef = storageRef.child(fileName)
        smsJsonRef.putStream(stream)
            .addOnSuccessListener {
                Toast.makeText(this, "Upload success", Toast.LENGTH_SHORT).show()
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

    private fun updateUI(number: String, description: String) {
        numberTV.text = number
        descriptionTV.text = description
    }
}
