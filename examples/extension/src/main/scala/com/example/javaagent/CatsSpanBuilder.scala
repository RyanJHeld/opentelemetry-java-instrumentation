package com.example.javaagent

import io.opentelemetry.api.common.AttributeKey.booleanKey
import io.opentelemetry.api.common.AttributeKey.doubleKey
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.internal.ImmutableSpanContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.internal.AttributeUtil
import io.opentelemetry.sdk.internal.AttributesMap
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.samplers.SamplingDecision
import io.opentelemetry.sdk.trace.samplers.SamplingResult
import io.opentelemetry.sdk.trace.{IdGenerator, SdkSpan, TracerSharedState, SpanLimits}

import java.util
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.annotation.Nullable
import cats.effect.*
import cats.effect.unsafe.implicits.global

import java.util

final case class CatsSpanBuilder(
  spanName: String,
  instrumentationScopeInfo: InstrumentationScopeInfo,
  tracerSharedState: TracerSharedState,
  spanLimits: SpanLimits,
  parentIO: IOLocal[Option[Context]] = IOLocal(Option.empty[Context]).unsafeRunSync(),
  attributesIO: IOLocal[AttributesMap] = IOLocal(Attributes.empty().asInstanceOf[AttributesMap]).unsafeRunSync(),
  linksIO: IOLocal[util.List[LinkData]] = IOLocal(Collections.emptyList()).unsafeRunSync()
) extends SpanBuilder {

  private var spanKind: SpanKind = SpanKind.INTERNAL
//  private var attributes: AttributesMap
//  private var links: util.List[LinkData]
  private var totalNumberOfLinksAdded: Int = 0
  private var startEpochNanos: Long = 0


  override def setParent(context: Context): SpanBuilder = {
    if (context != null) {
      parentIO.set(Some(context)).unsafeRunSync()
    }
    this
  }

  override def setNoParent(): SpanBuilder = {
    parentIO.set(Some(Context.root())).unsafeRunSync()
    this
  }

  override def setSpanKind(spanKind: SpanKind): SpanBuilder = {
    if (spanKind != null) {
      this.spanKind = spanKind
    }
    this
  }

  override def addLink(spanContext: SpanContext):  SpanBuilder = {
    if (spanContext != null && spanContext.isValid()) {
      addLink(LinkData.create(spanContext))
    }
    this
  }

  override def addLink(spanContext: SpanContext, attributes: Attributes): SpanBuilder = {
    if (spanContext == null || !spanContext.isValid()) {
      return this
    }
    if (attributes == null) {
      attributes = Attributes.empty()
    }
    val totalAttributeCount = attributes.size()
    addLink(
      LinkData.create(
        spanContext,
        AttributeUtil.applyAttributesLimit(
          attributes,
          spanLimits.getMaxNumberOfAttributesPerLink(),
          spanLimits.getMaxAttributeValueLength()),
        totalAttributeCount))
    this
  }

  private def addLink(link: LinkData): Unit = {
    totalNumberOfLinksAdded = totalNumberOfLinksAdded + 1
    linksIO.update { links =>
      val linksTemp = if (links.isEmpty)
        new util.ArrayList(spanLimits.getMaxNumberOfLinks)
      else links

      if (links.size() == spanLimits.getMaxNumberOfLinks)
        linksTemp
      else linksTemp.add(link)
    }
    if (links == null) {
      links = new util.ArrayList(spanLimits.getMaxNumberOfLinks)
    }

    // don't bother doing anything with any links beyond the max.
    if (links.size() == spanLimits.getMaxNumberOfLinks()) {
      return
    }

    links.add(link)
  }

  override def setAttribute(key: String, value: String): SpanBuilder = {
    setAttribute(stringKey(key), value)
  }

  override def setAttribute(key: String, value: Long): SpanBuilder = {
    setAttribute(longKey(key), value)
  }

  override def setAttribute(key: String, value: Double): SpanBuilder = {
    setAttribute(doubleKey(key), value)
  }

  override def setAttribute(key: String, value: Boolean): SpanBuilder = {
    setAttribute(booleanKey(key), value)
  }

  override def setAttribute[T](key: AttributeKey[T], value: T): SpanBuilder = {
    if (key == null || key.getKey().isEmpty() || value == null) {
      return this
    }
    attributes().put(key, value)
    this
  }

  override def setStartTimestamp(startTimestamp: Long, unit: TimeUnit): SpanBuilder = {
    if (startTimestamp < 0 || unit == null) {
      return this
    }
    startEpochNanos = unit.toNanos(startTimestamp)
    this
  }

  override def startSpan(): Span = {
    val parent = parentIO.get.unsafeRunSync()
    val parentContext: Context = parent.getOrElse(Context.current())
    val parentSpan: Span = Span.fromContext(parentContext)
    val parentSpanContext: SpanContext = parentSpan.getSpanContext
    val idGenerator: IdGenerator = tracerSharedState.getIdGenerator()
    val spanId: String = idGenerator.generateSpanId()
    val traceId: String = if (!parentSpanContext.isValid) {
     // New root span.
     idGenerator.generateTraceId()
    } else {
      // New child span.
      parentSpanContext.getTraceId
    }
    val immutableLinks: util.List[LinkData] =
      Collections.unmodifiableList(Option(links).getOrElse(Collections.emptyList()))
    // Avoid any possibility to modify the links list by adding links to the Builder after the
    // startSpan is called. If that happens all the links will be added in a new list.
    links = null
    val immutableAttributes: Attributes = Option(attributes).getOrElse(Attributes.empty())
    val samplingResult: SamplingResult =
      tracerSharedState
        .getSampler()
        .shouldSample(
          parentContext, traceId, spanName, spanKind, immutableAttributes, immutableLinks)
    val samplingDecision: SamplingDecision = samplingResult.getDecision

    val samplingResultTraceState: TraceState =
      samplingResult.getUpdatedTraceState(parentSpanContext.getTraceState)
    val traceFlags = if (isSampled(samplingDecision))
        TraceFlags.getSampled
      else TraceFlags.getDefault
    val spanContext: SpanContext =
      ImmutableSpanContext.create(
        traceId,
        spanId,
        traceFlags,
        samplingResultTraceState,
        false,
        tracerSharedState.isIdGeneratorSafeToSkipIdValidation())

    if (!isRecording(samplingDecision)) {
      Span.wrap(spanContext)
    }
    val samplingAttributes: Attributes = samplingResult.getAttributes
    if (!samplingAttributes.isEmpty) {
      samplingAttributes.forEach { (key: AttributeKey, value: Any) => attributes().put(key, value)}
    }

    // Avoid any possibility to modify the attributes by adding attributes to the Builder after the
    // startSpan is called. If that happens all the attributes will be added in a new map.
    val recordedAttributes: AttributesMap = attributes
    attributes = null

    SdkSpan.startSpan(
      spanContext,
      spanName,
      instrumentationScopeInfo,
      spanKind,
      parentSpan,
      parentContext,
      spanLimits,
      tracerSharedState.getActiveSpanProcessor(),
      tracerSharedState.getClock(),
      tracerSharedState.getResource(),
      recordedAttributes,
      immutableLinks,
      totalNumberOfLinksAdded,
      startEpochNanos)
  }

  private def attributes(): AttributesMap = {
  val attributes = this.attributes
  if (attributes == null) {
    this.attributes =
      AttributesMap.create(
        spanLimits.getMaxNumberOfAttributes(), spanLimits.getMaxAttributeValueLength());
    attributes = this.attributes;
  }
    attributes
  }

  // Visible for testing
  def isRecording(decision: SamplingDecision): Boolean = {
  SamplingDecision.RECORD_ONLY.equals(decision)
    || SamplingDecision.RECORD_AND_SAMPLE.equals(decision)
  }

  // Visible for testing
  def isSampled(decision: SamplingDecision): Boolean = {
  SamplingDecision.RECORD_AND_SAMPLE.equals(decision)
  }
}

object CatsSpanBuilder {

}
