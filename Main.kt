fun main() {
    println("Hello, Kotlin!")
}

You are given the following Kotlin coroutines program. Do not run it. Reason through its behaviour step by step.
kotlinimport kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

suspend fun main() = coroutineScope {
    val upstream = flow {
        emit(1)          // t = 0 ms
        delay(50)
        emit(2)          // t = 50 ms
        delay(30)
        emit(3)          // t = 80 ms
        delay(200)
        emit(4)          // t = 280 ms
    }

    val result = upstream
        .debounce(60)
        .flatMapLatest { value ->
            flow {
                emit(value * 10)
                delay(100)
                emit(value * 10 + 1)
            }
        }
        .take(4)

    result.collect { println(it) }
}

Task
Answer all four questions below. Show your reasoning for each — do not just state a conclusion.
Question 1 (1 point)
The upstream emits values at t=0ms, t=50ms, t=80ms, and t=280ms. With debounce(60) applied, which of these values pass through to flatMapLatest and which are dropped? For each value that passes, state the approximate time it is forwarded downstream.
Question 2 (2 points)
When flatMapLatest receives value 3, it starts an inner flow that emits 30, waits 100ms, then emits 31. Value 4 eventually arrives at flatMapLatest. Has the inner flow for value 3 already emitted 31 before value 4 arrives? Show your timing calculation. What does flatMapLatest do to the inner flow for value 3 at the moment value 4 is received?
Question 3 (3 points)
Based on your answers above, write the exact sequence of values printed by collect { println(it) }, in order. For each value, state the approximate time it is emitted and explain in one sentence why it appears in the output.
Question 4 (4 points)
Now change debounce(60) to debounce(25) and reason through the program again from scratch. How does the output sequence change compared to Question 3? For each difference, explain why the change in debounce timeout caused it.

Output Format
Write your answer as a single JSON object with the structure below. Do not include anything outside the JSON. Do not wrap it in markdown code fences.
json{
  "q1": {
    "passes": [
      { "value": "<int>", "time_ms": "<int>", "reason": "<string>" }
    ],
    "dropped": [
      { "value": "<int>", "reason": "<string>" }
    ]
  },
  "q2": {
    "31_emitted_before_value4": "<true|false>",
    "timing_calculation": "<string>",
    "cancellation_effect": "<string>"
  },
  "q3": {
    "output_sequence": [
      { "value": "<int>", "time_ms": "<int>", "reason": "<string>" }
    ]
  },
  "q4": {
    "new_output_sequence": ["<int>"],
    "differences_from_q3": [
      { "change": "<string>", "reason": "<string>" }
    ],
    "inner_flow_analysis": [
      { "value": "<int>", "second_emission_emitted": "<true|false>", "reason": "<string>" }
    ]
  }
}

