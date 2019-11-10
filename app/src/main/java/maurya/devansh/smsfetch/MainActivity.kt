package maurya.devansh.smsfetch

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import org.jetbrains.anko.toast


class MainActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val SMS_STORED = "sms_stored"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("maurya.devansh.smsfetch", Context.MODE_PRIVATE)

        getSmsReadPermission {
            val smsList = getSmsList()
            val enSmsList = arrayListOf<String>()

            identifySmsLanguage(smsList, enSmsList).observe(this, Observer {
                if (it == smsList.size) {
                    if (!prefs.getBoolean(SMS_STORED, false)) {
                        storeSmsInDB(enSmsList, prefs)
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

    private fun storeSmsInDB(smsList: List<String>, prefs: SharedPreferences) {

        val smsMap = mutableMapOf<String, List<String>>()
        smsMap["sms"] = smsList

        db.collection("sms").add(smsMap)
            .addOnSuccessListener {
                toast("${smsList.size} messages stored successfully")
                prefs.edit {
                    putBoolean(SMS_STORED, true)
                }
            }
            .addOnFailureListener {
                toast("Failed to store messages: $it")
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
}
