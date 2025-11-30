package com.example.crimicam.di

import com.example.crimicam.auth.UserSessionManager
import com.example.crimicam.data.repository.CriminalsRepository
import com.example.crimicam.facerecognitionnetface.models.data.CriminalDB
import com.example.crimicam.facerecognitionnetface.models.data.CriminalImagesVectorDB
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
    // USER SESSION & AUTH
    // ============================================
    single { UserSessionManager.getInstance() }

    // ============================================
    // USER-SPECIFIC DATABASE LAYERS (Subcollections)
    // ============================================

    // PersonDB and ImagesVectorDB are user-specific (for known people)
    factory {
        PersonDB(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    factory {
        ImagesVectorDB(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    // ============================================
    // GLOBAL DATABASE LAYERS (For Criminals - Shared)
    // ============================================

    // CriminalDB and CriminalImagesVectorDB are GLOBAL (shared across all officers)
    single { CriminalDB() }
    single { CriminalImagesVectorDB() }

    // Criminals Repository (optional - if you want to keep your repository pattern)
    single { CriminalsRepository() }

    // ============================================
    // FACE DETECTION & RECOGNITION (Shared Singletons)
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
    // USE CASES
    // ============================================

    // PersonUseCase - user-specific (for known people)
    factory {
        PersonUseCase(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    // ImageVectorUseCase - user-specific (for known people face recognition)
    single {
        ImageVectorUseCase(
            mediapipeFaceDetector = get(),
            faceSpoofDetector = get(),
            imagesVectorDB = get(),
            faceNet = get()
        )
    }

    // CriminalUseCase - GLOBAL (for criminal database management)
    single {
        CriminalUseCase(
            criminalDB = get(),
            criminalImagesVectorDB = get(),
            faceNet = get(),
            mediapipeFaceDetector = get()
        )
    }

    // ============================================
    // VIEW MODELS
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