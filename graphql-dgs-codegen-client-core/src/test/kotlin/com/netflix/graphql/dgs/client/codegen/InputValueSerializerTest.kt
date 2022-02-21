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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class InputValueSerializerTest {

    @Test
    fun `Serialize a complex object`() {

        val movieInput = MovieInput(
            1,
            "Some movie",
            MovieInput.Genre.ACTION,
            MovieInput.Director("The Director"),
            listOf(
                MovieInput.Actor("Actor 1", "Role 1"),
                MovieInput.Actor("Actor 2", "Role 2"),
            ),
            DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1))
        )

        val serialize = InputValueSerializer(mapOf(DateRange::class.java to DateRangeScalar())).serialize(movieInput)
        assertThat(serialize).isEqualTo("{movieId:1, title:\"Some movie\", genre:ACTION, director:{name:\"The Director\" }, actor:[{name:\"Actor 1\", roleName:\"Role 1\" }, {name:\"Actor 2\", roleName:\"Role 2\" }], releaseWindow:\"01/01/2020-01/01/2021\" }")
    }

    @Test
    fun `test issue 334 with object`() {

        val movieInput = MovieInput(
            1,
            "Some movi\u00e9\u2122",
            MovieInput.Genre.ACTION,
            MovieInput.Director("The \u201cDirector\u201d"),
            listOf(
                MovieInput.Actor("Actor\r1", "Role 1"),
                MovieInput.Actor("Actor 2", "Role\n2"),
            ),
            DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1))
        )

        val serialize = InputValueSerializer(mapOf(DateRange::class.java to DateRangeScalar())).serialize(movieInput)
        assertThat(serialize).isEqualTo("{movieId:1, title:\"Some movié™\", genre:ACTION, director:{name:\"The “Director”\" }, actor:[{name:\"Actor\\r1\", roleName:\"Role 1\" }, {name:\"Actor 2\", roleName:\"Role\\n2\" }], releaseWindow:\"01/01/2020-01/01/2021\" }")
    }

    @Test
    fun `List of a complex object`() {

        val movieInput = MovieInput(
            1,
            "Some movie",
            MovieInput.Genre.ACTION,
            MovieInput.Director("The Director"),
            listOf(
                MovieInput.Actor("Actor 1", "Role 1"),
                MovieInput.Actor("Actor 2", "Role 2"),
            ),
            DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1))
        )

        val serialize = InputValueSerializer(mapOf(DateRange::class.java to DateRangeScalar())).serialize(listOf(movieInput))
        assertThat(serialize).isEqualTo("[{movieId:1, title:\"Some movie\", genre:ACTION, director:{name:\"The Director\" }, actor:[{name:\"Actor 1\", roleName:\"Role 1\" }, {name:\"Actor 2\", roleName:\"Role 2\" }], releaseWindow:\"01/01/2020-01/01/2021\" }]")
    }

    @Test
    fun `Null values should be skipped`() {

        val movieInput = MovieInput(1)

        val serialize = InputValueSerializer(mapOf(DateRange::class.java to DateRangeScalar())).serialize(movieInput)
        assertThat(serialize).isEqualTo("{movieId:1 }")
    }

    @Test
    fun `String value`() {
        val serialize = InputValueSerializer().serialize("some string")
        assertThat(serialize).isEqualTo("\"some string\"")
    }

    @Test
    fun `String with slashes`() {
        val serialize = InputValueSerializer().serialize("some \\ \"string\"")
        assertThat(serialize).isEqualTo("\"some \\\\ \\\"string\\\"\"")
    }

    @Test
    fun `test issue 334`() {
        val serialize = InputValueSerializer().serialize("some\nstring")
        assertThat(serialize).isEqualTo("\"some\\nstring\"")
    }

    @Test
    fun `test issue 334b`() {
        val serialize = InputValueSerializer().serialize("null\u0000string")
        assertThat(serialize).isEqualTo("\"null\\u0000string\"")
    }

    @Test
    fun `test issue 334c`() {
        val serialize = InputValueSerializer().serialize("esc\u001bstring")
        assertThat(serialize).isEqualTo("\"esc\\u001bstring\"")
    }

    @Test
    fun `int value`() {
        val serialize = InputValueSerializer().serialize(1)
        assertThat(serialize).isEqualTo("1")
    }

    @Test
    fun `long value`() {
        val serialize = InputValueSerializer().serialize(1L)
        assertThat(serialize).isEqualTo("1")
    }

    @Test
    fun `boolean value`() {
        val serialize = InputValueSerializer().serialize(true)
        assertThat(serialize).isEqualTo("true")
    }

    @Test
    fun `double value`() {
        val serialize = InputValueSerializer().serialize(1.1)
        assertThat(serialize).isEqualTo("1.1")
    }

    @Test
    fun `Companion objects should be ignored`() {
        val serialize = InputValueSerializer().serialize(MyDataWithCompanion("some title"))
        assertThat(serialize).isEqualTo("{title:\"some title\" }")
    }

    @Test
    fun `List of Integer`() {
        val serialize = InputValueSerializer().serialize(listOf(1, 2, 3))
        assertThat(serialize).isEqualTo("[1, 2, 3]")
    }

    @Test
    fun `Base class properties should be found`() {
        val serialize = InputValueSerializer().serialize(MySubClass("DGS", 1500))
        assertThat(serialize).isEqualTo("{stars:1500, name:\"DGS\" }")
    }

    @Test
    fun `Date without scalar`() {
        val input = WithLocalDateTime(LocalDateTime.of(2021, 5, 13, 4, 34))
        val serialize = InputValueSerializer().serialize(input)
        assertThat(serialize).isEqualTo("""{date:"2021-05-13T04:34" }""")
    }

    data class MyDataWithCompanion(val title: String) {
        companion object
    }

    open class MyBaseClass(private val name: String)

    class MySubClass(name: String, val stars: Int) : MyBaseClass(name)

    class WithLocalDateTime(val date: LocalDateTime)
}
