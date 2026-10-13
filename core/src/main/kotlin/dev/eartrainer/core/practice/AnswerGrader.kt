package dev.eartrainer.core.practice

import dev.eartrainer.core.audio.PitchEstimate
import dev.eartrainer.core.audio.SingbackGrader
import dev.eartrainer.core.model.Exercise
import dev.eartrainer.core.model.ExpectedAnswer
import dev.eartrainer.core.model.Interval

sealed interface UserResponse {
    data class Speech(val interval: Interval?) : UserResponse
    data class Sung(val estimate: PitchEstimate?) : UserResponse
}

data class Grade(
    val correct: Boolean,
    val interval: Interval?,
)

object AnswerGrader {
    fun grade(exercise: Exercise, response: UserResponse): Grade {
        return when (val expected = exercise.expected) {
            is ExpectedAnswer.SpokenName -> when (response) {
                is UserResponse.Speech -> Grade(
                    correct = response.interval == expected.interval,
                    interval = expected.interval,
                )
                is UserResponse.Sung -> Grade(correct = false, interval = expected.interval)
            }
            is ExpectedAnswer.SungPitch -> when (response) {
                is UserResponse.Sung -> Grade(
                    correct = SingbackGrader.grade(
                        expected.midi,
                        response.estimate,
                        expected.toleranceCents,
                    ),
                    interval = null,
                )
                is UserResponse.Speech -> Grade(correct = false, interval = null)
            }
        }
    }
}
