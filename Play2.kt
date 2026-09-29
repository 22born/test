class MainActivity : ComponentActivity() {

    private val readAloudViewModel: ReadAloudViewModel by viewModels {
        ReadAloudViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val transcript =
            """
            The quick brown fox jumps over the lazy dog.
            Jetpack Compose calculates the actual visual lines.
            Pause and rotate the device while this is being spoken.
            """.trimIndent()

        setContent {
            MaterialTheme {
                TranscriptScreen(
                    transcript = transcript,
                    viewModel = readAloudViewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
    }
}



package com.example.readaloud

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID


// =============================================================================
// Speech abstraction
// =============================================================================

interface SpeechEngine {

    fun speak(
        text: String,
        utteranceId: String,
        listener: Listener,
    )

    fun stop()

    fun shutdown()

    interface Listener {

        fun onStart(utteranceId: String)

        fun onRangeStart(
            utteranceId: String,
            start: Int,
            endExclusive: Int,
        )

        fun onDone(utteranceId: String)

        fun onError(utteranceId: String)
    }
}


// =============================================================================
// Android TTS implementation
// =============================================================================

class AndroidSpeechEngine(
    context: Context,
) : SpeechEngine {

    private val lock = Any()

    private var initialized = false
    private var listener: SpeechEngine.Listener? = null

    private val tts: TextToSpeech

    init {
        tts = TextToSpeech(
            context.applicationContext,
        ) { status ->
            synchronized(lock) {
                initialized = status == TextToSpeech.SUCCESS

                if (initialized) {
                    tts.language = Locale.getDefault()
                }
            }
        }

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String) {
                    currentListener()?.onStart(utteranceId)
                }

                override fun onRangeStart(
                    utteranceId: String,
                    start: Int,
                    end: Int,
                    frame: Int,
                ) {
                    currentListener()?.onRangeStart(
                        utteranceId,
                        start,
                        end,
                    )
                }

                override fun onDone(utteranceId: String) {
                    currentListener()?.onDone(utteranceId)
                }

                @Deprecated("Deprecated Android callback")
                override fun onError(utteranceId: String) {
                    currentListener()?.onError(utteranceId)
                }

                override fun onError(
                    utteranceId: String,
                    errorCode: Int,
                ) {
                    currentListener()?.onError(utteranceId)
                }
            },
        )
    }

    private fun currentListener(): SpeechEngine.Listener? =
        synchronized(lock) {
            listener
        }

    override fun speak(
        text: String,
        utteranceId: String,
        listener: SpeechEngine.Listener,
    ) {
        val ready = synchronized(lock) {
            this.listener = listener
            initialized
        }

        if (!ready) {
            listener.onError(utteranceId)
            return
        }

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
    }

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() {
        synchronized(lock) {
            listener = null
        }

        tts.stop()
        tts.shutdown()
    }
}


// =============================================================================
// State
// =============================================================================

fun interface UtteranceIdGenerator {
    fun next(): String
}

enum class PlaybackState {
    STOPPED,
    PLAYING,
    PAUSED,
}

data class ReadAloudState(
    val transcript: String,
    val playbackState: PlaybackState = PlaybackState.STOPPED,
    val activeRange: IntRange? = null,
    val resumeOffset: Int = 0,
)

private data class PlaybackSession(
    val utteranceId: String,

    /**
     * Offset in the COMPLETE transcript corresponding
     * to index 0 of the string sent to SpeechEngine.
     */
    val baseOffset: Int,

    /**
     * Exact string supplied to SpeechEngine.speak().
     */
    val spokenText: String,
)


// =============================================================================
// ViewModel
// =============================================================================

