package com.example.crimicam.facerecognitionnetface


// Logs message using log_textview present in activity_main.xml
class Logger {

    companion object {

        fun log( message : String ) {
            MainActivity.setMessage(  MainActivity.logTextView.text.toString() + "\n" + ">> $message" )
            // To scroll to the last message
            // See this SO answer -> https://stackoverflow.com/a/37806544/10878733
            while ( MainActivity.logTextView.canScrollVertically(1) ) {
                MainActivity.logTextView.scrollBy(0, 10)
            }
        }

    }

}