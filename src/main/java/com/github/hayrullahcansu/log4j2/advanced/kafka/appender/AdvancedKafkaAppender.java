/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */

package com.github.hayrullahcansu.log4j2.advanced.kafka.appender;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.core.AbstractLifeCycle;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.layout.SerializedLayout;

import static org.apache.logging.log4j.core.layout.PatternLayout.DEFAULT_CONVERSION_PATTERN;
import static org.apache.logging.log4j.core.layout.PatternLayout.newSerializerBuilder;

/**
 * Sends log events to an Apache Kafka topic.
 */
@Plugin(name = "AdvancedKafka", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class AdvancedKafkaAppender extends AbstractAppender {


    private AbstractStringLayout.Serializer eventSerializer;
    private Layout<? extends Serializable> topicLayout;
    final static private String DefaultTopic = "advanced-kafka-appender";

    /**
     * Builds AdvancedKafkaAppender instances.
     *
     * @param <B> The type to build
     */
    public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<AdvancedKafkaAppender> {

        @PluginAttribute("topic")
        private String topic;

        @PluginBuilderAttribute
        @Required
        private String topicPattern;

        @PluginAttribute("key")
        private String key;

        @PluginAttribute(value = "syncSend", defaultBoolean = true)
        private boolean syncSend;

        @PluginElement("Properties")
        private Property[] properties;

        @SuppressWarnings("resource")
        @Override
        public AdvancedKafkaAppender build() {
            final Layout<? extends Serializable> layout = getLayout();
            if (layout == null) {
                AbstractLifeCycle.LOGGER.error("No layout provided for AdvancedKafkaAppender");
                return null;
            }
            final AdvancedKafkaManager advancedKafkaManager =
                    new AdvancedKafkaManager(getConfiguration().getLoggerContext(), getName(), syncSend, properties, key);
            return new AdvancedKafkaAppender(getName(), layout, getFilter(), isIgnoreExceptions(), advancedKafkaManager, topic,topicPattern);
        }

        public String getTopic() {
            return topic;
        }

        public boolean isSyncSend() {
            return syncSend;
        }

        public Property[] getProperties() {
            return properties;
        }

        public B setTopic(final String topic) {
            this.topic = topic;
            return asBuilder();
        }

        public B setSyncSend(final boolean syncSend) {
            this.syncSend = syncSend;
            return asBuilder();
        }

        public B setProperties(final Property[] properties) {
            this.properties = properties;
            return asBuilder();
        }
    }

    @Deprecated
    public static AdvancedKafkaAppender createAppender(
            final Layout<? extends Serializable> layout,
            final Filter filter,
            final String name,
            final boolean ignoreExceptions,
            final String topicPattern,
            final Property[] properties,
            final Configuration configuration,
            final String key) {

        if (layout == null) {
            AbstractLifeCycle.LOGGER.error("No layout provided for AdvancedKafkaAppender");
            return null;
        }
        final AdvancedKafkaManager advancedKafkaManager =
                new AdvancedKafkaManager(configuration.getLoggerContext(), name, true, properties, key);
        return new AdvancedKafkaAppender(name, layout, filter, ignoreExceptions, advancedKafkaManager, DefaultTopic, topicPattern);
    }

    /**
     * Creates a builder for a AdvancedKafkaAppender.
     *
     * @return a builder for a AdvancedKafkaAppender.
     */
    @PluginBuilderFactory
    public static <B extends Builder<B>> B newBuilder() {
        return new Builder<B>().asBuilder();
    }

    private final AdvancedKafkaManager manager;

    private AdvancedKafkaAppender(final String name, final Layout<? extends Serializable> layout, final Filter filter,
                                  final boolean ignoreExceptions, final AdvancedKafkaManager manager,final String topic, final String topicPattern) {
        super(name, filter, layout, ignoreExceptions);
        this.manager = Objects.requireNonNull(manager, "manager");

        this.eventSerializer = newSerializerBuilder()
                .setAlwaysWriteExceptions(true)
                .setDisableAnsi(false)
                .setNoConsoleNoAnsi(false)
                .setPattern(topicPattern)
                .setDefaultPattern(DEFAULT_CONVERSION_PATTERN)
                .build();
        topicLayout = PatternLayout.newBuilder()
                .withPattern(topicPattern)
                .withConfiguration(new DefaultConfiguration())
                .build();
        System.out.println("hayro reis reis reis");
    }

    @Override
    public void append(final LogEvent event) {
        if (event.getLoggerName() != null && event.getLoggerName().startsWith("org.apache.kafka")) {
            LOGGER.warn("Recursive logging from [{}] for appender [{}].", event.getLoggerName(), getName());
        } else {
            try {
                tryAppend(event);
            } catch (final Exception e) {
                error("Unable to write to Kafka in appender [" + getName() + "]", event, e);
            }
        }
    }

    private void tryAppend(final LogEvent event) throws ExecutionException, InterruptedException, TimeoutException {
        final Layout<? extends Serializable> layout = getLayout();
        byte[] data;

        if (layout instanceof SerializedLayout) {
            final byte[] header = layout.getHeader();
            final byte[] body = layout.toByteArray(event);
            data = new byte[header.length + body.length];
            System.arraycopy(header, 0, data, 0, header.length);
            System.arraycopy(body, 0, data, header.length, body.length);
        } else {
            data = layout.toByteArray(event);
        }
        String topic = getTopicFromEvent(event);
        manager.send(topic, data);
    }

    @Override
    public void start() {
        super.start();
        manager.startup();
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        setStopping();
        boolean stopped = super.stop(timeout, timeUnit, false);
        stopped &= manager.stop(timeout, timeUnit);
        setStopped();
        return stopped;
    }

    @Override
    public String toString() {
        return "AdvancedKafkaAppender{" +
                "name=" + getName() +
                ", state=" + getState() +
                ", topic=" + +
                '}';
    }

    private String getTopicFromEvent(final LogEvent event) {
        byte[] data;
        String topic = "";
        if (topicLayout instanceof SerializedLayout) {
            final byte[] header = topicLayout.getHeader();
            final byte[] body = topicLayout.toByteArray(event);
            data = new byte[header.length + body.length];
            System.arraycopy(header, 0, data, 0, header.length);
            System.arraycopy(body, 0, data, header.length, body.length);
        } else {
            data = topicLayout.toByteArray(event);
        }
        try {
            return new String(data,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            return DefaultTopic;
        }
    }
}