class ReadAloudViewModel(
    application: Application,
    private val speechEngine: SpeechEngine,
    private val idGenerator: UtteranceIdGenerator =
        UtteranceIdGenerator {
            UUID.randomUUID().toString()
        },
) : AndroidViewModel(application) {

    private val lock = Any()

    private val _state =
        MutableStateFlow(
            ReadAloudState(
                transcript = "",
            ),
        )

    val state: StateFlow<ReadAloudState> =
        _state.asStateFlow()

    private var currentSession: PlaybackSession? = null

    private var cleared = false


    // -------------------------------------------------------------------------
    // Transcript
    // -------------------------------------------------------------------------

    fun setTranscript(transcript: String) {

        var shouldStop = false

        synchronized(lock) {

            if (cleared) return

            if (_state.value.transcript == transcript) {
                return
            }

            shouldStop =
                currentSession != null ||
                    _state.value.playbackState != PlaybackState.STOPPED

            currentSession = null

            _state.value =
                ReadAloudState(
                    transcript = transcript,
                    playbackState = PlaybackState.STOPPED,
                    activeRange = null,
                    resumeOffset = 0,
                )
        }

        if (shouldStop) {
            speechEngine.stop()
        }
    }


    // -------------------------------------------------------------------------
    // Play / resume
    // -------------------------------------------------------------------------

    fun play() {

        val request: PlaybackSession

        synchronized(lock) {

            if (cleared) return

            val state = _state.value

            if (state.transcript.isEmpty()) {
                return
            }

            if (state.playbackState == PlaybackState.PLAYING) {
                return
            }

            val baseOffset =
                if (state.playbackState == PlaybackState.PAUSED) {
                    state.resumeOffset.coerceIn(
                        0,
                        state.transcript.length,
                    )
                } else {
                    0
                }

            /*
             * A non-empty transcript can only produce an empty
             * suffix when resumeOffset == transcript.length.
             * Treat that as completion/reset rather than trying
             * to speak an empty string.
             */
            if (baseOffset >= state.transcript.length) {

                currentSession = null

                _state.value =
                    state.copy(
                        playbackState = PlaybackState.STOPPED,
                        activeRange = null,
                        resumeOffset = 0,
                    )

                return
            }

            val spokenText =
                state.transcript.substring(baseOffset)

            request =
                PlaybackSession(
                    utteranceId = idGenerator.next(),
                    baseOffset = baseOffset,
                    spokenText = spokenText,
                )

            /*
             * Install session before external speak().
             *
             * A synchronous callback can therefore already
             * identify this request as current.
             */
            currentSession = request

            _state.value =
                state.copy(
                    playbackState = PlaybackState.PLAYING,

                    /*
                     * On resume preserve the paused highlight
                     * until the first new range callback arrives.
                     *
                     * From STOPPED there is no old highlight.
                     */
                    activeRange =
                        if (state.playbackState == PlaybackState.PAUSED) {
                            state.activeRange
                        } else {
                            null
                        },

                    resumeOffset = baseOffset,
                )
        }

        speechEngine.speak(
            text = request.spokenText,
            utteranceId = request.utteranceId,
            listener = listener,
        )
    }


    // -------------------------------------------------------------------------
    // Pause
    // -------------------------------------------------------------------------

    fun pause() {

        var shouldStop = false

        synchronized(lock) {

            if (cleared) return

            val state = _state.value

            if (state.playbackState != PlaybackState.PLAYING) {
                return
            }

            val session = currentSession

            /*
             * Prefer the beginning of the currently highlighted
             * range.
             *
             * If TTS has not emitted a range yet, resume from
             * the beginning of the current request.
             */
            val resumeOffset =
                state.activeRange?.first
                    ?: session?.baseOffset
                    ?: state.resumeOffset

            /*
             * Invalidate BEFORE stop().
             *
             * Late callbacks caused by stop are stale.
             */
            currentSession = null

            _state.value =
                state.copy(
                    playbackState = PlaybackState.PAUSED,
                    resumeOffset = resumeOffset,
                    // activeRange intentionally preserved
                )

            shouldStop = true
        }

        if (shouldStop) {
            speechEngine.stop()
        }
    }


    // -------------------------------------------------------------------------
    // Stop
    // -------------------------------------------------------------------------

    fun stop() {

        var shouldStop = false

        synchronized(lock) {

            if (cleared) return

            val state = _state.value

            shouldStop =
                currentSession != null ||
                    state.playbackState != PlaybackState.STOPPED

            currentSession = null

            _state.value =
                state.copy(
                    playbackState = PlaybackState.STOPPED,
                    activeRange = null,
                    resumeOffset = 0,
                )
        }

        if (shouldStop) {
            speechEngine.stop()
        }
    }


    // -------------------------------------------------------------------------
    // TTS listener
    // -------------------------------------------------------------------------

    private val listener =
        object : SpeechEngine.Listener {

            override fun onStart(utteranceId: String) {
                // PLAYING is entered immediately when Play is pressed.
            }

            override fun onRangeStart(
                utteranceId: String,
                start: Int,
                endExclusive: Int,
            ) {

                synchronized(lock) {

                    if (cleared) return

                    val session =
                        currentSession ?: return

                    val state =
                        _state.value

                    if (
                        state.playbackState != PlaybackState.PLAYING ||
                        session.utteranceId != utteranceId
                    ) {
                        return
                    }

                    /*
                     * Validate against the exact substring
                     * supplied to this speak() request.
                     */
                    val valid =
                        start >= 0 &&
                            start < endExclusive &&
                            endExclusive <= session.spokenText.length

                    if (!valid) {
                        /*
                         * Keep previous valid highlight.
                         */
                        return
                    }

                    val globalStart =
                        session.baseOffset + start

                    val globalEndExclusive =
                        session.baseOffset + endExclusive

                    /*
                     * Defensive global bounds check.
                     */
                    if (
                        globalStart < 0 ||
                        globalStart >= globalEndExclusive ||
                        globalEndExclusive > state.transcript.length
                    ) {
                        return
                    }

                    _state.value =
                        state.copy(
                            activeRange =
                                globalStart until globalEndExclusive,

                            /*
                             * This is also the point from which
                             * Pause will resume.
                             */
                            resumeOffset = globalStart,
                        )
                }
            }

            override fun onDone(utteranceId: String) {
                finish(utteranceId)
            }

            override fun onError(utteranceId: String) {
                finish(utteranceId)
            }
        }


    private fun finish(utteranceId: String) {

        synchronized(lock) {

            if (cleared) return

            val session =
                currentSession ?: return

            if (
                session.utteranceId != utteranceId ||
                _state.value.playbackState != PlaybackState.PLAYING
            ) {
                return
            }

            /*
             * Invalidate before publishing terminal state.
             */
            currentSession = null

            _state.value =
                _state.value.copy(
                    playbackState = PlaybackState.STOPPED,
                    activeRange = null,
                    resumeOffset = 0,
                )
        }
    }


    // -------------------------------------------------------------------------
    // ViewModel lifetime
    // -------------------------------------------------------------------------

    override fun onCleared() {

        val shouldStop: Boolean

        synchronized(lock) {

            if (cleared) {
                return
            }

            shouldStop =
                currentSession != null ||
                    _state.value.playbackState != PlaybackState.STOPPED

            cleared = true
            currentSession = null
        }

        if (shouldStop) {
            speechEngine.stop()
        }

        speechEngine.shutdown()

        super.onCleared()
    }
}


