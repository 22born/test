AndroidSpeechEngine     ← implement SpeechEngine
ReadAloudViewModel      ← playback/session/lifecycle state
supporting UI/layout    ← line measurement + highlighting





Android Read-Aloud Transcript

Build a Jetpack Compose screen that reads a supplied transcript aloud and visually follows the currently spoken text.

The starter project provides "MainActivity", "TranscriptScreen", and the "SpeechEngine" contract. You may add or restructure code as needed.

Requirements

UI

The screen must contain exactly two buttons above the transcript:

- Play / Pause
- Stop

Button behavior:

- STOPPED → "Play"
- PLAYING → "Pause"
- PAUSED → "Play"
- "Stop" is enabled only while playing or paused.
- For an empty transcript, both buttons are disabled and no speech starts.

Below the buttons, display exactly one visual line of transcript text.

The transcript must be:

- horizontally centered;
- exactly "20sp";
- highlighted using foreground color "#FF9800" for the currently spoken range.

Highlighting must change foreground color only and must not cause text to reflow.

Speech

Use the supplied "SpeechEngine" interface for playback. The production implementation must use Android "TextToSpeech".

"onRangeStart()" provides "[start, endExclusive)" offsets into the exact string supplied to that "speak()" request. These use normal Android/Kotlin UTF-16 "String" indexing.

Use these offsets as the source of truth. Do not estimate speech timing or locate spoken words by searching their text.

Invalid ranges must be ignored without crashing or replacing the previous valid highlight.

Visual lines

Only one visual line is displayed at a time.

Visual lines must be determined from actual Compose text layout and available width. Do not split using a fixed number of characters or words.

- With no active range, display the first visual line.
- With an active range, display the line containing its start.
- Explicit newlines create line boundaries.
- If a range crosses lines, display the line containing its start and highlight only the portion visible on that line.
- If a range starts at the first character of the next line, display that next line.
- Trailing whitespace/newlines should not visibly appear at the selected line end.
- An oversized unbreakable token may be clipped.

Playback

Play from the beginning

Play starts from offset "0", clears any previous highlight, and begins reading the complete transcript.

Pause

Pause stops the current speech request while preserving the current displayed line and highlight.

Resume

Play while paused resumes from the beginning of the most recently active spoken range.

For example, if the active range begins at global transcript offset "10", resume should speak:

"transcript.substring(10)"

Callbacks from resumed speech are relative to that substring and must be translated back to positions in the original transcript.

For example:

"resume offset = 10"
"callback = [6, 9)"
"original transcript range = [16, 19)"

This must remain correct across multiple pause/resume cycles.

If playback is paused before any valid range callback has been received, resume from the beginning of the current speech request.

Repeating the current word/range when resuming is acceptable. Exact audio-level seeking is not required.

Stop

Stop must:

- stop current speech;
- clear the highlight;
- reset playback position;
- return to the first visual line.

The next Play starts from the beginning of the full transcript.

Completion or speech error should have the same reset behavior.

Asynchronous callbacks

Each speech request, including resumed requests, must use a unique utterance ID.

Callbacks belonging to old playback requests must not modify current playback.

This includes callbacks arriving after:

- Pause / Resume;
- Stop / Play;
- completion or error;
- transcript replacement.

Speech callbacks may arrive asynchronously and from different threads. The implementation must remain safe under concurrent callback delivery.

Do not use timers, sleeps, delays, or estimated word durations to simulate synchronization.

Transcript changes

If the supplied transcript changes while playing or paused:

- stop current speech;
- reset to the stopped state;
- clear the current highlight and playback position;
- display the first line of the new transcript;
- do not automatically start the new transcript.

Late callbacks from the previous transcript must have no effect.

Recomposition with the same transcript must not restart or reset playback.

Configuration changes

Playback must survive Activity configuration changes such as screen rotation.

Rotating while playing must preserve:

- playback;
- current highlight;
- displayed line;
- button state.

