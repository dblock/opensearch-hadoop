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
package org.opensearch.hadoop.integration.pig;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.LogFactory;
import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.executionengine.ExecJob;
import org.opensearch.hadoop.OpenSearchHadoopIllegalStateException;
import org.opensearch.hadoop.HdpBootstrap;
import org.opensearch.hadoop.QueryTestParams;
import org.opensearch.hadoop.util.StringUtils;
import org.junit.rules.LazyTempFolder;

/**
 * Wrapper around Pig.
 */
public class PigWrapper {

    private PigServer pig;
    private final LazyTempFolder stagingDir;

    public PigWrapper(LazyTempFolder stagingDir) {
        this.stagingDir = stagingDir;
    }

    public void start() {
        try {
            pig = createPig();
        } catch (ExecException ex) {
            throw new OpenSearchHadoopIllegalStateException("Cannot create pig server", ex);
        }
        pig.setBatchOn();
    }

    protected PigServer createPig() throws ExecException {
        HdpBootstrap.hackHadoopStagingOnWin();

        Properties properties = HdpBootstrap.asProperties(new QueryTestParams(stagingDir).provisionQueries(HdpBootstrap.hadoopConfig()));
        String pigHost = properties.getProperty("pig");
        // remote Pig instance
        if (StringUtils.hasText(pigHost) && !"local".equals(pigHost)) {
            LogFactory.getLog(PigWrapper.class).info("Executing Pig in Map/Reduce mode");
            return new PigServer(ExecType.MAPREDUCE, properties);
        }

        // use local instance
        LogFactory.getLog(PigWrapper.class).info("Executing Pig in local mode");
        properties.put("mapred.job.tracker", "local");
        return new PigServer(ExecType.LOCAL, properties);
    }

    public void stop() {
        // close pig
        if (pig != null) {
            pig.shutdown();
            pig = null;
        }
    }

    public void executeScript(String script) throws Exception {
        pig.registerScript(new ByteArrayInputStream(script.getBytes()));
        try {
            List<ExecJob> executeBatch = pig.executeBatch();
            for (ExecJob execJob : executeBatch) {
                if (execJob.getStatus() == ExecJob.JOB_STATUS.FAILED) {
                    throw new OpenSearchHadoopIllegalStateException("Pig execution failed", execJob.getException());
                }
            }
        } finally {
            pig.discardBatch();
            pig.setBatchOn();
        }
    }
}