// =============================================================================
// Visual line helpers
// =============================================================================

private data class VisibleLine(
    val text: String,
    val sourceStart: Int,
    val sourceEndExclusive: Int,
)

private fun visibleLineFor(
    transcript: String,
    layout: TextLayoutResult,
    activeRange: IntRange?,
): VisibleLine {

    if (transcript.isEmpty()) {
        return VisibleLine(
            text = "",
            sourceStart = 0,
            sourceEndExclusive = 0,
        )
    }

    /*
     * If activeRange spans multiple visual lines, the
     * line containing activeRange.first wins.
     */
    val requestedOffset =
        activeRange?.first ?: 0

    val safeOffset =
        requestedOffset.coerceIn(
            0,
            transcript.lastIndex,
        )

    val lineIndex =
        layout.getLineForOffset(safeOffset)

    val start =
        layout.getLineStart(lineIndex)

    /*
     * visibleEnd removes trailing whitespace/newline
     * from what is actually displayed.
     */
    val endExclusive =
        layout.getLineEnd(
            lineIndex = lineIndex,
            visibleEnd = true,
        ).coerceAtLeast(start)

    return VisibleLine(
        text = transcript.substring(
            start,
            endExclusive,
        ),
        sourceStart = start,
        sourceEndExclusive = endExclusive,
    )
}