Reference Answer
json{
  "q1": {
    "passes": [
      { "value": 3, "time_ms": 140, "reason": "After emit(3) at t=80ms the next emission is at t=280ms, a gap of 200ms which exceeds the 60ms window, so 3 is forwarded at 80+60=140ms." },
      { "value": 4, "time_ms": 340, "reason": "After emit(4) at t=280ms the upstream completes with no further emissions, so 4 is forwarded at 280+60=340ms." }
    ],
    "dropped": [
      { "value": 1, "reason": "emit(2) arrives at t=50ms, only 50ms after emit(1) at t=0ms, which is within the 60ms window. The debounce timer resets." },
      { "value": 2, "reason": "emit(3) arrives at t=80ms, only 30ms after emit(2) at t=50ms, which is within the 60ms window. The debounce timer resets again." }
    ]
  },
  "q2": {
    "31_emitted_before_value4": true,
    "timing_calculation": "Value 3 is forwarded by debounce at ~t=140ms. The inner flow immediately emits 30, then waits delay(100). So 31 is emitted at ~t=240ms. Value 4 does not arrive at flatMapLatest until ~t=340ms. 240ms < 340ms, so 31 is emitted 100ms before value 4 arrives.",
    "cancellation_effect": "The inner flow for value 3 has already completed by t=340ms, so when value 4 arrives flatMapLatest has nothing to cancel. The cancellation is a no-op."
  },
  "q3": {
    "output_sequence": [
      { "value": 30, "time_ms": 140, "reason": "Value 3 passes debounce at ~140ms and the inner flow immediately emits 3*10=30." },
      { "value": 31, "time_ms": 240, "reason": "The inner flow for value 3 waits delay(100) then emits 3*10+1=31." },
      { "value": 40, "time_ms": 340, "reason": "Value 4 passes debounce at ~340ms and the new inner flow immediately emits 4*10=40." },
      { "value": 41, "time_ms": 440, "reason": "The inner flow for value 4 waits delay(100) then emits 4*10+1=41. take(4) now has 4 items and terminates collection." }
    ]
  },
  "q4": {
    "new_output_sequence": [10, 20, 30, 31],
    "differences_from_q3": [
      { "change": "Values 1 and 2 now pass debounce (were dropped in Q3)", "reason": "debounce(25) has a 25ms window. The gap between emit(1) and emit(2) is 50ms > 25ms, so 1 passes. The gap between emit(2) and emit(3) is 30ms > 25ms, so 2 passes." },
      { "change": "Output starts with 10 and 20 instead of 30 (Q3 started at 30)", "reason": "Values 1 and 2 now pass debounce and each triggers an inner flow that immediately emits 10 and 20 respectively before being cancelled." },
      { "change": "Values 40 and 41 no longer appear in the output (they appeared in Q3)", "reason": "take(4) is satisfied by 10, 20, 30, 31 before the inner flow for value 4 ever emits 40 or 41." }
    ],
    "inner_flow_analysis": [
      { "value": 1, "second_emission_emitted": false, "reason": "Value 1 passes at ~t=25ms and inner flow emits 10 immediately. 11 would be due at ~t=125ms but value 2 arrives at flatMapLatest at ~t=75ms, cancelling the inner flow mid-delay." },
      { "value": 2, "second_emission_emitted": false, "reason": "Value 2 passes at ~t=75ms and inner flow emits 20 immediately. 21 would be due at ~t=175ms but value 3 arrives at flatMapLatest at ~t=105ms, cancelling the inner flow mid-delay." },
      { "value": 3, "second_emission_emitted": true, "reason": "Value 3 passes at ~t=105ms and inner flow emits 30 immediately. 31 is due at ~t=205ms. Value 4 does not arrive until ~t=305ms, so the inner flow completes and 31 is emitted." },
      { "value": 4, "second_emission_emitted": true, "reason": "Value 4 passes at ~t=305ms and inner flow emits 40 at ~t=305ms and 41 at ~t=405ms. However take(4) is already satisfied after 31 and collection is terminated before 40 is emitted." }
    ]
  }
}


//\\\\\\\\

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
 
suspend fun main() = coroutineScope {
    val upstream = flow {
        emit(1)          // t = 0 ms
        delay(50)
        emit(2)          // t = 50 ms
        delay(30)
        emit(3)          // t = 80 ms
        delay(200)
        emit(4)          // t = 280 ms
    }
 
    val result = upstream
        .debounce(60)
        .flatMapLatest { value ->
            flow {
                emit(value * 10)
                delay(100)
                emit(value * 10 + 1)
            }
        }
        .take(4)
 
    result.collect { println(it) }
}


Questions
Q1 — Debounce windowing  (1 point)


The upstream emits values at the following times:

emit(1) at t = 0 ms
emit(2) at t = 50 ms
emit(3) at t = 80 ms
emit(4) at t = 280 ms

Question: With debounce(60) applied, which upstream values pass through to flatMapLatest and which are dropped? State the time at which each passing value is forwarded downstream.

Expected answer:
1 is dropped. emit(2) arrives at t=50ms, which is within the 60ms window after emit(1). The debounce timer resets.
2 is dropped. emit(3) arrives at t=80ms, which is 30ms after emit(2) — still within the 60ms window. The timer resets again.
3 passes through. After emit(3) at t=80ms, the next emission is emit(4) at t=280ms — a gap of 200ms, well beyond 60ms. So 3 is forwarded at approximately t=140ms (80+60).
4 passes through. After emit(4) at t=280ms, the upstream completes with no further emissions. 4 is forwarded at approximately t=340ms (280+60).

Q2 — Cancellation reasoning  (2 points)


Question: When value 4 arrives at flatMapLatest (at ~t=340ms), an inner flow for value 3 is already running. Answer both parts:

Has the inner flow for value 3 already emitted its second value (31) before value 4 arrives? Show your timing reasoning.
What happens to the inner flow for value 3 at the moment value 4 is received by flatMapLatest?

Expected answer:
Value 3 is forwarded by debounce at ~t=140ms. The inner flow immediately emits 30, then waits delay(100) before emitting 31. So 31 would be emitted at ~t=240ms — well before value 4 arrives at ~t=340ms. Yes, 31 has already been emitted.
When value 4 arrives at ~t=340ms, flatMapLatest cancels the inner flow for 3. However, that inner flow has already completed (it emitted both 30 and 31 before t=340ms), so there is nothing to cancel. The cancellation is a no-op.

