package ai.mlc.mlcchat

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import ai.mlc.mlcchat.ui.theme.MLCChatTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    var hasImage = false

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.v("pickImageLauncher", "Selected image uri: $it")
            chatState.messages.add(
                MessageData(
                    role = MessageRole.User,
                    text = "",
                    id = UUID.randomUUID(),
                    imageUri = it
                )
            )
        }
    }

    private var cameraImageUri: Uri? = null
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraImageUri != null) {
            Log.v("takePictureLauncher", "Camera image uri: $cameraImageUri")
            chatState.messages.add(
                MessageData(
                    role = MessageRole.User,
                    text = "",
                    id = UUID.randomUUID(),
                    imageUri = cameraImageUri
                )
            )
        }
    }

    lateinit var chatState: AppViewModel.ChatState

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatState = AppViewModel(this.application).ChatState()

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize()
            ) {
                MLCChatTheme {
                    NavView(this)
                }
            }
        }
    }

    fun pickImageFromGallery() {
        pickImageLauncher.launch("image/*")
    }

    fun takePhoto() {
        val contentValues = ContentValues().apply {
            val timeFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "IMG_${timeFormatter.format(Date())}.jpg"
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        cameraImageUri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        takePictureLauncher.launch(cameraImageUri)
    }
}
