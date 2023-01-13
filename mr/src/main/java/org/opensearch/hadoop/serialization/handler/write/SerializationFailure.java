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

package org.opensearch.hadoop.serialization.handler.write;

import java.util.List;

import org.opensearch.hadoop.handler.impl.BaseExceptional;

public class SerializationFailure extends BaseExceptional {
    private final Object record;

    public SerializationFailure(Exception reason, Object record, List<String> passReasons) {
        super(reason, passReasons);
        this.record = record;
    }

    /**
     * @return the record that triggered the exception while being processed for bulk indexing.
     */
    public Object getRecord() {
        return record;
    }
}