package com.example.crimicam.di

import com.example.crimicam.auth.UserSessionManager
import com.example.crimicam.facerecognitionnetface.models.data.ImagesVectorDB
import com.example.crimicam.facerecognitionnetface.models.data.PersonDB
import com.example.crimicam.facerecognitionnetface.models.domain.ImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.PersonUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.FaceNet
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.MediapipeFaceDetector
import com.example.crimicam.facerecognitionnetface.models.domain.face_detection.FaceSpoofDetector
import com.example.crimicam.presentation.main.Home.Camera.CameraViewModel
import com.example.crimicam.presentation.main.KnownPeople.KnownPeopleViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // User Session Manager (Singleton)
    single { UserSessionManager.getInstance() }

    // Provide current user ID
    // This will throw exception if user is not logged in
    factory { get<UserSessionManager>().getCurrentUserId() }

    // Database layers - inject user ID dynamically
    factory {
        PersonDB(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    factory {
        ImagesVectorDB(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    // Face Detection & Recognition Dependencies (Singletons)
    single {
        MediapipeFaceDetector(
            context = androidContext()
        )
    }

    single {
        FaceNet(
            context = androidContext(),
            useGpu = true,  // Enable GPU acceleration if available
            useXNNPack = true  // Enable XNNPACK delegate
        )
    }

    single {
        FaceSpoofDetector(
            context = androidContext(),
            useGpu = false,  // Set based on your preference
            useXNNPack = true,
            useNNAPI = false
        )
    }

    // Use Cases - inject user ID dynamically
    factory {
        PersonUseCase(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    single {
        ImageVectorUseCase(
            mediapipeFaceDetector = get(),
            faceSpoofDetector = get(),
            imagesVectorDB = get(),
            faceNet = get()
        )
    }

    // ViewModels
    viewModel {
        KnownPeopleViewModel(
            personUseCase = get(),
            imageVectorUseCase = get()
        )
    }

    viewModel {
        CameraViewModel(
            personUseCase = get(),
            imageVectorUseCase = get()
        )
    }
}

/**
 * IMPORTANT NOTES:
 *
 * 1. User MUST be logged in before accessing camera or known people screens
 * 2. PersonDB and ImagesVectorDB are now factories (not singletons) because they
 *    depend on current user ID which can change
 * 3. Add authentication check in your navigation:
 *
 *    if (userSessionManager.isUserLoggedIn()) {
 *        // Navigate to camera/known people
 *    } else {
 *        // Navigate to login
 *    }
 *
 * 4. New Firestore structure:
 *    users/{userId}/persons/{personId}
 *    users/{userId}/face_images/{imageId}
 */