package pl.minimal.alok

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLContext

/*
Couldn't get it working with pattern @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSxx"), see JacksonOffsetDateTimeTest
 */
data class Worklog(
    val started: OffsetDateTime,
    val timeSpentSeconds: Int,
)

data class WorklogList(val maxResults: Int, val total: Int, val worklogs: List<Worklog>)

class JiraError(message: String) : RuntimeException(message)

interface JiraApi {
    var cookie: String
    fun putWorklog(issue: String, date: LocalDate, timeHours: Double): Pair<String, Worklog>
}

class JiraApiImpl(
    private val user: String = "E.68235581"
) : AutoCloseable, JiraApi {

    override var cookie: String = ""

    private val base = "https://jira.playmobile.pl/jira/rest/api/latest/issue/"

    private val client = HttpClient(Apache) {
        engine {
            sslContext = SSLContext.getDefault()
        }
        install(JsonFeature) {
            serializer = JacksonSerializer() {
                val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx")
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                registerModule(KotlinModule())
                registerModule(SimpleModule().also { module ->
                    module.addSerializer(OffsetDateTime::class.java, object : JsonSerializer<OffsetDateTime>() {
                        override fun serialize(
                            offsetDateTime: OffsetDateTime,
                            jsonGenerator: JsonGenerator,
                            serializerProvider: SerializerProvider
                        ) {
                            jsonGenerator.writeString(dtf.format(offsetDateTime))
                        }
                    })
                    module.addDeserializer(OffsetDateTime::class.java, object : JsonDeserializer<OffsetDateTime>() {
                        override fun deserialize(parser: JsonParser, ctx: DeserializationContext): OffsetDateTime {
                            return OffsetDateTime.from(dtf.parse(parser.readValueAs(String::class.java)))
                        }
                    })
                })
            }
        }
    }


    fun getWorklog(issue: String): List<Worklog> =
        runBlocking {
            try {
                client.get<WorklogList>("$base$issue/worklog") {
                    header("Cookie", cookie)
                    header("X-AUSERNAME", user)
                }.let {
                    if(it.maxResults < it.total) {
                        throw JiraError("Paging not supported, got only ${it.maxResults} out of total ${it.total}")
                    }
                    it.worklogs
                }
            } catch (e: ClientRequestException) {
                if (e.response.status.value == 401) {
                    throw JiraError("Unauthorized (401), did you set the cookie?")
                } else {
                    throw JiraError("Unexpected response: ${e.response.status.value}")
                }
            }
        }


    fun addWorklog(issue: String, date: LocalDate, timeHours: Double): Worklog {
        val worklog = Worklog(
            date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(),
            (timeHours * 3600).toInt()
        )
        return runBlocking {
            try {
                client.post<Unit>("$base$issue/worklog") {
                    body = worklog
                    header("Cookie", cookie)
                    header("X-AUSERNAME", user)
                }
                worklog
            } catch (e: ClientRequestException) {
                if (e.response.status.value == 401) {
                    throw JiraError("Unauthorized (401), did you set the cookie?")
                } else {
                    throw JiraError("Unexpected response: ${e.response.status.value}")
                }
            }
        }
    }

    override fun putWorklog(issue: String, date: LocalDate, timeHours: Double): Pair<String, Worklog> {
        val existing = getWorklog(issue).find { it.started.toLocalDate() == date }
        return if (existing == null) {
            "Added" to addWorklog(issue, date, timeHours)
        } else {
            "Already exists" to existing
        }
    }

    override fun close() {
        client.close()
    }
}
