/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.samples.ingest;

import com.google.protobuf.ByteString;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.hbase.HBaseIO;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.DefaultValueFactory;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.KV;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IngestToHBase {

    private static final Logger LOG = LoggerFactory.getLogger(IngestToHBase.class);
    /**
     * Specific pipeline options.
     */
    private interface Options extends PipelineOptions {
        String GDELT_EVENTS_URL = "http://data.gdeltproject.org/events/";

        @Description("GDELT file date")
        @Default.InstanceFactory(Options.GDELTFileFactory.class)
        String getDate();
        void setDate(String value);

        @Description("Input Path")
        String getInput();
        void setInput(String value);

        @Description("Output Path")
        String getOutput();
        void setOutput(String value);

        class GDELTFileFactory implements DefaultValueFactory<String> {
            public String create(PipelineOptions options) {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
                return format.format(new Date());
            }
        }
    }

    public static void main(String[] args) {
        Options options = PipelineOptionsFactory.fromArgs(args).withValidation().as(Options.class);
        if (options.getInput() == null) {
            options.setInput(Options.GDELT_EVENTS_URL + options.getDate() + ".export.CSV.zip");
        }
        LOG.info(options.toString());

        final String table = "";
        final Configuration conf = HBaseConfiguration.create();

        Pipeline pipeline = Pipeline.create(options);
        pipeline
            .apply("ReadFromGDELTFile", TextIO.read().from(options.getInput()))
            .apply("ConvertToKV", MapElements.via(new SimpleFunction<String, KV<String, String>>() {
                @Override
                public KV<String, String> apply(String input) {
                  String key = "";
                  //TODO
                  return KV.of(key, input);
                }
            }))
            .apply("ToHBaseMutation", MapElements.via(new SimpleFunction<KV<String, String>, KV<byte[], Iterable<Mutation>>>() {
              @Override
              public KV<byte[], Iterable<Mutation>> apply(KV<String, String> input) {
                return makeWrite(input.getKey(), input.getValue());
              }
            }))
            .apply("WriteToHBase", HBaseIO.write().withConfiguration(conf).withTableId(table));
;
        pipeline.run();
    }

    private static KV<byte[], Iterable<Mutation>> makeWrite(String key, String value) {
        byte[] rowKey = key.getBytes(StandardCharsets.UTF_8);
        List<Mutation> mutations = new ArrayList<>();
        mutations.add(makeMutation(key, value));
        return KV.of(rowKey, (Iterable<Mutation>) mutations);
    }

    private static Mutation makeMutation(String key, String value) {
        byte[] rowKey = key.getBytes(StandardCharsets.UTF_8);
        return new Put(rowKey)
            .addColumn(COLUMN_FAMILY, COLUMN_NAME, Bytes.toBytes(value))
            .addColumn(COLUMN_FAMILY, COLUMN_EMAIL, Bytes.toBytes(value + "@email.com"));
    }

    private static final byte[] COLUMN_FAMILY = Bytes.toBytes("info");
    private static final byte[] COLUMN_NAME = Bytes.toBytes("name");
    private static final byte[] COLUMN_EMAIL = Bytes.toBytes("email");
}
