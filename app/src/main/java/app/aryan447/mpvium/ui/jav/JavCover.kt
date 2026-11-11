package app.aryan447.mpvium.ui.jav

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.aryan447.mpvium.domain.jav.JavLibraryItem
import app.aryan447.mpvium.ui.streaming.components.StreamingImage

/**
 * JAV artwork frame.
 *
 * Source covers are landscape (DMM package images are 3:2 or 4:3) while the old library
 * frame was 2:3 portrait, so [ContentScale.Crop] cut the sides off. The list uses the image's
 * own ratio and [showBackdrop] = false; grid and detail use a landscape frame with the full
 * image on top of a dimmed copy of itself so 4:3 and 16:9 artwork still fits without empty
 * bars.
 */
@Composable
internal fun JavCover(
  item: JavLibraryItem,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(14.dp),
  contentScale: ContentScale = ContentScale.Fit,
  showBackdrop: Boolean = true,
  onImageLoaded: ((Bitmap) -> Unit)? = null,
) {
  Box(
    modifier = modifier
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    if (showBackdrop) {
      StreamingImage(
        url = item.coverUrl,
        fallbackVideo = item.video,
        isSeries = false,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .blur(26.dp),
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
      )
    }

    StreamingImage(
      url = item.coverUrl,
      fallbackVideo = item.video,
      isSeries = false,
      contentScale = contentScale,
      onImageLoaded = onImageLoaded,
      modifier = Modifier.fillMaxSize(),
    )
  }
}
