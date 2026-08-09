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
            userRepository.updateProfile(uid, name, status, photoUri)
            _saving.value = false
        }
    }

    fun signOut() = authRepository.signOut()
}
