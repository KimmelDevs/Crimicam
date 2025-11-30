package com.example.crimicam.di

import com.example.crimicam.auth.UserSessionManager
import com.example.crimicam.facerecognitionnetface.models.data.CriminalDB
import com.example.crimicam.facerecognitionnetface.models.data.ImagesVectorDB
import com.example.crimicam.facerecognitionnetface.models.data.PersonDB
import com.example.crimicam.facerecognitionnetface.models.domain.CriminalUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.ImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.PersonUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.FaceNet
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.MediapipeFaceDetector
import com.example.crimicam.facerecognitionnetface.models.domain.face_detection.FaceSpoofDetector
import com.example.crimicam.presentation.main.Admin.AdminViewModel
import com.example.crimicam.presentation.main.Home.Camera.CameraViewModel
import com.example.crimicam.presentation.main.KnownPeople.KnownPeopleViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // ============================================
    // User Session Management
    // ============================================

    single { UserSessionManager.getInstance() }

    // Provide current user ID (throws exception if not logged in)
    factory { get<UserSessionManager>().getCurrentUserId() }

    // ============================================
    // Database Layers
    // ============================================

    // User-specific collections (subcollections under users/{userId})
    factory {
        PersonDB(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    factory {
        ImagesVectorDB(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    // Global collection (top-level collection, not user-specific)
    single { CriminalDB() }
    single { CriminalImagesVectorDB() }

    // ============================================
    // Face Detection & Recognition (Singletons)
    // ============================================

    single {
        MediapipeFaceDetector(
            context = androidContext()
        )
    }

    single {
        FaceNet(
            context = androidContext(),
            useGpu = true,
            useXNNPack = true
        )
    }

    single {
        FaceSpoofDetector(
            context = androidContext(),
            useGpu = false,
            useXNNPack = true,
            useNNAPI = false
        )
    }

    // ============================================
    // Use Cases
    // ============================================

    // User-specific use cases
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

    // Global criminal use case (not user-specific)
    single {
        CriminalUseCase(
            criminalDB = get(),
            criminalImagesVectorDB = get(),
            faceNet = get(),
            mediapipeFaceDetector = get()
        )
    }

    // ============================================
    // ViewModels
    // ============================================

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

    viewModel {
        AdminViewModel(
            criminalUseCase = get()
        )
    }
}

/**
 * FIRESTORE STRUCTURE:
 *
 * 1. USER-SPECIFIC DATA (Subcollections):
 *    users/{userId}/
 *      ├── persons/{personId}           // Known people for this user
 *      └── face_images/{imageId}        // Face embeddings for this user's people
 *
 * 2. GLOBAL DATA (Top-level Collections):
 *    criminals/{criminalId}             // Criminal records (accessible by all)
 *    criminal_face_images/{imageId}     // Face embeddings for criminals (accessible by all)
 *
 * IMPORTANT NOTES:
 *
 * 1. Authentication Required:
 *    - User MUST be logged in to access user-specific features (Known People, Camera)
 *    - Check authentication before navigation:
 *      if (userSessionManager.isUserLoggedIn()) {
 *          // Navigate to protected screens
 *      } else {
 *          // Navigate to login
 *      }
 *
 * 2. Criminal Database:
 *    - Stored in top-level collection (not user-specific)
 *    - All users can view/search criminals
 *    - Only admins should be able to add/edit criminals (implement role-based access)
 *
 * 3. Face Recognition:
 *    - Camera searches both user's known people AND criminals
 *    - User-specific face embeddings are isolated per user
 *    - Criminal face embeddings are shared across all users
 */