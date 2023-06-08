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

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelProtoDataType;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.SchemaVersion;
import org.apache.calcite.schema.Schemas;
import org.apache.calcite.schema.Table;
import org.checkerframework.checker.nullness.qual.Nullable;

import jdk.jfr.EventType;
import jdk.jfr.Timespan;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.*;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedStackTrace;

public class JfrSchema implements Schema {

    private static final System.Logger LOGGER = System.getLogger(JfrSchema.class.getName());
    private static final int LOCAL_OFFSET = TimeZone.getDefault().getOffset(System.currentTimeMillis());

    private final Map<String, JfrScannableTable> tableTypes;

    public JfrSchema(Path jfrFile) {
        this.tableTypes = Collections.unmodifiableMap(getTableTypes(jfrFile));
    }

    private static Map<String, JfrScannableTable> getTableTypes(Path jfrFile) {
        try (var es = EventStream.openFile(jfrFile)) {
            RelDataTypeFactory typeFactory = new JavaTypeFactoryImpl();
            Map<String, JfrScannableTable> tableTypes = new HashMap<>();

            es.onMetadata(event -> {
                for (EventType eventType : event.getEventTypes()) {
                    if (!tableTypes.containsKey(eventType.getName())) {
                        RelDataTypeFactory.Builder builder = new RelDataTypeFactory.Builder(typeFactory);
                        List<AttributeValueConverter> converters = new ArrayList<>();

                        for (ValueDescriptor field : eventType.getFields()) {
                            RelDataType type = getRelDataType(eventType, field, typeFactory);
                            if (type == null) {
                                continue;
                            }

                            if (type.getSqlTypeName().toString().equals("ROW")) {
                                builder.add(field.getName(), type).nullable(true);
                            }
                            else {
                                builder.add(field.getName(), type.getSqlTypeName()).nullable(true);
                            }

                            converters.add(getConverter(field, type));
                        }

                        tableTypes.put(eventType.getName(),
                                new JfrScannableTable(jfrFile, eventType, builder.build(), converters.toArray(new AttributeValueConverter[0])));
                    }
                }
            });

            es.start();

            return tableTypes;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RelDataType getRelDataType(EventType eventType, ValueDescriptor field, RelDataTypeFactory typeFactory) {
        RelDataType type;
        switch (field.getTypeName()) {
            case "boolean":
                type = typeFactory.createJavaType(boolean.class);
                break;
            case "char":
                type = typeFactory.createJavaType(char.class);
                break;
            case "byte":
                type = typeFactory.createJavaType(byte.class);
                break;
            case "short":
                type = typeFactory.createJavaType(short.class);
                break;
            case "int":
                type = typeFactory.createJavaType(int.class);
                break;
            case "long":
                if ("jdk.jfr.Timestamp".equals(field.getContentType())) {
                    type = typeFactory.createJavaType(Timestamp.class);
                }
                else {
                    type = typeFactory.createJavaType(long.class);
                }
                break;
            case "float":
                type = typeFactory.createJavaType(float.class);
                break;
            case "double":
                type = typeFactory.createJavaType(double.class);
                break;
            case "java.lang.Class":
                type = typeFactory.createJavaType(RecordedClass.class);
                break;
            case "java.lang.String":
                type = typeFactory.createJavaType(String.class);
                break;
            case "java.lang.Thread":
                List<RelDataType> types = Arrays.asList(typeFactory.createJavaType(String.class), typeFactory.createJavaType(long.class),
                        typeFactory.createJavaType(String.class), typeFactory.createJavaType(long.class), typeFactory.createJavaType(String.class));
                List<String> names = Arrays.asList("osName", "osThreadId", "javaName", "javaThreadId", "group");
                type = typeFactory.createStructType(types, names);
                break;
            case "jdk.types.ClassLoader":
                type = typeFactory.createJavaType(String.class);
                break;
            case "jdk.types.StackTrace":
                type = typeFactory.createJavaType(RecordedStackTrace.class);
                break;
            default:
                LOGGER.log(Level.WARNING, "Unknown type of attribute {0}::{1}: {2}", eventType.getName(), field.getName(), field.getTypeName());
                type = null;
        }
        return type;
    }

    private static AttributeValueConverter getConverter(ValueDescriptor field, RelDataType type) {
        // 1. common attributes

        // timestamps are adjusted by Calcite using local TZ offset; account for that
        if (field.getName().equals("startTime")) {
            return event -> event.getStartTime().toEpochMilli() + LOCAL_OFFSET;
        }
        else if (field.getName().equals("duration")) {
            return event -> event.getDuration().toNanos();
        }
        else if (field.getName().equals("stackTrace")) {
            return event -> event.getStackTrace();
        }

        // 2. special value types
        else if (field.getTypeName().equals("java.lang.Class")) {
            return event -> event.getClass(field.getName());
        }
        else if (field.getTypeName().equals("jdk.types.ClassLoader")) {
            return event -> {
                RecordedClassLoader recordedClassLoader = (RecordedClassLoader) event.getValue(field.getName());

                if (recordedClassLoader == null) {
                    return null;
                }
                else if (recordedClassLoader.getName() != null) {
                    return recordedClassLoader.getName();
                }
                else {
                    RecordedClass classLoaderType = recordedClassLoader.getType();
                    return classLoaderType != null ? classLoaderType.getName() : null;
                }
            };
        }
        else if (field.getTypeName().equals("java.lang.Thread")) {
            return event -> {
                RecordedThread recordedThread = (RecordedThread) event.getValue(field.getName());
                return new Object[]{
                        recordedThread.getOSName(),
                        recordedThread.getOSThreadId(),
                        recordedThread.getJavaName(),
                        recordedThread.getJavaThreadId(),
                        recordedThread.getThreadGroup() != null ? recordedThread.getThreadGroup().getName() : null,
                };
            };
        }
        // 3. further special cases
        else if (field.getAnnotation(Timespan.class) != null) {
            return event -> {
                Duration duration = event.getDuration(field.getName());
                // Long.MIN_VALUE is used as a sentinel value for absent values e.g. for jdk.GCConfiguration.pauseTarget
                // TODO: handle nanos value overflow
                return duration.getSeconds() == Long.MIN_VALUE ? Long.MIN_VALUE : duration.toNanos();
            };
        }
        // 4. default pass-through
        else {
            return event -> event.getValue(field.getName());
        }
    }

    @Override
    public @Nullable Table getTable(String name) {
        return tableTypes.get(name);
    }

    @Override
    public Set<String> getTableNames() {
        return tableTypes.keySet();
    }

    @Override
    public @Nullable RelProtoDataType getType(String name) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Set<String> getTypeNames() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Collection<Function> getFunctions(String name) {
        if (name.equals("CLASS_NAME")) {
            return Collections.singleton(GetClassNameFunction.INSTANCE);
        }
        else if (name.equals("TRUNCATE_STACKTRACE")) {
            return Collections.singleton(TruncateStackTraceFunction.INSTANCE);
        }
        else if (name.equals("HAS_MATCHING_FRAME")) {
            return Collections.singleton(HasMatchingFrameFunction.INSTANCE);
        }

        return Collections.emptySet();
    }

    @Override
    public Set<String> getFunctionNames() {
        return Set.of("CLASS_NAME", "TRUNCATE_STACKTRACE", "HAS_MATCHING_FRAME");
    }

    @Override
    public @Nullable Schema getSubSchema(String name) {
        return null;
    }

    @Override
    public Set<String> getSubSchemaNames() {
        return Collections.emptySet();
    }

    @Override
    public Expression getExpression(@Nullable SchemaPlus parentSchema, String name) {
        return Schemas.subSchemaExpression(parentSchema, name, getClass());
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Schema snapshot(SchemaVersion version) {
        return this;
    }
}