Key trap: many models will assume the inner flow for 3 is still running when 4 arrives, leading them to claim 31 is dropped. The timing shows it completed 100ms earlier.

Q3 — Full output prediction  (3 points)


Question: Write out the complete sequence of values printed by collect { println(it) }, in order. Include the approximate emission time for each value. Justify each entry in one sentence.

Expected answer:



Q4 — Parameter mutation  (4 points)


Question: Change debounce(60) to debounce(25). Without running the code, predict how the output sequence changes compared to Q3. Justify each difference.

Expected answer:

With debounce(25), the windowing changes entirely:

emit(1) at t=0ms: next emission is at t=50ms (gap=50ms). 50ms > 25ms, so 1 passes through at ~t=25ms.
emit(2) at t=50ms: next emission is at t=80ms (gap=30ms). 30ms > 25ms, so 2 passes through at ~t=75ms.
emit(3) at t=80ms: next emission is at t=280ms (gap=200ms). 200ms > 25ms, so 3 passes through at ~t=105ms.
emit(4) at t=280ms: upstream completes, so 4 passes through at ~t=305ms.

Now all four upstream values pass debounce. Each triggers a new flatMapLatest inner flow. The key question is: does each inner flow complete its delay(100) before the next upstream value arrives?

Value 1 inner flow: emits 10 at ~t=25ms, then needs 100ms for 11 (due at ~t=125ms). But value 2 arrives at flatMapLatest at ~t=75ms — only 50ms later. The inner flow for 1 is cancelled mid-delay. 11 is NEVER emitted.
Value 2 inner flow: emits 20 at ~t=75ms, needs 100ms for 21 (due at ~t=175ms). Value 3 arrives at ~t=105ms — only 30ms later. Cancelled mid-delay. 21 is NEVER emitted.
Value 3 inner flow: emits 30 at ~t=105ms, needs 100ms for 31 (due at ~t=205ms). Value 4 arrives at ~t=305ms — 200ms later. 31 IS emitted at ~t=205ms before cancellation.
Value 4 inner flow: emits 40 at ~t=305ms, then 41 at ~t=405ms. take(4) triggers after 41.

Final output with debounce(25): 10, 20, 30, 31, 40, 41 — but take(4) cuts it to: 10, 20, 30, 31.

Compare to Q3 output (30, 31, 40, 41): the output is completely different. With debounce(25), values 1 and 2 now pass through but their inner flows are mostly cancelled; take(4) is satisfied before values 40 and 41 are ever emitted.


Scoring rubric




Evaluation notes

Signals of genuine reasoning vs. recall
A model reasoning correctly will construct a timeline and check each gap against the debounce window explicitly.
A model relying on recall will often get Q1 right (debounce is well-documented) but fail Q2 by assuming flatMapLatest always cancels the second inner emission.
Q4 is the definitive separator: a model must discard its Q3 answer entirely and re-reason. Any model that produces a Q4 answer structurally similar to Q3 (just with different values) has likely pattern-matched rather than reasoned.

Partial credit guidance
Award partial credit in Q3 if the model gets the correct values but wrong times (understanding > precision).
In Q4, a model that correctly identifies which inner flows get cancelled — even if it miscounts take(4) — demonstrates understanding of the core mechanic.
Do not award Q2 credit for stating 'flatMapLatest cancels previous flows' without applying the timing argument.

Automated scoring note
Q1, Q2, Q3: can be scored deterministically by checking the output sequence and key phrases in the justification.
Q4: requires a judge model or human review due to the complexity of the reasoning chain. A simple string-match on '10, 20, 30, 31' is insufficient; the justification must be checked.


Variant ideas
To generate additional benchmark instances from the same template, vary these parameters:

Change debounce timeout (25, 40, 90, 120ms) — each creates a completely different set of passing values.
Change the inner flow delay (50ms, 150ms, 200ms) — affects whether inner flows complete before the next flatMapLatest trigger.
Replace flatMapLatest with flatMapConcat — tests whether the model understands that concat queues rather than cancels.
Add a buffer(1) between debounce and flatMapLatest — changes backpressure behaviour and emission timing.
Change take(4) to take(3) or take(6) — shifts the termination point to a different part of the reasoning chain.

Each parameter change produces a new problem with a unique correct answer, making this template highly extensible for large benchmark suites.