Rotation must not stop playback or create another speech request. Subsequent speech callbacks must update the recreated UI.

Rotating while paused must preserve the paused position and highlight, and Play must resume from the preserved position.

Do not bypass Activity recreation using "android:configChanges".

Process-death recovery, foreground/background playback, media sessions, and audio focus are out of scope.

Starter Code

"SpeechEngine.kt"

package com.example.readaloud

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

"TranscriptScreen.kt"

package com.example.readaloud

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TranscriptScreen(
    transcript: String,
    modifier: Modifier = Modifier,
) {
    TODO("Implement")
}

"MainActivity.kt"

package com.example.readaloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TranscriptScreen(
                        transcript = SAMPLE_TRANSCRIPT,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

private const val SAMPLE_TRANSCRIPT =
    "The quick brown fox jumps over the lazy dog. " +
        "Jetpack Compose makes Android UI development declarative. " +
        "This transcript should continue playing when the screen rotates."

Test Cases

The following are the key behavioral and edge-case tests.

1. Pause → Resume offset translation

Start playback and emit "[10, 15)". Pause, then Play.

Verify playback resumes with "transcript.substring(10)".

A callback "[6, 9)" from the resumed request must highlight original transcript range "[16, 19)".

2. Multiple Pause / Resume cycles

Perform at least three pause/resume cycles with different spoken ranges.

Verify all positions remain relative to the original transcript and do not accumulate incorrect substring-relative offsets.

3. Stale callbacks after Resume

Play request A → receive range A → Pause → Play request B → receive range B.

Then deliver late range, completion, and error callbacks from A.

Verify none of them modify B's state or highlight.

4. Pause vs Stop

Verify Pause preserves the current highlight, displayed line, and resume position.

Verify Stop clears them, resets playback to the beginning, and causes the next Play to speak the complete transcript.

5. Repeated words

Use:

"go go go"

Emit the offsets corresponding to the second "go".

Verify the second occurrence is highlighted rather than locating the first matching word.

6. Invalid ranges

Establish a valid highlight, then send negative, empty, reversed, and out-of-bounds ranges.

Verify there is no crash and the previous valid highlight remains unchanged.

7. Terminal callback cannot resurrect playback

Play → range → Done → late range.

Repeat with Error.

Verify playback remains stopped with no highlight after the terminal callback.

8. Real Compose text layout

Render text at different available widths and use proportional characters such as:

"iiiiiiiiiiiiiiii WWWWWWWWWWWWWWWW"

Verify visual-line selection follows actual Compose measurement rather than character or word counts.

9. Range crossing a visual-line boundary

Create a range beginning on line 1 and ending on line 2.

Verify line 1 is displayed and only its intersection is highlighted.

Then start a range exactly at the first character of line 2 and verify line 2 becomes visible.

10. Rotation while playing

Play → receive a range → recreate the Activity.

Verify:

- playback remains active;
- highlight survives;
- no additional "speak()" occurs;
- rotation does not call "stop()".

Deliver another range afterward and verify the recreated UI updates.

11. Rotation while paused

Play → receive range beginning at offset "N" → Pause → recreate Activity → Play.

Verify paused state/highlight survive and playback resumes with "transcript.substring(N)".

12. Transcript replacement

Play transcript A → receive a range → Pause → replace with transcript B.

Deliver late callbacks from A.

Verify they have no effect. The next Play must speak the complete transcript B.

13. Concurrent callback race

Deliver current and stale range/completion/error callbacks concurrently from multiple threads.

Verify:

- no crashes or corrupted state;
- stale sessions cannot overwrite the current session;
- terminal sessions cannot be resurrected by late callbacks;
- any published active range is valid.

14. Visual contract

Using Roborazzi or equivalent visual regression testing, verify the playing state has:

- exactly two buttons;
- "Pause" and "Stop" labels;
- one visual transcript line;
- horizontally centered transcript;
- exactly "20sp" text;
- spoken range foreground exactly "#FF9800";
- no additional highlight styling.

15. Highlighting does not reflow

Capture the same transcript before and after highlighting a range near a wrapping boundary.

Verify only foreground color changes. Character positions, wrapping, line boundaries, and text geometry must remain unchanged.



------------------


Read-Aloud Transcript — Android / Jetpack Compose Assignment
Requirements
Build a Jetpack Compose screen that reads a supplied transcript aloud and visually follows the currently spoken text.
The screen receives the transcript externally:
@Composable
fun TranscriptScreen(
    transcript: String,
    viewModel: ReadAloudViewModel,
    modifier: Modifier = Modifier,
)
Production speech should use Android TextToSpeech through the provided SpeechEngine abstraction. Tests must be able to inject a fake engine and must not use real TTS.
UI
The screen contains exactly two buttons above the transcript:
[ Play / Pause ]   [ Stop ]

       transcript line
The Play/Pause button displays:
Play when STOPPED
Pause when PLAYING
Play when PAUSED
Stop is enabled only while PLAYING or PAUSED. With an empty transcript, both buttons are disabled and no speech may start.
Display exactly one visual line of transcript text at a time.
Transcript styling:
font size: exactly 20sp
horizontally centered
active spoken range foreground: exactly #FF9800
highlighting changes foreground color only; it must not change layout, weight, size, spacing, or background.
Text layout
Visual lines must come from actual Compose text measurement using the available width, font metrics, density and font scale.
Do not use fixed character/word counts to determine wrapping.
Initially, show the first visual line. While speaking or paused, show the line containing the start of the active range.
Explicit newlines create line boundaries. If an active range crosses two lines, show the line containing its start and highlight only the portion on that line. Trailing whitespace/newlines should not visibly appear at the selected line end. Oversized unbreakable tokens may be clipped.
Speech ranges
onRangeStart() provides:
[start, endExclusive)
relative to the exact string passed to that speak() request.
Offsets use Kotlin/Android UTF-16 String indexing.
Use the supplied offsets directly. Do not search the transcript for the spoken word; repeated words must therefore work correctly.
A valid callback satisfies:
0 <= start < endExclusive <= spokenText.length
Invalid callbacks are ignored and must not clear a previous valid highlight.
Playback
Use three states:
enum class PlaybackState {
    STOPPED,
    PLAYING,
    PAUSED,
}
Play from STOPPED: speak the complete original transcript, create a unique utterance ID, immediately enter PLAYING and clear any old highlight.
Pause: stop the current TTS request, invalidate its session, enter PAUSED, and preserve the current highlight/displayed line. Resume from the beginning of the most recently active range. If no range has arrived yet, resume from the beginning of the current speech request.
Resume: create a new utterance ID and speak:
transcript.substring(resumeOffset)
TTS offsets from this substring must be translated back to offsets in the original transcript.
For example, if playback resumes at global offset 10 and TTS reports [6,9), the UI range is [16,19).
This must remain correct through multiple pause/resume cycles. Exact audio-level mid-word seeking is not required; repeating the current word from its beginning is expected.
Stop: stop playback, invalidate the session, enter STOPPED, clear the highlight, reset the resume position to 0, and return to the first visual line. The next Play starts from the beginning.
Current-session onDone() or onError() also returns to STOPPED and clears/reset playback state.
Stale and concurrent callbacks
Every speech request must have a unique utterance ID.
Callbacks from previous sessions must never affect the current session, including callbacks arriving after Pause, Stop, Resume, completion, error, or transcript replacement.
TTS callbacks may arrive asynchronously and from different threads. The implementation must safely handle concurrent callbacks without corrupting state.
Do not use delays, timers, sleeps, or estimated word durations. TTS range callbacks are the source of truth.
Transcript changes
If the transcript changes while PLAYING or PAUSED:
stop current speech;
invalidate the session;
switch to STOPPED;
clear highlight/resume position;
show the new transcript's first line;
do not automatically start playback.
Late callbacks from the previous transcript must be ignored.
Recomposition with the same transcript must not restart or stop playback.
Configuration changes
Playback must survive Activity recreation such as screen rotation.
While PLAYING, rotation must not:
stop speech;
start another speech request;
lose the active range or playback state.
Callbacks received after recreation must update the newly created UI.
While PAUSED, rotation must preserve the paused highlight and resume position. Play afterward must resume from that position.
Do not avoid Activity recreation using android:configChanges.
Process-death recovery, foreground services and background playback are out of scope.
Crucial Test Cases
1. Pause → Resume with global offset translation
Play the full transcript and emit global range [10,15). Pause and then Play.
Verify:
speak(transcript.substring(10), ...)
If resumed TTS emits [6,9), verify the active global range becomes [16,19).
2. Multiple Pause/Resume cycles
Perform several cycles:
Play
range
Pause
Play
range
Pause
Play
range
Verify every active range and resume position remains relative to the original transcript, not the previously spoken substring.
3. Stale callbacks after Resume
Execute:
Play A
range A
Pause A

Play B
range B

late range A
late done A
late error A
Verify B's playback state and highlight remain unchanged.
4. Pause vs Stop
Verify Pause:
preserves highlight;
preserves displayed line;
preserves resume position.
Then verify Stop:
clears highlight;
resets resume offset to 0;
returns to the first line.
The next Play must send the complete transcript.
5. Repeated words and exact offsets
Use:
go go go
Supply offsets for the second go.
Verify only the second occurrence is selected/highlighted. The implementation must not locate the word by searching its text.
6. Invalid callbacks preserve valid state
First establish a valid highlight.
Then emit:
negative start;
start == end;
start > end;
end beyond spoken text.
Verify no crash and that the previous valid highlight remains unchanged.
7. Terminal session cannot be resurrected
Test:
Play
range
Done
late range
and:
Play
range
Error
late range
Both must remain STOPPED with no highlight after the terminal callback.
8. Actual Compose layout measurement
Render the same transcript at narrow and wide widths and include substantially different proportional glyphs such as:
iiiiiiiiiiiiiiii
WWWWWWWWWWWWWWWW
Verify wrapping follows actual Compose measurement rather than character or word counts.
9. Cross-line active range
Create an active range beginning on visual line 1 and ending on line 2.
Verify:
line 1 is displayed;
only the line-1 intersection is highlighted.
Then make the range start exactly at the first character of line 2 and verify line 2 is displayed.
10. Rotation while PLAYING
Execute:
Play
range
Activity recreation
Verify:
state remains PLAYING;
highlight/displayed line survives;
stop() was not called because of recreation;
no second speak() occurred.
Then emit another range callback and verify the recreated UI updates.
11. Rotation while PAUSED
Execute:
Play
range at global offset N
Pause
Activity recreation
Play
Verify paused state/highlight survives recreation and the resumed request starts with:
transcript.substring(N)
12. Transcript replacement invalidates old playback
Execute:
Play transcript A
range A
Pause

replace with transcript B

late callbacks from A

Play
Verify old callbacks do nothing and Play sends the complete transcript B.
13. Real concurrency / race-condition test
Deliver current and stale onRangeStart, onDone, and onError callbacks concurrently from multiple executor threads.
Verify:
no crash;
no corrupted state;
stale sessions cannot overwrite the current session;
Done/Error cannot be followed by a late range that resurrects playback;
published ranges remain valid.
14. Roborazzi — visual contract
Capture the PLAYING state and verify:
exactly two buttons;
labels Pause and Stop;
exactly one transcript line;
transcript is horizontally centered;
font size is 20sp;
active range foreground is exactly #FF9800;
no highlight background or other styling change.
15. Roborazzi — highlighting does not reflow
Capture the same transcript before and after applying a highlight, preferably with the highlighted word near a wrap boundary.
Verify that highlighting changes only the foreground color:
character positions remain identical;
line boundaries remain identical;
text dimensions remain identical;
no wrapping/reflow occurs.





------------------------------------
    

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
