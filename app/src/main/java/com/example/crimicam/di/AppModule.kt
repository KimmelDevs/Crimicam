package com.example.crimicam.di

import android.content.Context
import com.example.crimicam.facerecognitionnetface.models.data.ImagesVectorDB
import com.example.crimicam.facerecognitionnetface.models.data.PersonDB
import com.example.crimicam.facerecognitionnetface.models.domain.ImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.PersonUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.MediapipeFaceDetector
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.FaceNet // Add this import
import com.example.crimicam.facerecognitionnetface.models.domain.face_detection.FaceSpoofDetector
import com.example.crimicam.presentation.main.KnownPeople.KnownPeopleViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // Database
    single { PersonDB() }
    single { ImagesVectorDB() }

    // Face Detection & Recognition Dependencies
    single {
        MediapipeFaceDetector(
            context = androidContext() // or get<Context>() if you prefer
        )
    }

    // Add these missing dependencies that ImageVectorUseCase requires
    single {
        FaceNet(
            context = androidContext()
        )
    }

    single {
        FaceSpoofDetector(
            context = androidContext()
        )
    }

    // Use Cases
    single { PersonUseCase(get()) }
    single {
        ImageVectorUseCase(
            mediapipeFaceDetector = get(),
            faceSpoofDetector = get(),
            imagesVectorDB = get(),
            faceNet = get()
        )
    }

    // ViewModel
    viewModel {
        KnownPeopleViewModel(
            personUseCase = get(),
            imageVectorUseCase = get()
        )
    }
}