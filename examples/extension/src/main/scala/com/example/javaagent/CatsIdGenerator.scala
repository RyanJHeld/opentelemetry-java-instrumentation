package com.example.javaagent

import io.opentelemetry.sdk.trace.IdGenerator
import java.util.concurrent.atomic.AtomicLong
import cats.effect._
import cats.effect.unsafe.implicits.global

/**
 * Custom {@link IdGenerator} which provides span and trace ids.
 *
 * @see io.opentelemetry.sdk.trace.SdkTracerProvider
 * @see DemoAutoConfigurationCustomizerProvider
 */
class CatsIdGenerator extends IdGenerator {
  private val traceId = IOLocal(0L).unsafeRunSync()
  private val spanId = IOLocal(0L).unsafeRunSync()

  override def generateSpanId(): String =
    String.format("%016d", spanId.modify(id => (id + 1, id + 1)).unsafeRunSync())


  override def generateTraceId(): String =
    String.format("%032d", traceId.modify(id => (id + 1, id + 1)).unsafeRunSync())

}
