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
package org.opensearch.hadoop.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.opensearch.hadoop.rest.RestClient;

public class TestUtils {

    public static final String OPENSEARCH_LOCAL_PORT = "opensearch.hadoop.testing.local.opensearch.port";

    public static boolean delete(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        boolean result = true;
        if (file.isDirectory()) {
            String[] children = file.list();
            for (int i = 0; i < children.length; i++) {
                result &= delete(new File(file, children[i]));
            }
        }
        return file.delete() & result;
    }

    public static ClusterInfo getOpenSearchClusterInfo() {
        RestClient client = new RestClient(new TestSettings());
        try {
            return client.mainInfo();
        } finally {
            client.close();
        }
    }

    public static String resource(String index, String type, OpenSearchMajorVersion testVersion) {
        if (TestUtils.isTypelessVersion(testVersion)) {
            return index;
        } else {
            return index + "/" + type;
        }
    }

    public static String docEndpoint(String index, String type, OpenSearchMajorVersion testVersion) {
        if (TestUtils.isTypelessVersion(testVersion)) {
            return index + "/_doc";
        } else {
            return index + "/" + type;
        }
    }

    public static boolean isTypelessVersion(OpenSearchMajorVersion version) {
        // Types have been removed in 2.0.0
        return version.onOrAfter(OpenSearchMajorVersion.V_2_X);
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("win");
    }

    public static byte[] fromInputStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        try {
            while ((length = in.read(buffer)) != -1)
                out.write(buffer, 0, length);
        } finally {
            if (in != null) {
                in.close(); // call this in a finally block
            }
        }

        return out.toByteArray();
    }
}