/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */
 
/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.opensearch.storm.cfg;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.opensearch.hadoop.cfg.Settings;
import org.opensearch.hadoop.util.IOUtils;
import org.opensearch.hadoop.util.StringUtils;
import org.opensearch.hadoop.util.unit.Booleans;

public class StormSettings extends Settings {

    private final Map<Object, Object> cfg;

    public StormSettings(Map<?, ?> settings) {
        // the Storm APersistentMap is read-only so make a copy
        this.cfg = new LinkedHashMap<Object, Object>(settings);
    }

    public boolean getStormTickTupleFlush() {
        return Booleans.parseBoolean(getProperty(StormConfigurationOptions.OPENSEARCH_STORM_BOLT_TICK_TUPLE_FLUSH, StormConfigurationOptions.OPENSEARCH_STORM_BOLT_TICK_TUPLE_FLUSH_DEFAULT));
    }

    public boolean getStormBoltAck() {
        return Booleans.parseBoolean(getProperty(StormConfigurationOptions.OPENSEARCH_STORM_BOLT_ACK, StormConfigurationOptions.OPENSEARCH_STORM_BOLT_ACK_DEFAULT));
    }

    public int getStormBulkSize() {
        String value = getProperty(StormConfigurationOptions.OPENSEARCH_STORM_BOLT_FLUSH_ENTRIES_SIZE);
        if (StringUtils.hasText(value)) {
            return Integer.valueOf(value);
        }
        return getBatchSizeInEntries();
    }

    public boolean getStormSpoutReliable() {
        return Booleans.parseBoolean(getProperty(StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_RELIABLE, StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_RELIABLE_DEFAULT));
    }

    public int getStormSpoutReliableQueueSize() {
        return Integer.parseInt(getProperty(StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_RELIABLE_QUEUE_SIZE, StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_RELIABLE_QUEUE_SIZE_DEFAULT));
    }

    public int getStormSpoutReliableRetriesPerTuple() {
        return Integer.parseInt(getProperty(StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_RELIABLE_RETRIES_PER_TUPLE, StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_RELIABLE_RETRIES_PER_TUPLE_DEFAULT));
    }

    public TupleFailureHandling getStormSpoutReliableTupleFailureHandling() {
        return TupleFailureHandling.valueOf(getProperty(StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_RELIABLE_TUPLE_FAILURE_HANDLE, StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_RELIABLE_TUPLE_FAILURE_HANDLE_DEFAULT).toUpperCase(Locale.ENGLISH));
    }

    public List<String> getStormSpoutFields() {
        return StringUtils.tokenize(getProperty(StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_FIELDS, StormConfigurationOptions.OPENSEARCH_STORM_SPOUT_FIELDS_DEFAULT));
    }

    public int getNimbusCredentialRenewersFrequencySeconds() {
        Object seconds = cfg.get("nimbus.credential.renewers.freq.secs");
        if (seconds == null) {
            return -1;
        } else if (seconds instanceof Number) {
            return ((Number) seconds).intValue();
        } else {
            return Integer.parseInt(seconds.toString());
        }
    }

    @Override
    public InputStream loadResource(String location) {
        return IOUtils.open(location);
    }

    @Override
    public Settings copy() {
        return new StormSettings(new LinkedHashMap<Object, Object>(cfg));
    }

    @Override
    public String getProperty(String name) {
        Object value = cfg.get(name);
        return (value != null ? value.toString() : null);

    }

    @Override
    public void setProperty(String name, String value) {
        cfg.put(name, value);
    }

    @Override
    public Properties asProperties() {
        Properties props = new Properties();

        if (cfg != null) {
            for (Entry<Object, Object> entry : cfg.entrySet()) {
                if (entry.getKey() instanceof String) {
                    Object value = entry.getValue();
                    if (value == null) {
                        value = StringUtils.EMPTY;
                    }
                    props.put(entry.getKey().toString(), value.toString());
                }
            }
        }
        return props;
    }
}