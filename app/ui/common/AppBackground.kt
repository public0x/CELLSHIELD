// You can create a new file like `ui/common/AppBackground.kt` for this
package com.cellshield.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.cellshield.app.R // Make sure to import your R file

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // You need to add a background image to your `res/drawable` folder
        // Name it `app_background.png` or similar
        Image(
            painter = painterResource(id = R.drawable.app_background), // CHANGE: Use your image
            contentDescription = "App Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        content()
    }
}
