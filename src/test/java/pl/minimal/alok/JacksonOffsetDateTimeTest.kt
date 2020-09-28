package pl.minimal.alok

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class JacksonOffsetDateTimeTest {
    val json = """{"date":"2020-08-31 12:17:00.000+0200"}"""

    @Test
    fun testAnnotationWithPattern() {
        class Model(@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSXX") val date: OffsetDateTime)
        val mapper = ObjectMapper().also {
            it.registerModule(JavaTimeModule())
            it.registerModule(KotlinModule())
        }
        val model = mapper.readValue<Model>(json)
        val output = mapper.writeValueAsString(model)

        // Looks like jackson applies the offset, so we get correct local time, but offset is lost
        assertEquals("""{"date":"2020-08-31 10:17:00.000Z"}""", output)
    }

    @Test
    fun testCustomSerializer() {
        class Model(val date: OffsetDateTime)
        val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSxx")
        val mapper = ObjectMapper().also {
            it.registerModule(KotlinModule())
            it.registerModule(SimpleModule().also { module ->
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

        val model = mapper.readValue<Model>(json)
        val output = mapper.writeValueAsString(model)

        // Looks like jackson applies the offset, so we get correct local time, but offset is lost
        assertEquals(json, output)
    }
}