package com.example.ericloop

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import com.example.ericloop.data.DataRepository
import com.example.ericloop.sync.GithubSync
import com.example.ericloop.ui.EricLoopApp

class LoopViewModel(application: Application) : AndroidViewModel(application) {
    val repository = DataRepository(application)
    val sync = GithubSync(application, repository)
}

class MainActivity : ComponentActivity() {
    private val model: LoopViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EricLoopApp(model.repository, model.sync) }
    }
}
