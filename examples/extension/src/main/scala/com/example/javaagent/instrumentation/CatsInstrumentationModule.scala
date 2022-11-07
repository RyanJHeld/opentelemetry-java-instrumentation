package com.example.javaagent.instrumentation

import java.util.Collections.singletonList
import com.google.auto.service.AutoService
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers
import java.util
import util.Collections.singletonList
import net.bytebuddy.matcher.ElementMatcher


final class CatsInstrumentationModule extends InstrumentationModule("opentelemetry-java-instrumentation-cats-effect-extension") {
  override def typeInstrumentations(): util.List[TypeInstrumentation] =
    singletonList(new CatsInstrumentation())
}
