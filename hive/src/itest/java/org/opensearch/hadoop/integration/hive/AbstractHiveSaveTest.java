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
package org.opensearch.hadoop.integration.hive;

import org.opensearch.hadoop.cfg.ConfigurationOptions;
import org.opensearch.hadoop.rest.RestUtils;
import org.opensearch.hadoop.util.OpenSearchMajorVersion;
import org.opensearch.hadoop.util.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.sql.SQLException;

import static org.opensearch.hadoop.util.TestUtils.docEndpoint;
import static org.opensearch.hadoop.util.TestUtils.resource;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import static org.opensearch.hadoop.integration.hive.HiveSuite.isLocal;
import static org.opensearch.hadoop.integration.hive.HiveSuite.server;
import static org.hamcrest.CoreMatchers.is;

import static org.hamcrest.Matchers.containsString;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AbstractHiveSaveTest {

    private OpenSearchMajorVersion targetVersion;

    @Before
    public void before() throws Exception {
        HiveSuite.before();
        targetVersion = TestUtils.getOpenSearchClusterInfo().getMajorVersion();
    }

    @After
    public void after() throws Exception {
        HiveSuite.after();
    }

    @Test
    public void testBasicSave() throws Exception {
        // load the raw data as a native, managed table
        // and then insert its content into the external one

        String localTable = createTable("source");
        String load = loadData("source");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE artistssave ("
                        + "id       BIGINT, "
                        + "name     STRING, "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-artists", "data", targetVersion));

        String selectTest = "SELECT s.name, struct(s.url, s.picture) FROM source s";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE artistssave "
                        + "SELECT s.id, s.name, named_struct('url', s.url, 'picture', s.picture) FROM source s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testMappingVersion() throws Exception {
        // load the raw data as a native, managed table
        // and then insert its content into the external one

        String localTable = createTable("sourcewithmetadata");
        String load = loadData("sourcewithmetadata");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE savewithmetadata ("
                        + "id       BIGINT, "
                        + "name     STRING, "
                        + "ts       STRING) "
                        + tableProps(resource("hive-savemeta", "data", targetVersion), "'opensearch.mapping.id' = 'id'", "'opensearch.mapping.version' = '<\"5\">'");

        String selectTest = "SELECT s.name, s.ts FROM sourcewithmetadata s";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE savewithmetadata "
                        + "SELECT s.id, s.name, s.ts FROM sourcewithmetadata s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testBasicSaveMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-artists").getResolvedView().toString(),
                is("*/*=[id=LONG, links=[picture=TEXT, url=TEXT], name=TEXT]"));
    }

    @Test
    // see http://shmsoft.blogspot.ro/2011/10/loading-inner-maps-in-hive.html
    public void testCompoundSave() throws Exception {

        // load the raw data as a native, managed table
        // and then insert its content into the external one
        String localTable = "CREATE TABLE compoundsource ("
                + "rid      INT, "
                + "name     STRING, "
                + "url      STRING, "
                + "picture  STRING, "
                + "mapids   ARRAY<INT>, "
                + "rdata    MAP<INT, STRING>)"
                + "ROW FORMAT DELIMITED "
                + "FIELDS TERMINATED BY '\t' "
                + "COLLECTION ITEMS TERMINATED BY ',' "
                + "MAP KEYS TERMINATED BY ':' "
                + "LINES TERMINATED BY '\n' "
                + "LOCATION '/tmp/hive/warehouse/compound/' ";


        // load the data - use the URI just to be sure
        String load = loadData("compoundSource");


        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE compoundsave ("
                        + "rid      INT, "
                        + "mapids   ARRAY<INT>, "
                        + "rdata    MAP<INT, STRING>) "
                        + tableProps(resource("hive-compound", "data", targetVersion));

        String selectTest = "SELECT rid, mapids, rdata FROM compoundsource";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE compoundsave "
                        + "SELECT rid, mapids, rdata FROM compoundsource";

        System.out.println(ddl);
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testCompoundSaveMapping() throws Exception {
        assertThat(
                RestUtils.getMappings("hive-compound").getResolvedView().toString(),
                is("*/*=[mapids=LONG, rdata=[1=TEXT, 10=TEXT, 11=TEXT, 12=TEXT, 13=TEXT, 2=TEXT, 3=TEXT, 4=TEXT, 5=TEXT, 6=TEXT, 7=TEXT, 8=TEXT, 9=TEXT], rid=LONG]"));
    }


    @Test
    public void testTimestampSave() throws Exception {
        String localTable = createTable("timestampsource");
        String load = loadData("timestampsource");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE artiststimestampsave ("
                        + "id       BIGINT, "
                        + "dte     TIMESTAMP, "
                        + "name     STRING, "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-artiststimestamp", "data", targetVersion));

        String currentDate = "SELECT *, from_unixtime(unix_timestamp()) from timestampsource";

        // since the date format is different in Hive vs ISO8601/Joda, save only the date (which is the same) as a string
        // we do this since unix_timestamp() saves the date as a long (in seconds) and w/o mapping the date is not recognized as data
        String insert =
                "INSERT OVERWRITE TABLE artiststimestampsave "
                        + "SELECT NULL, from_unixtime(unix_timestamp()), s.name, named_struct('url', s.url, 'picture', s.picture) FROM timestampsource s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(currentDate));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testTimestampSaveMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-artiststimestamp").getResolvedView().toString(),
                is("*/*=[dte=DATE, links=[picture=TEXT, url=TEXT], name=TEXT]"));
    }

    @Test
    public void testFieldAlias() throws Exception {
        String localTable = createTable("aliassource");
        String load = loadData("aliassource");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE aliassave ("
                        + "dTE     TIMESTAMP, "
                        + "Name     STRING, "
                        + "links    STRUCT<uRl:STRING, pICture:STRING>) "
                        + tableProps(resource("hive-aliassave", "data", targetVersion), "'opensearch.mapping.names' = 'dTE:@timestamp, uRl:url_123'");

        // since the date format is different in Hive vs ISO8601/Joda, save only the date (which is the same) as a string
        // we do this since unix_timestamp() saves the date as a long (in seconds) and w/o mapping the date is not recognized as data
        String insert =
                "INSERT OVERWRITE TABLE aliassave "
                        + "SELECT from_unixtime(unix_timestamp()), s.name, named_struct('uRl', s.url, 'pICture', s.picture) FROM aliassource s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testFieldAliasMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-aliassave").getResolvedView().toString(),
                is("*/*=[@timestamp=DATE, links=[picture=TEXT, url_123=TEXT], name=TEXT]"));
    }

    @Test
    @Ignore // cast isn't fully supported for date as it throws CCE
    public void testDateSave() throws Exception {
        String localTable = createTable("datesource");
        String load = loadData("datesource");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE datesave ("
                        + "id       BIGINT, "
                        + "date     DATE, "
                        + "name     STRING, "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-datesave", "data", targetVersion));

        // this works
        String someDate = "SELECT cast('2013-10-21' as date) from datesource";


        // this does not
        String insert =
                "INSERT OVERWRITE TABLE datesave "
                        + "SELECT NULL, cast('2013-10-21' as date), s.name, named_struct('url', s.url, 'picture', s.picture) FROM datesource s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(someDate));
        System.out.println(server.execute(insert));
    }

    @Test
    @Ignore
    public void testDateSaveMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-datesave").getResolvedView().toString(),
                is("*/*=[id=LONG, date=LONG, name=TEXT, links=[url=TEXT, picture=TEXT]]"));
    }

    @Test
    public void testChar() throws Exception {
        String localTable = createTable("charsource");
        String load = loadData("charsource");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE charsave ("
                        + "id       BIGINT, "
                        + "name     CHAR(20), "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-charsave", "data", targetVersion));

        // this does not
        String insert =
                "INSERT OVERWRITE TABLE charsave "
                        + "SELECT s.id, s.name, named_struct('url', s.url, 'picture', s.picture) FROM charsource s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testCharMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-charsave").getResolvedView().toString(),
                is("*/*=[id=LONG, links=[picture=TEXT, url=TEXT], name=TEXT]"));
    }

    @Test
    public void testExternalSerDe() throws Exception {
        String localTable = "CREATE TABLE externalserde ("
                + "data       STRING) "
                + "ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.RegexSerDe' "
                + "WITH SERDEPROPERTIES ('input.regex'='(.*)') "
                + "LOCATION '/tmp/hive/warehouse/externalserde/' ";

        String load = loadData("externalserde");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE externalserdetest ("
                        + "data     STRING)"
                        + tableProps(resource("hive-externalserde", "data", targetVersion));

        String insert =
                "INSERT OVERWRITE TABLE externalserdetest "
                        + "SELECT s.data FROM externalserde s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testExternalSerDeMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-externalserde").getResolvedView().toString(),
                is("*/*=[data=TEXT]"));
    }

    @Test
    public void testVarcharSave() throws Exception {
        String localTable = createTable("varcharsource");
        String load = loadData("varcharsource");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE varcharsave ("
                        + "id       BIGINT, "
                        + "name     VARCHAR(10), "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-varcharsave", "data", targetVersion));

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE varcharsave "
                        + "SELECT s.id, s.name, named_struct('url', s.url, 'picture', s.picture) FROM varcharsource s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testVarcharSaveMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-varcharsave").getResolvedView().toString(),
                is("*/*=[id=LONG, links=[picture=TEXT, url=TEXT], name=TEXT]"));
    }

    @Test
    public void testCreate() throws Exception {
        // load the raw data as a native, managed table
        // and then insert its content into the external one

        String localTable = createTable("createsource");
        String load = loadData("createsource");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE createsave ("
                        + "id       BIGINT, "
                        + "name     STRING, "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-createsave", "data", targetVersion),
                                "'" + ConfigurationOptions.OPENSEARCH_MAPPING_ID + "'='id'",
                                "'" + ConfigurationOptions.OPENSEARCH_WRITE_OPERATION + "'='create'");

        String selectTest = "SELECT s.name, struct(s.url, s.picture) FROM createsource s";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE createsave "
                        + "SELECT s.id, s.name, named_struct('url', s.url, 'picture', s.picture) FROM createsource s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testCreateMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-createsave").getResolvedView().toString(),
                is("*/*=[id=LONG, links=[picture=TEXT, url=TEXT], name=TEXT]"));
    }

    @Test(expected = SQLException.class)
    public void testCreateWithDuplicates() throws Exception {
        // load the raw data as a native, managed table
        // and then insert its content into the external one

        String localTable = createTable("createsourceduplicate");
        String load = loadData("createsourceduplicate");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE createsaveduplicate ("
                        + "id       BIGINT, "
                        + "name     STRING, "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-createsave", "data", targetVersion),
                                "'" + ConfigurationOptions.OPENSEARCH_MAPPING_ID + "'='id'",
                                "'" + ConfigurationOptions.OPENSEARCH_WRITE_OPERATION + "'='create'");

        String selectTest = "SELECT s.name, struct(s.url, s.picture) FROM createsourceduplicate s";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE createsaveduplicate "
                        + "SELECT s.id, s.name, named_struct('url', s.url, 'picture', s.picture) FROM createsourceduplicate s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testUpdateWithId() throws Exception {
        // load the raw data as a native, managed table
        // and then insert its content into the external one

        String localTable = createTable("updatesource");
        String load = loadData("updatesource");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE updatesave ("
                        + "id       BIGINT, "
                        + "name     STRING, "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-updatesave", "data", targetVersion),
                                "'" + ConfigurationOptions.OPENSEARCH_MAPPING_ID + "'='id'",
                                "'" + ConfigurationOptions.OPENSEARCH_WRITE_OPERATION + "'='upsert'");

        String selectTest = "SELECT s.name, struct(s.url, s.picture) FROM updatesource s";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE updatesave "
                        + "SELECT s.id, s.name, named_struct('url', s.url, 'picture', s.picture) FROM updatesource s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testUpdateWithIdMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-updatesave").getResolvedView().toString(),
                is("*/*=[id=LONG, links=[picture=TEXT, url=TEXT], name=TEXT]"));
    }

    @Test(expected = SQLException.class)
    public void testUpdateWithoutUpsert() throws Exception {
        // load the raw data as a native, managed table
        // and then insert its content into the external one

        String localTable = createTable("updatewoupsertsource");
        String load = loadData("updatewoupsertsource");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE updatewoupsertsave ("
                        + "id       BIGINT, "
                        + "name     STRING, "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-updatewoupsertsave", "data", targetVersion),
                                "'" + ConfigurationOptions.OPENSEARCH_MAPPING_ID + "'='id'",
                                "'" + ConfigurationOptions.OPENSEARCH_WRITE_OPERATION + "'='update'");

        String selectTest = "SELECT s.name, struct(s.url, s.picture) FROM updatewoupsertsource s";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE updatewoupsertsave "
                        + "SELECT s.id, s.name, named_struct('url', s.url, 'picture', s.picture) FROM updatewoupsertsource s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testIndexPattern() throws Exception {
        // load the raw data as a native, managed table
        // and then insert its content into the external one

        String localTable = createTable("sourcepattern");
        String load = loadData("sourcepattern");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE pattern ("
                        + "id       BIGINT, "
                        + "name     STRING, "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-pattern-{id}", "data", targetVersion));

        String selectTest = "SELECT s.name, struct(s.url, s.picture) FROM sourcepattern s";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE pattern "
                        + "SELECT s.id, s.name, named_struct('url', s.url, 'picture', s.picture) FROM sourcepattern s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testIndexPatternMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-pattern-12").getResolvedView().toString(),
                is("*/*=[id=LONG, links=[picture=TEXT, url=TEXT], name=TEXT]"));
    }

    @Test
    public void testIndexPatternFormat() throws Exception {
        // load the raw data as a native, managed table
        // and then insert its content into the external one

        String localTable = createTable("sourcepatternformat");
        String load = loadData("sourcepatternformat");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE patternformat ("
                        + "id       BIGINT, "
                        + "name     STRING, "
                        + "ts       STRING, "
                        + "links    STRUCT<url:STRING, picture:STRING>) "
                        + tableProps(resource("hive-pattern-format-{ts|YYYY-MM-dd}", "data", targetVersion));

        String selectTest = "SELECT s.name, s.ts, struct(s.url, s.picture) FROM sourcepatternformat s";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE patternformat "
                        + "SELECT s.id, s.name, s.ts, named_struct('url', s.url, 'picture', s.picture) FROM sourcepatternformat s";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(insert));
    }

    @Test
    public void testIndexPatternFormatMapping() throws Exception {
        assertThat(RestUtils.getMappings("hive-pattern-format-2012-10-06").getResolvedView().toString(),
                is("*/*=[id=LONG, links=[picture=TEXT, url=TEXT], name=TEXT, ts=DATE]"));
    }

    @Test
    public void testMappingExclude() throws Exception {
        String localTable = createTable("sourcefieldexclude");
        String load = loadData("sourcefieldexclude");

        // create external table
        String ddl =
                "CREATE EXTERNAL TABLE fieldexclude ("
                        + "id       BIGINT, "
                        + "name     STRING)"
                        + tableProps(resource("hive-fieldexclude", "data", targetVersion), "'opensearch.mapping.id'='id'", "'opensearch.mapping.exclude'='id'");

        String selectTest = "SELECT s.id, s.name FROM sourcefieldexclude s";

        // transfer data
        String insert =
                "INSERT OVERWRITE TABLE fieldexclude "
                        + "SELECT id, name FROM sourcefieldexclude ";

        System.out.println(ddl);
        System.out.println(server.execute(ddl));
        System.out.println(server.execute(localTable));
        System.out.println(server.execute(load));
        System.out.println(server.execute(selectTest));
        System.out.println(server.execute(insert));

        String docEndpoint = docEndpoint("hive-fieldexclude", "data", targetVersion);
        String string = RestUtils.get(docEndpoint + "/1");
        assertThat(string, containsString("MALICE"));

        string = RestUtils.get(docEndpoint + "/7");
        assertThat(string, containsString("Manson"));

        assertFalse(RestUtils.getMappings("hive-fieldexclude").getResolvedView().toString().contains("id="));
    }

    private String createTable(String tableName) {
        return String.format("CREATE TABLE %s ("
                + "id       BIGINT, "
                + "name     STRING, "
                + "url      STRING, "
                + "picture  STRING, "
                + "ignore1  STRING, "
                + "ignore2  STRING, "
                + "ts       STRING) "
                + "ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' LINES TERMINATED BY '\n'"
                + "LOCATION '/tmp/hive/warehouse/%s/' "
                , tableName, tableName);
    }

    private String loadData(String tableName) {
        return "LOAD DATA " + (isLocal ? "LOCAL" : "") + " INPATH '" + HiveSuite.hdfsResource + "' OVERWRITE INTO TABLE " + tableName;
    }

    private static String tableProps(String resource, String... params) {
        return HiveSuite.tableProps(resource, null, params);
    }
}