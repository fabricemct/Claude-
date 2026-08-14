package com.whatschat.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatschat.app.data.model.User
import com.whatschat.app.data.repository.AuthRepository
import com.whatschat.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        val uid = authRepository.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                userRepository.observeUser(uid).collect { _user.value = it }
            }
        }
    }

    fun saveProfile(name: String, status: String, photoUri: Uri?) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _saving.value = true
            _errorMessage.value = null
            val result = userRepository.updateProfile(uid, name, status, photoUri)
            result.onFailure { _errorMessage.value = it.message ?: "Failed to save profile." }
            _saving.value = false
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun signOut() = authRepository.signOut()
}
