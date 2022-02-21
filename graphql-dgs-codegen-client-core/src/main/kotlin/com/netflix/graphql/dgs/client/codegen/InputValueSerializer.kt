/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.client.codegen

import graphql.schema.Coercing
import org.reflections.ReflectionUtils
import java.lang.reflect.Field
import java.time.*
import java.util.*

class InputValueSerializer(private val scalars: Map<Class<*>, Coercing<*, *>> = emptyMap()) {
    companion object {
        val escapeChars = mapOf(
            '"' to '"',
            '\\' to '\\',
            '/' to '/',
            '\b' to 'b',
            0xC /*'\f'*/ to 'f',
            '\n' to 'n',
            '\r' to 'r',
            '\t' to 't'
        )

        val toStringClasses = setOf(
            String::class.java,
            LocalDateTime::class.java,
            LocalDate::class.java,
            LocalTime::class.java,
            TimeZone::class.java,
            Date::class.java,
            OffsetDateTime::class.java,
            Currency::class.java,
            Instant::class.java
        )
    }

    fun serialize(input: Any?): String {
        if (input == null) {
            return "null"
        }

        val type = input::class.java

        return if (type in scalars) {
            """"${scalars.getValue(type).serialize(input)}""""
        } else if (type.isPrimitive || type.isAssignableFrom(java.lang.Integer::class.java) || type.isAssignableFrom(java.lang.Long::class.java) || type.isAssignableFrom(java.lang.Double::class.java) || type.isAssignableFrom(java.lang.Float::class.java) || type.isAssignableFrom(java.lang.Boolean::class.java) || type.isAssignableFrom(java.lang.Short::class.java) || type.isAssignableFrom(java.lang.Byte::class.java) || type.isEnum) {
            input.toString()
        } else if (type in toStringClasses) {
            // Call toString for known types, in case no scalar is found. This is for backward compatibility.
            val s = input.toString()
            toGraphQLStringLiteral(s)
        } else if (input is List<*>) {
            """[${input.filterNotNull().joinToString(", ") { listItem -> serialize(listItem) }}]"""
        } else if (input is Map<*, *>) {
            input.entries.joinToString(", ", "{ ", " }") { (key, value) ->
                if (value != null) {
                    """$key: ${serialize(value)}"""
                } else {
                    """$key: null"""
                }
            }
        } else {
            val fields = LinkedList<Field>()
            ReflectionUtils.getAllFields(input.javaClass).forEach {
                if (!it.isSynthetic) {
                    it.isAccessible = true
                    fields.add(it)
                }
            }

            fields.filter { !it.type::class.isCompanion }.mapNotNull {
                val nestedValue = it.get(input)
                if (nestedValue != null && nestedValue::class.java == input::class.java) {
                    """${it.name}:$nestedValue"""
                } else if (nestedValue != null && !nestedValue::class.isCompanion) {
                    """${it.name}:${serialize(nestedValue)}"""
                } else {
                    null
                }
            }.joinToString(", ", "{", " }")
        }
    }

    /**
     * https://spec.graphql.org/October2021/#StringCharacter says anything except '"', '\\', or "(?:\n|\r\n|\r)"
     * with emphasis on '"', '\\', '/', '\b', '\f', '\n', '\r', '\t',
     * and then escaping [\x0020-\xFFFF] is player's choice
     *
     * graphql.util.EscapeUtil.escapeJsonString is very close to what we want, but is marked @Internal
     */
    private fun toGraphQLStringLiteral(input: String): String {
        val result = StringBuilder()
        result.append('"')
        input.forEach {
            // DANGER: these **must** be first, since they are special cased
            if (escapeChars.contains(it)) {
                result.append('\\').append(escapeChars[it])
            } else if (it.code < 0x20) {
                // while the spec seems to forbid this, graphql-js accepts them, as does graphql.parser.Parser
                result.append("\\u").append(String.format("%04x", it.code))
            } else {
                result.append(it)
            }
        }
        result.append('"')
        return result.toString()
    }
}
