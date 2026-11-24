package com.prabhu.wallpaperstudio.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.prabhu.wallpaperstudio.render.PhotoMath
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Re-encodes a video so it matches this phone: cropped to the screen shape,
 * scaled to the chosen quality, trimmed, frame rate capped and (optionally) muted.
 * A smaller, screen-shaped file plays smoother and uses less battery.
 */
object VideoOptimizer {

    data class Options(
        val uri: Uri,
        val startMs: Long,
        val endMs: Long,
        /** Short side in pixels, 0 = full screen resolution. */
        val shortSide: Int,
        /** Target frame rate, 0 = keep original. */
        val fps: Int,
        /** true = crop to fill the screen, false = fit inside it. */
        val fill: Boolean,
        /** Keep the sound track. */
        val sound: Boolean = false,
    )

    /** Must be called from the main thread. */
    @OptIn(UnstableApi::class)
    suspend fun optimize(context: Context, o: Options, onProgress: (Int) -> Unit): File =
        suspendCancellableCoroutine { cont ->
            val screen = PhotoMath.screenSize(context)
            val short = if (o.shortSide <= 0) screen.x else min(o.shortSide, screen.x)
            val long = (short * screen.y / screen.x.toFloat()).roundToInt()
            val w = short and 1.inv()
            val h = long and 1.inv()

            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(o.startMs)
                .setEndPositionMs(o.endMs)
                .build()
            val item = MediaItem.Builder().setUri(o.uri).setClippingConfiguration(clipping).build()

            val videoEffects = ArrayList<Effect>()
            if (o.fps > 0) videoEffects += FrameDropEffect.createDefaultFrameDropEffect(o.fps.toFloat())
            videoEffects += Presentation.createForWidthAndHeight(
                w, h,
                if (o.fill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT,
            )

            val edited = EditedMediaItem.Builder(item)
                .setRemoveAudio(!o.sound)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
            val composition = Composition.Builder(EditedMediaItemSequence(edited))
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()

            val dir = File(context.filesDir, "video").apply { mkdirs() }
            val out = File(dir, "wallpaper_${System.currentTimeMillis()}.mp4")
            val handler = Handler(Looper.getMainLooper())
            var finished = false

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished = true
                        if (cont.isActive) cont.resume(out)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        finished = true
                        out.delete()
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            val holder = ProgressHolder()
            val poll = object : Runnable {
                override fun run() {
                    if (finished) return
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress)
                    }
                    handler.postDelayed(this, 250)
                }
            }

            cont.invokeOnCancellation {
                handler.post {
                    finished = true
                    transformer.cancel()
                    out.delete()
                }
            }

            transformer.start(composition, out.absolutePath)
            handler.post(poll)
        }
}
