package com.example.crimicam.di

import com.example.crimicam.facerecognitionnetface.models.data.ImagesVectorDB
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

    // Database layers
    single { ImagesVectorDB() }

    // Face Detection & Recognition Dependencies
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

    // Use Cases
    single {
        PersonUseCase()  // PersonUseCase doesn't take parameters based on your code
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