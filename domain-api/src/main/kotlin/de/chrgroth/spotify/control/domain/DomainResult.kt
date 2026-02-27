package de.chrgroth.spotify.control.domain

sealed class DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>()
    data class Failure(val error: DomainError) : DomainResult<Nothing>()

    fun <R> map(transform: (T) -> R): DomainResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> DomainResult<R>): DomainResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): DomainError? = (this as? Failure)?.error
}
