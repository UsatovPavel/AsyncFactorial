package ru.hse.scala.individual.http

import sttp.tapir.Schema
import tethys.{JsonReader, JsonWriter}
import tethys.derivation.semiauto.{jsonReader, jsonWriter}

/** Kafka-ready request item.
  *
  *   - jobId: correlates all items belonging to one logical request/job
  *   - itemId: stable per-item id
  */
final case class TaskItem(
    jobId: String,
    itemId: Long,
    input: Int,
)
object TaskItem {
  implicit val schema: Schema[TaskItem] = Schema.derived
  // Avoid self-referential implicit derivation warnings/errors by deriving in a non-implicit val first.
  private val derivedReader: JsonReader[TaskItem] = jsonReader[TaskItem]
  private val derivedWriter: JsonWriter[TaskItem] = jsonWriter[TaskItem]

  implicit val jsonReaderTaskItem: JsonReader[TaskItem] = derivedReader
  implicit val jsonWriterTaskItem: JsonWriter[TaskItem] = derivedWriter
}

/** Kafka-ready result item (ADT).
  *
  * Invariant is enforced by types: result is either Success or Failure.
  */
sealed trait ResultItem {
  def jobId: String
  def itemId: Long
  def input: Int
}

object ResultItem {
  final case class Success(
      jobId: String,
      itemId: Long,
      input: Int,
      result: String,
  ) extends ResultItem

  final case class Failure(
      jobId: String,
      itemId: Long,
      input: Int,
      error: String,
  ) extends ResultItem
}

/** Wire-format DTO (HTTP/JSON, Kafka payload).
  *
  * We keep it flat to match your original API shape and make external integrations easier.
  * Invariant is enforced at the conversion boundary from the ADT.
  */
final case class ResultItemDto(
    jobId: String,
    itemId: Long,
    input: Int,
    result: Option[String],
    error: Option[String],
)

object ResultItemDto {
  implicit val schema: Schema[ResultItemDto] = Schema.derived
  private val derivedReader: JsonReader[ResultItemDto] = jsonReader[ResultItemDto]
  private val derivedWriter: JsonWriter[ResultItemDto] = jsonWriter[ResultItemDto]

  implicit val jsonReaderResultItemDto: JsonReader[ResultItemDto] = derivedReader
  implicit val jsonWriterResultItemDto: JsonWriter[ResultItemDto] = derivedWriter

  def from(item: ResultItem): ResultItemDto =
    item match {
      case ResultItem.Success(jobId, itemId, input, value) =>
        ResultItemDto(jobId, itemId, input, result = Some(value), error = None)
      case ResultItem.Failure(jobId, itemId, input, message) =>
        ResultItemDto(jobId, itemId, input, result = None, error = Some(message))
    }
}
