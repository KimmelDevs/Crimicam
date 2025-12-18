package com.example.crimicam.presentation.main.Home.Monitor

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class VideoItem(
    val uri: Uri,
    val displayName: String,
    val duration: Long,
    val date: String,
    val size: Long,
    val dateAdded: Long
)

data class CapturedImageItem(
    val id: String,
    val fullFrameBase64: String?,
    val isRecognized: Boolean,
    val isCriminal: Boolean,
    val matchedPersonName: String?,
    val confidence: Float,
    val dangerLevel: String?,
    val timestamp: String
)

data class MonitorState(
    val videos: List<VideoItem> = emptyList(),
    val images: List<CapturedImageItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedVideo: VideoItem? = null,
    val selectedImage: CapturedImageItem? = null
)

class MonitorViewModel(
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "MonitorViewModel"
    }

    fun loadVideos() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val videos = getVideosFromMediaStore(context.contentResolver)

                _state.value = _state.value.copy(
                    videos = videos,
                    isLoading = false
                )

                Log.d(TAG, "Loaded ${videos.size} videos from CrimiCam folder")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading videos", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load videos: ${e.message}"
                )
            }
        }
    }

    fun loadImages() {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Please sign in to view images"
                )
                return@launch
            }

            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val querySnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("captured_faces")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val images = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        parseCapturedImage(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing document ${doc.id}", e)
                        null
                    }
                }

                _state.value = _state.value.copy(
                    images = images,
                    isLoading = false
                )

                Log.d(TAG, "Loaded ${images.size} images")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading images", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load images: ${e.message}"
                )
            }
        }
    }

    private fun getVideosFromMediaStore(contentResolver: ContentResolver): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        // Query for videos in the CrimiCam folder specifically
        val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Video.Media.DATA} LIKE ?"
        }

        val selectionArgs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            arrayOf("%DCIM/CrimiCam%")
        } else {
            arrayOf("%DCIM/CrimiCam%")
        }

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                Log.d(TAG, "Found ${cursor.count} videos in CrimiCam folder")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    val size = cursor.getLong(sizeColumn)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    val date = dateFormat.format(Date(dateAdded * 1000))

                    videos.add(
                        VideoItem(
                            uri = uri,
                            displayName = name,
                            duration = duration,
                            date = date,
                            size = size,
                            dateAdded = dateAdded
                        )
                    )

                    Log.d(TAG, "Video found: $name, Duration: $duration ms, Size: ${size / 1024}KB")
                }
            }

            if (videos.isEmpty()) {
                Log.w(TAG, "No videos found in DCIM/CrimiCam folder")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error querying videos from MediaStore", e)
            e.printStackTrace()
        }

        return videos
    }

    private fun parseCapturedImage(id: String, data: Map<String, Any>): CapturedImageItem {
        val timestamp = data["timestamp"] as? com.google.firebase.Timestamp
        val timestampString = timestamp?.toDate()?.let { date ->
            SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(date)
        } ?: "Unknown time"

        val fullFrameBase64 = data["full_frame_image_base64"] as? String
        val isRecognized = data["is_recognized"] as? Boolean ?: false
        val isCriminal = data["is_criminal"] as? Boolean ?: false
        val matchedPersonName = data["matched_person_name"] as? String
        val confidence = (data["confidence"] as? Number)?.toFloat() ?: 0f
        val dangerLevel = data["danger_level"] as? String

        return CapturedImageItem(
            id = id,
            fullFrameBase64 = fullFrameBase64,
            isRecognized = isRecognized,
            isCriminal = isCriminal,
            matchedPersonName = matchedPersonName,
            confidence = confidence,
            dangerLevel = dangerLevel,
            timestamp = timestampString
        )
    }

    fun selectVideo(video: VideoItem) {
        _state.value = _state.value.copy(selectedVideo = video)
    }

    fun selectImage(image: CapturedImageItem) {
        _state.value = _state.value.copy(selectedImage = image)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(
            selectedVideo = null,
            selectedImage = null
        )
    }
}