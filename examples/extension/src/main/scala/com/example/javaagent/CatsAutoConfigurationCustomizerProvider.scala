package com.example.javaagent

import com.google.auto.service.AutoService
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.SpanLimits
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import java.util.HashMap
import java.util.Map

/**
 * This is one of the main entry points for Instrumentation Agent's customizations. It allows
 * configuring the {@link AutoConfigurationCustomizer}. See the {@link
 * #customize(AutoConfigurationCustomizer)} method below.
 *
 * <p>Also see https://github.com/open-telemetry/opentelemetry-java/issues/2022
 *
 * @see AutoConfigurationCustomizerProvider
 * @see DemoPropagatorProvider
 */
object CatsAutoConfigurationCustomizerProvider extends AutoConfigurationCustomizerProvider {

  override def customize(autoConfiguration: AutoConfigurationCustomizer): Unit = {
    autoConfiguration
      .addTracerProviderCustomizer(this.configureSdkTracerProvider)
  }

  private def configureSdkTracerProvider(tracerProvider: SdkTracerProviderBuilder, config: ConfigProperties): SdkTracerProviderBuilder = {
    tracerProvider
      .setIdGenerator(new CatsIdGenerator())
  }

}
