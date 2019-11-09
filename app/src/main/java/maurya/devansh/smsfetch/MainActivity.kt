package maurya.devansh.smsfetch

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity() {

    val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted() {
                toast("Permission granted")
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

}
