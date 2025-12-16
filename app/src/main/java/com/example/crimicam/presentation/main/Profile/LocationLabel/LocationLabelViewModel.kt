package com.example.crimicam.presentation.main.Profile.LocationLabel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LabelItem(
    val id: String = "",
    val name: String = ""
)

class LocationLabelViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _labels = MutableStateFlow<List<LabelItem>>(emptyList())
    val labels: StateFlow<List<LabelItem>> = _labels

    init {
        loadLabels()
    }

    fun loadLabels() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(userId)
            .collection("labelled_location")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val labelList = snapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("name")
                        if (name != null) LabelItem(id = doc.id, name = name) else null
                    }
                    _labels.value = labelList
                }
            }
    }

    fun addLabel(labelName: String) {
        val userId = auth.currentUser?.uid ?: return
        val labelData = mapOf("name" to labelName)

        viewModelScope.launch {
            firestore.collection("users")
                .document(userId)
                .collection("labelled_location")
                .add(labelData)
        }
    }

    fun updateLabel(labelId: String, newName: String) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(userId)
            .collection("labelled_location")
            .document(labelId)
            .update("name", newName)
    }

    fun deleteLabel(labelId: String) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(userId)
            .collection("labelled_location")
            .document(labelId)
            .delete()
    }
}
