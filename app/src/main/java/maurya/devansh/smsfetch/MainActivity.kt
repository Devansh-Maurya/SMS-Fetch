package maurya.devansh.smsfetch

import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity() {

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getSmsReadPermission {
            val smsList = getSmsList()
            storeSmsInDB(smsList)
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
                val body = cursor.getString(cursor.getColumnIndexOrThrow("body")).toString()
                //val address = cursor.getShort(cursor.getColumnIndexOrThrow("address")).toString()
                smsList.add(body)

                cursor.moveToNext()
            }
        }
        cursor?.close()

        return smsList
    }

    private fun storeSmsInDB(smsList: List<String>) {

        val smsMap = mutableMapOf<String, List<String>>()
        smsMap["sms"] = smsList

        db.collection("sms").add(smsMap)
            .addOnSuccessListener {
                toast("Messages stored successfully")
            }
            .addOnFailureListener {
                toast("Failed to store SMS: $it")
            }
    }
}