private fun localHighlightRange(
    activeRange: IntRange?,
    line: VisibleLine,
): IntRange? {

    activeRange ?: return null

    if (line.text.isEmpty()) {
        return null
    }

    val globalStart =
        activeRange.first

    val globalEndExclusive =
        activeRange.last + 1

    val intersectionStart =
        maxOf(
            globalStart,
            line.sourceStart,
        )

    val intersectionEndExclusive =
        minOf(
            globalEndExclusive,
            line.sourceEndExclusive,
        )

    if (intersectionStart >= intersectionEndExclusive) {
        return null
    }

    return (
        intersectionStart - line.sourceStart
        ) until (
        intersectionEndExclusive - line.sourceStart
        )
}


// =============================================================================
// Styling
// =============================================================================

private val SpokenRangeOrange =
    Color(0xFFFF9800)

private val TranscriptStyle =
    TextStyle(
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
    )


// =============================================================================
// One-line transcript renderer
// =============================================================================

@Composable
private fun TranscriptLine(
    transcript: String,
    activeRange: IntRange?,
    modifier: Modifier = Modifier,
) {

    if (transcript.isEmpty()) {
        return
    }

    val textMeasurer =
        rememberTextMeasurer()

    var availableWidthPx by
        remember {
            mutableIntStateOf(0)
        }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged {
                availableWidthPx = it.width
            },
    ) {

        if (availableWidthPx <= 0) {
            return@Box
        }

        /*
         * Measure the COMPLETE transcript using the actual
         * width and the exact same text style used below.
         */
        val layout =
            textMeasurer.measure(
                text = AnnotatedString(transcript),
                style = TranscriptStyle,
                softWrap = true,
                overflow = TextOverflow.Clip,
                constraints = Constraints(
                    maxWidth = availableWidthPx,
                ),
            )

        val line =
            visibleLineFor(
                transcript = transcript,
                layout = layout,
                activeRange = activeRange,
            )

        val localHighlight =
            localHighlightRange(
                activeRange = activeRange,
                line = line,
            )

        val annotated =
            buildAnnotatedString {

                append(line.text)

                if (localHighlight != null) {
                    addStyle(
                        style = SpanStyle(
                            color = SpokenRangeOrange,
                        ),
                        start = localHighlight.first,
                        end = localHighlight.last + 1,
                    )
                }
            }

        Text(
            text = annotated,
            modifier = Modifier.fillMaxWidth(),
            style = TranscriptStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}


// =============================================================================
// Testable screen
// =============================================================================

@Composable
fun TranscriptScreen(
    transcript: String,
    viewModel: ReadAloudViewModel,
    modifier: Modifier = Modifier,
) {

    /*
     * ViewModel survives Activity recreation.
     *
     * The composable does NOT stop playback from onDispose.
     */
    val state by
        viewModel.state.collectAsState()

    /*
     * External transcript is authoritative.
     */
    LaunchedEffect(
        viewModel,
        transcript,
    ) {
        viewModel.setTranscript(transcript)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(12.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {

            /*
             * Button #1:
             *
             * STOPPED -> Play
             * PLAYING -> Pause
             * PAUSED  -> Play
             */
            Button(
                enabled =
                    state.transcript.isNotEmpty(),
                onClick = {

                    when (state.playbackState) {

                        PlaybackState.PLAYING ->
                            viewModel.pause()

                        PlaybackState.STOPPED,
                        PlaybackState.PAUSED ->
                            viewModel.play()
                    }
                },
            ) {

                Text(
                    if (
                        state.playbackState ==
                        PlaybackState.PLAYING
                    ) {
                        "Pause"
                    } else {
                        "Play"
                    },
                )
            }

            /*
             * Button #2:
             * always Stop.
             */
            Button(
                enabled =
                    state.playbackState !=
                        PlaybackState.STOPPED,
                onClick = viewModel::stop,
            ) {
                Text("Stop")
            }
        }

        TranscriptLine(
            transcript = state.transcript,
            activeRange = state.activeRange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}


// =============================================================================
// Production ViewModel factory
// =============================================================================

class ReadAloudViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {

        require(
            modelClass.isAssignableFrom(
                ReadAloudViewModel::class.java,
            ),
        )

        /*
         * This factory's create() is only called when a new
         * ViewModel is actually required.
         *
         * During configuration recreation the retained
         * ViewModel is reused, so a second engine is not
         * created merely because a new Activity exists.
         */
        val engine =
            AndroidSpeechEngine(
                application.applicationContext,
            )

        return ReadAloudViewModel(
            application = application,
            speechEngine = engine,
        ) as T
    }
}
