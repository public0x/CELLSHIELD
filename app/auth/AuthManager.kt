package com.cellshield.app.auth

import android.net.Uri
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UserProfile(
    val name: String = "",
    val phone: String = "",
    val telco: String = "",
    val photoUrl: String? = null,
    val consentAccepted: Boolean = false
)

object AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private var userProfileCache: UserProfile? = null
    private val _userProfileState = MutableStateFlow<UserProfile?>(null)
    val userProfileState: StateFlow<UserProfile?> = _userProfileState

    fun getAuthInstance(): FirebaseAuth {
        return auth
    }

    // --- Basic getters ---
    fun isLoggedIn(): Boolean = auth.currentUser != null
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun getCurrentUserId(): String? = getCurrentUser()?.uid
    fun getCurrentUserEmail(): String? = getCurrentUser()?.email
    fun getCurrentUserName(): String? = getCurrentUser()?.displayName
    fun getCurrentUserPhoto(): String? = getCurrentUser()?.photoUrl?.toString()
    fun getCurrentUserPhone(): String? = userProfileCache?.phone
    fun getCurrentUserTelco(): String? = userProfileCache?.telco

    // --- Authentication flows ---
    fun loginWithEmail(email: String, password: String, callback: (success: Boolean, error: String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        fetchUserProfile { callback(true, null) }
                    } else {
                        auth.signOut()
                        callback(false, "Please verify your email before logging in.")
                    }
                } else {
                    callback(false, task.exception?.message ?: "Login failed.")
                }
            }
    }

    fun registerWithEmail(email: String, password: String, callback: (success: Boolean, error: String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { emailTask ->
                            if (emailTask.isSuccessful) {
                                callback(true, null)
                            } else {
                                callback(false, emailTask.exception?.message ?: "Failed to send verification email.")
                            }
                        }
                } else {
                    callback(false, task.exception?.message ?: "Registration failed.")
                }
            }
    }

    fun resendVerificationEmail(callback: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        if (user != null && !user.isEmailVerified) {
            user.sendEmailVerification()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) callback(true, null)
                    else callback(false, task.exception?.message ?: "Failed to resend email.")
                }
        } else {
            callback(false, "User not logged in or already verified.")
        }
    }

    fun firebaseAuthWithGoogle(idToken: String, callback: (isNewUser: Boolean, error: String?) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val authResult: AuthResult? = task.result
                    val isNewUser = authResult?.additionalUserInfo?.isNewUser ?: false
                    if (!isNewUser) {
                        fetchUserProfile { callback(isNewUser, null) }
                    } else {
                        callback(isNewUser, null)
                    }
                } else {
                    callback(false, task.exception?.message ?: "Google Sign-In failed.")
                }
            }
    }

    fun linkPhoneCredential(credential: PhoneAuthCredential, callback: (success: Boolean, error: String?) -> Unit) {
        val user = getCurrentUser()
        if (user == null) {
            callback(false, "No user is logged in to link.")
            return
        }

        user.linkWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(true, null)
                } else {
                    callback(false, task.exception?.message ?: "Failed to link phone number.")
                }
            }
    }

    // --- 👇 ADD THIS NEW FUNCTION ---
    /**
     * Updates the current user's phone number using a verified credential.
     * This is a security-sensitive operation and may require recent re-authentication.
     */
    fun updatePhoneNumber(credential: PhoneAuthCredential, callback: (success: Boolean, error: String?) -> Unit) {
        val user = getCurrentUser()
        if (user == null) {
            callback(false, "No user is logged in.")
            return
        }

        user.updatePhoneNumber(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(true, null)
                } else {
                    // This can fail if the number is in use or if login is not recent
                    callback(false, task.exception?.message ?: "Failed to update phone number.")
                }
            }
    }
    // --- 👆 END OF NEW FUNCTION ---

    fun logout(onComplete: () -> Unit) {
        auth.signOut()
        userProfileCache = null
        _userProfileState.value = null
        onComplete()
    }

    // --- Firestore profile management ---
    fun fetchUserProfile(onComplete: (() -> Unit)? = null) {
        val userId = getCurrentUserId()
        if (userId == null) {
            onComplete?.invoke()
            return
        }
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val profile = document.toObject(UserProfile::class.java) ?: UserProfile()
                    userProfileCache = profile
                    _userProfileState.value = profile
                } else {
                    userProfileCache = null
                    _userProfileState.value = null
                }
                onComplete?.invoke()
            }
            .addOnFailureListener { onComplete?.invoke() }
    }

    fun updateUserAccount(
        name: String,
        phone: String,
        telco: String,
        photoUrl: String? = null,
        callback: (success: Boolean, error: String?) -> Unit
    ) {
        val userId = getCurrentUserId()
        if (userId == null) {
            callback(false, "No user is currently logged in.")
            return
        }

        // Get the current consent flag to make sure we don't overwrite it
        val currentConsent = userProfileCache?.consentAccepted ?: false

        val userProfile = UserProfile(
            name = name,
            phone = phone,
            telco = telco,
            photoUrl = photoUrl ?: userProfileCache?.photoUrl, // Keep existing photo if new one isn't provided
            consentAccepted = currentConsent
        )
        firestore.collection("users").document(userId)
            .set(userProfile)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    userProfileCache = userProfile
                    _userProfileState.value = userProfile
                    callback(true, null)
                } else {
                    callback(false, task.exception?.message ?: "Failed to save user data.")
                }
            }
    }

    fun acceptConsent(callback: (Boolean) -> Unit) {
        val uid = getCurrentUserId() ?: return callback(false)
        val profile = userProfileCache ?: return callback(false)
        val updated = profile.copy(consentAccepted = true)
        firestore.collection("users").document(uid)
            .set(updated)
            .addOnSuccessListener {
                userProfileCache = updated
                _userProfileState.value = updated
                callback(true)
            }
            .addOnFailureListener { callback(false) }
    }

    fun resetPassword(email: String, callback: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) callback(true, null)
                else callback(false, task.exception?.message)
            }
    }

    fun deleteAccount(callback: (Boolean, String?) -> Unit) {
        val userId = getCurrentUserId()
        val currentUser = getCurrentUser()
        if (userId == null || currentUser == null) {
            callback(false, "No user is currently logged in.")
            return
        }
        firestore.collection("users").document(userId)
            .delete()
            .addOnCompleteListener { deleteDocTask ->
                if (deleteDocTask.isSuccessful) {
                    currentUser.delete()
                        .addOnCompleteListener { deleteUserTask ->
                            if (deleteUserTask.isSuccessful) {
                                userProfileCache = null
                                _userProfileState.value = null
                                callback(true, null)
                            } else {
                                callback(false, deleteUserTask.exception?.message ?: "Failed to delete auth user.")
                            }
                        }
                } else {
                    callback(false, deleteDocTask.exception?.message ?: "Failed to delete user data.")
                }
            }
    }

    fun uploadProfilePhoto(uri: Uri, callback: (Boolean, String?, String?) -> Unit) {
        // ... (This function is unchanged, but left here for completeness) ...
        val uid = getCurrentUserId()
        if (uid == null) {
            callback(false, null, "No user logged in")
            return
        }
        val ref = storage.reference.child("profile_photos/$uid.jpg")
        ref.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                val profile = userProfileCache?.copy(photoUrl = downloadUri.toString())
                if (profile != null) {
                    firestore.collection("users").document(uid).set(profile)
                        .addOnSuccessListener {
                            userProfileCache = profile
                            _userProfileState.value = profile
                            callback(true, downloadUri.toString(), null)
                        }
                        .addOnFailureListener { e -> callback(false, null, e.message) }
                } else {
                    // This case might happen if profile is not fetched yet
                    // Still, we can just update the photoUrl in a new profile object
                    val newProfile = UserProfile(
                        name = getCurrentUserName() ?: "",
                        phone = getCurrentUserPhone() ?: "",
                        telco = getCurrentUserTelco() ?: "",
                        photoUrl = downloadUri.toString()
                    )
                    firestore.collection("users").document(uid).set(newProfile, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            userProfileCache = newProfile
                            _userProfileState.value = newProfile
                            callback(true, downloadUri.toString(), null)
                        }
                        .addOnFailureListener { e -> callback(false, null, e.message) }
                }
            }
            .addOnFailureListener { e -> callback(false, null, e.message) }
    }
}