package com.example.javaagent.instrumentation

import com.example.javaagent.CatsAutoConfigurationCustomizerProvider
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer

import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers.named


class CatsInstrumentation extends TypeInstrumentation {
  override def typeMatcher =
    AgentElementMatchers.implementsInterface(ElementMatchers.named("io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider"))

  override def transform(typeTransformer: TypeTransformer): Unit = {
    typeTransformer.applyAdviceToMethod(
      named("customize")
        .and(ElementMatchers.takesArgument(0, named("io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer")))
        .and(ElementMatchers.isPublic),
      this.getClass.getName + "$CatsInstrumentationAdvice$"
    )
  }

  object CatsInstrumentationAdvice {
    def onEnter(autoConfiguration: AutoConfigurationCustomizer): Unit = {
      CatsAutoConfigurationCustomizerProvider.customize(autoConfiguration)
    }
  }
}
