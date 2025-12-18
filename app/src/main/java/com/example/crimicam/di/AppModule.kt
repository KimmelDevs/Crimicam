package com.example.crimicam.di

import com.example.crimicam.auth.UserSessionManager
import com.example.crimicam.data.repository.WebRTCSignalingRepository
import com.example.crimicam.facerecognitionnetface.models.data.CriminalDB
import com.example.crimicam.facerecognitionnetface.models.data.CriminalImagesVectorDB
import com.example.crimicam.facerecognitionnetface.models.data.ImagesVectorDB
import com.example.crimicam.facerecognitionnetface.models.domain.CriminalImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.CriminalUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.ImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.PersonUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.FaceNet
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.MediapipeFaceDetector
import com.example.crimicam.facerecognitionnetface.models.domain.face_detection.FaceSpoofDetector
import com.example.crimicam.presentation.main.Admin.AdminViewModel
import com.example.crimicam.presentation.main.Home.ActivityDetail.ActivityDetailViewModel
import com.example.crimicam.presentation.main.Home.Camera.CameraViewModel
import com.example.crimicam.presentation.main.Home.Monitor.MonitorViewModel
import com.example.crimicam.presentation.main.Home.Monitor.StreamViewerViewModel
import com.example.crimicam.presentation.main.KnownPeople.KnownPeopleViewModel
import com.example.crimicam.presentation.main.Map.MapViewModel
import com.example.crimicam.webrtc.WebRTCManager
import com.example.crimicam.webrtc.WebRTCSignalingService
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

    // PersonUseCase - user-specific (for known people)
    factory {
        PersonUseCase(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    // ImagesVectorDB - user-specific (for known people face images)
    factory {
        ImagesVectorDB(currentUserId = get<UserSessionManager>().getCurrentUserId())
    }

    // ============================================
    // GLOBAL DATABASE LAYERS (For Criminals - Shared Across All Users)
    // ============================================

    // CriminalDB - GLOBAL (criminal records database)
    single {
        CriminalDB()  // Uses global collection "criminals"
    }

    // CriminalImagesVectorDB - GLOBAL (criminal face images)
    single {
        CriminalImagesVectorDB()  // Uses global collection "criminal_face_images"
    }

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

    // ImageVectorUseCase - user-specific (for known people face recognition)
    factory {
        ImageVectorUseCase(
            mediapipeFaceDetector = get(),
            faceSpoofDetector = get(),
            imagesVectorDB = get(),
            faceNet = get()
        )
    }

    // CriminalImageVectorUseCase - GLOBAL (for criminal face recognition)
    single {
        CriminalImageVectorUseCase(
            mediapipeFaceDetector = get(),
            criminalImagesVectorDB = get(),
            faceNet = get(),
            faceSpoofDetector = get()
        )
    }

    // CriminalUseCase - GLOBAL (criminal database management)
    single {
        CriminalUseCase(
            criminalImagesVectorDB = get(),
            faceNet = get(),
            mediapipeFaceDetector = get()
        )
    }

    // ============================================
    // WEBRTC INFRASTRUCTURE
    // ============================================

    // WebRTC Signaling Repository - Singleton for shared access
    single { WebRTCSignalingRepository() }

    // WebRTC Signaling Service - Singleton for shared access
    single { WebRTCSignalingService() }

    // WebRTC Manager - Factory (creates new instance per injection)
    factory { WebRTCManager(androidContext()) }

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
            imageVectorUseCase = get(),
            criminalImageVectorUseCase = get(),
            context = androidContext()
        )
    }

    viewModel {
        MonitorViewModel(
            context = androidContext()
        )
    }

    viewModel {
        StreamViewerViewModel(
            repository = get(),
            signalingService = get()
        )
    }

    viewModel {
        AdminViewModel(
            criminalUseCase = get(),
            criminalImageVectorUseCase = get()
        )
    }

    viewModel {
        MapViewModel(context = androidContext())
    }

    viewModel {
        ActivityDetailViewModel()
    }
}