package com.cellshield.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

object ReviewRepository {
    private val firestore = FirebaseFirestore.getInstance()

    fun addReview(review: Review, callback: (Boolean, String?) -> Unit) {
        firestore.collection("reviews")
            .add(review)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) callback(true, null) else callback(false, task.exception?.message)
            }
    }

    /** Listens to reviews collection and returns ordered list via callback. */
    fun listenForReviews(onChange: (List<Review>) -> Unit) {
        firestore.collection("reviews")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.let {
                    val list = it.toObjects(Review::class.java).mapIndexed { index, r ->
                        // keep firestore doc id (useful for update/delete)
                        r.copy(id = it.documents.getOrNull(index)?.id ?: r.id)
                    }
                    onChange(list)
                }
            }
    }
}
