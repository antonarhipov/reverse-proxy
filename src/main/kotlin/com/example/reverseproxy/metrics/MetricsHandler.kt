package com.example.reverseproxy.metrics

import io.ktor.server.application.*
import io.ktor.server.metrics.dropwizard.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import com.codahale.metrics.*
import com.codahale.metrics.jvm.*
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Handles metrics collection for the reverse proxy.
 */
class MetricsHandler {
    private val logger = LoggerFactory.getLogger(MetricsHandler::class.java)

    // Create a metrics registry
    private val metricRegistry = MetricRegistry()

    // Create meters for different HTTP methods
    private val getRequests = metricRegistry.meter("http.requests.get")
    private val postRequests = metricRegistry.meter("http.requests.post")
    private val putRequests = metricRegistry.meter("http.requests.put")
    private val deleteRequests = metricRegistry.meter("http.requests.delete")
    private val otherRequests = metricRegistry.meter("http.requests.other")

    // Create timers for response times
    private val responseTimer = metricRegistry.timer("http.response.time")

    // Create meters for response status codes
    private val status2xxResponses = metricRegistry.meter("http.responses.2xx")
    private val status3xxResponses = metricRegistry.meter("http.responses.3xx")
    private val status4xxResponses = metricRegistry.meter("http.responses.4xx")
    private val status5xxResponses = metricRegistry.meter("http.responses.5xx")

    /**
     * Configures metrics collection for the application.
     * @param application The Application to configure.
     */
    fun configureMetrics(application: Application) {
        // Register JVM metrics
        registerJvmMetrics()

        // Install the DropwizardMetrics plugin
        application.install(DropwizardMetrics) {
            // Configure the registry
            registry = metricRegistry

            // Configure SLF4J reporter
            val slf4jReporter = Slf4jReporter.forRegistry(metricRegistry)
                .outputTo(LoggerFactory.getLogger("metrics"))
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build()
            slf4jReporter.start(1, TimeUnit.MINUTES)
        }

        // Add an interceptor for custom metrics
        application.intercept(ApplicationCallPipeline.Monitoring) {
            // Record request metrics
            when (call.request.local.method.value.uppercase()) {
                "GET" -> getRequests.mark()
                "POST" -> postRequests.mark()
                "PUT" -> putRequests.mark()
                "DELETE" -> deleteRequests.mark()
                else -> otherRequests.mark()
            }

            // Measure response time
            val timer = responseTimer.time()

            try {
                proceed()

                // Record response status code metrics
                val statusCode = call.response.status()?.value ?: 0
                when (statusCode) {
                    in 200..299 -> status2xxResponses.mark()
                    in 300..399 -> status3xxResponses.mark()
                    in 400..499 -> status4xxResponses.mark()
                    in 500..599 -> status5xxResponses.mark()
                }
            } finally {
                // Stop the timer
                timer.stop()
            }
        }

        // Add a route to expose metrics
        application.routing {
            get("/metrics") {
                // Return metrics in a simple text format
                val reporter = StringReporter.forRegistry(metricRegistry)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build()

                val metrics = reporter.getMetricsText()
                call.respondText(metrics)
            }
        }

        logger.info("Metrics collection configured")
    }

    /**
     * Gets the metric registry.
     * @return The metric registry.
     */
    fun getMetricRegistry(): MetricRegistry {
        return metricRegistry
    }

    /**
     * Registers JVM metrics with the registry.
     */
    private fun registerJvmMetrics() {
        // Register JVM metrics
        metricRegistry.register("jvm.memory", MemoryUsageGaugeSet())
        metricRegistry.register("jvm.garbage", GarbageCollectorMetricSet())
        metricRegistry.register("jvm.threads", ThreadStatesGaugeSet())
        metricRegistry.register("jvm.files", FileDescriptorRatioGauge())
        metricRegistry.register("jvm.classloader", ClassLoadingGaugeSet())
    }

    /**
     * A simple reporter that returns metrics as a string.
     */
    private class StringReporter private constructor(
        private val registry: MetricRegistry,
        private val rateUnit: TimeUnit,
        private val durationUnit: TimeUnit
    ) {
        companion object {
            fun forRegistry(registry: MetricRegistry): Builder {
                return Builder(registry)
            }
        }

        class Builder(private val registry: MetricRegistry) {
            private var rateUnit: TimeUnit = TimeUnit.SECONDS
            private var durationUnit: TimeUnit = TimeUnit.MILLISECONDS

            fun convertRatesTo(rateUnit: TimeUnit): Builder {
                this.rateUnit = rateUnit
                return this
            }

            fun convertDurationsTo(durationUnit: TimeUnit): Builder {
                this.durationUnit = durationUnit
                return this
            }

            fun build(): StringReporter {
                return StringReporter(registry, rateUnit, durationUnit)
            }
        }

        fun getMetricsText(): String {
            val sb = StringBuilder()

            // Add gauges
            registry.gauges.forEach { (name, gauge) ->
                sb.appendLine("$name: ${gauge.value}")
            }

            // Add counters
            registry.counters.forEach { (name, counter) ->
                sb.appendLine("$name: ${counter.count}")
            }

            // Add meters
            registry.meters.forEach { (name, meter) ->
                sb.appendLine("$name:")
                sb.appendLine("  count: ${meter.count}")
                sb.appendLine("  mean rate: ${meter.meanRate} events/${rateUnit.name.lowercase()}")
                sb.appendLine("  1-minute rate: ${meter.oneMinuteRate} events/${rateUnit.name.lowercase()}")
                sb.appendLine("  5-minute rate: ${meter.fiveMinuteRate} events/${rateUnit.name.lowercase()}")
                sb.appendLine("  15-minute rate: ${meter.fifteenMinuteRate} events/${rateUnit.name.lowercase()}")
            }

            // Add timers
            registry.timers.forEach { (name, timer) ->
                sb.appendLine("$name:")
                sb.appendLine("  count: ${timer.count}")
                sb.appendLine("  min: ${timer.snapshot.min / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  max: ${timer.snapshot.max / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  mean: ${timer.snapshot.mean / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  stddev: ${timer.snapshot.stdDev / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  median: ${timer.snapshot.median / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  75%: ${timer.snapshot.get75thPercentile() / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  95%: ${timer.snapshot.get95thPercentile() / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  98%: ${timer.snapshot.get98thPercentile() / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  99%: ${timer.snapshot.get99thPercentile() / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  99.9%: ${timer.snapshot.get999thPercentile() / durationUnit.toNanos(1)} ${durationUnit.name.lowercase()}")
                sb.appendLine("  mean rate: ${timer.meanRate} calls/${rateUnit.name.lowercase()}")
                sb.appendLine("  1-minute rate: ${timer.oneMinuteRate} calls/${rateUnit.name.lowercase()}")
                sb.appendLine("  5-minute rate: ${timer.fiveMinuteRate} calls/${rateUnit.name.lowercase()}")
                sb.appendLine("  15-minute rate: ${timer.fifteenMinuteRate} calls/${rateUnit.name.lowercase()}")
            }

            return sb.toString()
        }
    }
}
