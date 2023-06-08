/*
 *  Copyright 2021 - 2023 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.moditect.jfranalytics;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JfrSchemaFactoryTest {

    @Test
    public void canRetrieveTables() throws Exception {
        try (Connection connection = getConnection("basic.jfr")) {
            DatabaseMetaData md = connection.getMetaData();
            try (ResultSet rs = md.getTables(null, "%", "%", null)) {
                Set<String> tableNames = new HashSet<>();

                while (rs.next()) {
                    tableNames.add(rs.getString(3));
                }

                assertThat(tableNames).contains("jdk.GarbageCollection", "jdk.ThreadSleep", "jfrunit.Sync");
            }

            try (ResultSet rs = md.getColumns(null, "JFR", "jdk.ThreadSleep", null)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("startTime").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("TIMESTAMP(0)").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("duration").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("BIGINT").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("eventThread").describedAs("column name");
                assertThat(rs.getString(6)).startsWith("RecordType").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("stackTrace").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("OTHER").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("time").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("BIGINT").describedAs("type name");

                assertThat(rs.next()).isFalse();
            }
        }

        try (Connection connection = getConnection("data-types.jfr")) {
            DatabaseMetaData md = connection.getMetaData();
            try (ResultSet rs = md.getTables(null, "%", "%", null)) {
                Set<String> tableNames = new HashSet<>();

                while (rs.next()) {
                    tableNames.add(rs.getString(3));
                }

                assertThat(tableNames).contains("test.DataTypes");
            }

            try (ResultSet rs = md.getColumns(null, "JFR", "test.DataTypes", null)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("startTime").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("TIMESTAMP(0)").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("duration").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("BIGINT").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("eventThread").describedAs("column name");
                assertThat(rs.getString(6)).startsWith("RecordType").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("stackTrace").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("OTHER").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("someBoolean").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("BOOLEAN").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("someChar").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("CHAR(1)").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("someByte").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("TINYINT").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("someShort").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("SMALLINT").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("someInt").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("INTEGER").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("someLong").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("BIGINT").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("someFloat").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("REAL").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("someDouble").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("DOUBLE").describedAs("type name");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(4)).isEqualTo("someString").describedAs("column name");
                assertThat(rs.getString(6)).isEqualTo("VARCHAR").describedAs("type name");

                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    public void canSelectDifferentDataTypes() throws Exception {
        try (Connection connection = getConnection("data-types.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM jfr."test.DataTypes"
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();

                assertThat(rs.getTimestamp(1)).isEqualTo(Timestamp.from(ZonedDateTime.parse("2021-12-28T17:10:09.724000000+01:00").toInstant()));
                assertThat(rs.getBoolean(5)).isTrue();
                assertThat(rs.getString(6)).isEqualTo("X");
                assertThat(rs.getByte(7)).isEqualTo(Byte.MAX_VALUE);
                assertThat(rs.getShort(8)).isEqualTo(Short.MAX_VALUE);
                assertThat(rs.getInt(9)).isEqualTo(Integer.MAX_VALUE);
                assertThat(rs.getLong(10)).isEqualTo(Long.MAX_VALUE);
                assertThat(rs.getFloat(11)).isEqualTo(Float.MAX_VALUE);
                assertThat(rs.getDouble(12)).isEqualTo(Double.MAX_VALUE);
                assertThat(rs.getString(13)).isEqualTo("SQL rockz");

                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    public void canRunSimpleSelectFromThreadSleep() throws Exception {
        try (Connection connection = getConnection("basic.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT "startTime", "time", ("eventThread")."javaName", TRUNCATE_STACKTRACE("stackTrace", 7)
                    FROM jfr."jdk.ThreadSleep"
                    WHERE "time" = 1000000000
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();

                assertThat(rs.getTimestamp(1)).isEqualTo(Timestamp.from(ZonedDateTime.parse("2021-12-23T13:40:50.402000000Z").toInstant()));
                assertThat(rs.getLong(2)).isEqualTo(1_000_000_000L);
                assertThat(rs.getString(3)).isEqualTo("main");

                assertThat(rs.getString(4)).isEqualTo("""
                        java.lang.Thread.sleep(long)
                        org.moditect.jfrunit.demos.todo.HelloJfrUnitTest.basicTest():24
                        jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Method, Object, Object[])
                        jdk.internal.reflect.NativeMethodAccessorImpl.invoke(Object, Object[]):77
                        jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(Object, Object[]):43
                        java.lang.reflect.Method.invoke(Object, Object[]):568
                        org.junit.platform.commons.util.ReflectionUtils.invokeMethod(Method, Object, Object[]):688
                        """);
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    public void canRunSimpleSelectFromGarbageCollection() throws Exception {
        try (Connection connection = getConnection("basic.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT "startTime", "duration", "gcId", "name", "cause", "sumOfPauses", "longestPause"
                    FROM jfr."jdk.GarbageCollection"
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();

                assertThat(rs.getTimestamp(1)).isEqualTo(Timestamp.from(ZonedDateTime.parse("2021-12-23T13:40:50.384000000Z").toInstant()));
                assertThat(rs.getLong(2)).isEqualTo(17717731L);
                assertThat(rs.getInt(3)).isEqualTo(2);
                assertThat(rs.getString(4)).isEqualTo("G1Full");
                assertThat(rs.getString(5)).isEqualTo("System.gc()");
                assertThat(rs.getLong(6)).isEqualTo(17717730L);
                assertThat(rs.getLong(7)).isEqualTo(17717730L);
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    public void canRunSimpleSelectFromClassLoad() throws Exception {
        try (Connection connection = getConnection("class-loading.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT "startTime", "loadedClass", "initiatingClassLoader", "definingClassLoader"
                    FROM jfr."jdk.ClassLoad"
                    ORDER by "startTime"
                    LIMIT 1
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();

                assertThat(rs.getTimestamp(1)).isEqualTo(Timestamp.from(ZonedDateTime.parse("2021-12-26T17:32:45.428000000+01:00").toInstant()));
                assertThat(rs.getString(2)).isEqualTo("""
                        {
                          classLoader = null
                          name = "java/lang/Throwable"
                          package = {
                            name = "java/lang"
                            module = {
                              name = "java.base"
                              version = "17"
                              location = "jrt:/java.base"
                              classLoader = null
                            }
                            exported = true
                          }
                          modifiers = 33
                          hidden = false
                        }
                        """);
                assertThat(rs.getString(3)).isEqualTo("platform");
                assertThat(rs.getString(4)).isEqualTo("bootstrap");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    public void canRunSimpleSelectFromGcConfiguration() throws Exception {
        try (Connection connection = getConnection("gc-configuration.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT *
                    FROM jfr."jdk.GCConfiguration"
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();

                assertThat(rs.getTimestamp(1)).isEqualTo(Timestamp.from(ZonedDateTime.parse("2021-12-28T16:13:32.114000000+01:00").toInstant()));
                assertThat(rs.getString(2)).isEqualTo("G1New");
                assertThat(rs.getString(3)).isEqualTo("G1Old");
                assertThat(rs.getInt(4)).isEqualTo(10);
                assertThat(rs.getInt(5)).isEqualTo(3);
                assertThat(rs.getBoolean(6)).isTrue();
                assertThat(rs.getBoolean(7)).isFalse();
                assertThat(rs.getBoolean(8)).isFalse();
                assertThat(rs.getLong(9)).isEqualTo(Long.MIN_VALUE);
                assertThat(rs.getInt(10)).isEqualTo(12);

                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    public void canUseGetClassNameFunction() throws Exception {
        try (Connection connection = getConnection("class-loading.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT CLASS_NAME("loadedClass") as className
                    FROM jfr."jdk.ClassLoad"
                    ORDER by "startTime"
                    LIMIT 1
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("java.lang.Throwable");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    public void canRunAggregations() throws Exception {
        try (Connection connection = getConnection("basic.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT count(*), sum("time")
                    FROM jfr."jdk.ThreadSleep"
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(51);
                assertThat(rs.getLong(2)).isEqualTo(5_850_000_000L);
                assertThat(rs.next()).isFalse();
            }
        }

        try (Connection connection = getConnection("class-loading.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT "definingClassLoader", count(*) as loadedClasses
                    FROM jfr."jdk.ClassLoad"
                    GROUP BY "definingClassLoader"
                    ORDER BY loadedClasses DESC
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("io.quarkus.bootstrap.classloading.QuarkusClassLoader");
                assertThat(rs.getLong(2)).isEqualTo(728);

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("bootstrap");
                assertThat(rs.getLong(2)).isEqualTo(625);

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("platform");
                assertThat(rs.getLong(2)).isEqualTo(388);

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isNull();
                assertThat(rs.getLong(2)).isEqualTo(41);

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("app");
                assertThat(rs.getLong(2)).isEqualTo(1);

                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    public void canRunSimpleSelectFromObjectAllocation() throws Exception {
        try (Connection connection = getConnection("object-allocations.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                      SELECT TRUNCATE_STACKTRACE("stackTrace", 40), SUM("weight")
                      FROM jfr."jdk.ObjectAllocationSample"
                      WHERE "startTime" > (SELECT "startTime" FROM jfr."jfrunit.Reset")
                      GROUP BY TRUNCATE_STACKTRACE("stackTrace", 40)
                      ORDER BY SUM("weight") DESC
                      LIMIT 10
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).startsWith("java.io.BufferedReader.<init>(Reader, int):106");
                assertThat(rs.getLong(2)).isEqualTo(311214384);
            }
        }
    }

    @Test
    public void canUseHasMatchingFrameFunction() throws Exception {
        try (Connection connection = getConnection("object-allocations.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                      SELECT TRUNCATE_STACKTRACE("stackTrace", 10)
                      FROM jfr."jdk.ObjectAllocationSample"
                      WHERE "startTime" > (SELECT "startTime" FROM jfr."jfrunit.Reset")
                        AND HAS_MATCHING_FRAME("stackTrace", '.*java\\.util\\.ArrayList\\.addAll.*')
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                int size = 0;
                while (rs.next()) {
                    size++;
                }

                assertThat(size).isEqualTo(73);
            }
        }
    }

    @Test
    public void canJoinThreadStartAndStop() throws Exception {
        try (Connection connection = getConnection("thread-start-stop.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                      SELECT ts."parentThread"."javaName", ts."thread"."javaName", ts."thread"."javaThreadId", te."thread"."javaName", te."thread"."javaThreadId"
                      FROM jfr."jdk.ThreadStart" ts
                      LEFT JOIN jfr."jdk.ThreadEnd" te ON ts."thread"."javaThreadId" = te."thread"."javaThreadId"
                      ORDER BY ts."thread"."javaThreadId"
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("main");
                assertThat(rs.getString(2)).isEqualTo("pool-1-thread-1");
                assertThat(rs.getLong(3)).isEqualTo(21L);
                assertThat(rs.getString(4)).isEqualTo("pool-1-thread-1");
                assertThat(rs.getLong(5)).isEqualTo(21L);

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("main");
                assertThat(rs.getString(2)).isEqualTo("pool-1-thread-2");
                assertThat(rs.getLong(3)).isEqualTo(22L);
                assertThat(rs.getString(4)).isEqualTo("pool-1-thread-2");
                assertThat(rs.getLong(5)).isEqualTo(22L);

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("Signal Dispatcher");
                assertThat(rs.getString(2)).isEqualTo("Attach Listener");
                assertThat(rs.getLong(3)).isEqualTo(23L);
                assertThat(rs.getString(4)).isNull();
                assertThat(rs.getObject(5)).isNull();

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("Attach Listener");
                assertThat(rs.getString(2)).isEqualTo("RMI TCP Accept-0");
                assertThat(rs.getLong(3)).isEqualTo(24L);
                assertThat(rs.getString(4)).isNull();
                assertThat(rs.getObject(5)).isNull();

                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    public void canReadAsyncProfilerWallProfile() throws Exception {
        try (Connection connection = getConnection("async-profiler-wall.jfr")) {
            PreparedStatement statement = connection.prepareStatement("""
                      SELECT COUNT(*)
                      FROM jfr."jdk.ExecutionSample"
                    """);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(428);
            }
        }
    }

    private Connection getConnection(String jfrFileName) throws SQLException {
        Path jfrFile = getTestResource(jfrFileName);

        Properties properties = new Properties();
        properties.put("model", JfrSchemaFactory.getInlineModel(jfrFile));

        return DriverManager.getConnection("jdbc:calcite:", properties);
    }

    private Path getTestResource(String resource) {
        try {
            Path path = Path.of(JfrSchemaFactoryTest.class.getResource("/" + resource).toURI());

            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Couldn't find resource: " + path);
            }

            return path;
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
