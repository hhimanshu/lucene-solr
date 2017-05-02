/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queries.payloads.PayloadFunction;
import org.apache.lucene.queries.payloads.PayloadScoreQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.FieldType;
import org.apache.solr.util.PayloadUtils;

/**
 * Creates a PayloadScoreQuery wrapping a SpanQuery created from the input value, applying text analysis and
 * constructing SpanTermQuery or SpanNearQuery based on number of terms.
 *
 * <br>Other parameters:
 * <br><code>f</code>, the field (required)
 * <br><code>func</code>, payload function (min, max, or average; required)
 * <br><code>includeSpanScore</code>, multiple payload function result by similarity score or not (default: false)
 * <br>Example: <code>{!payload_score f=weighted_terms_dpf}Foo Bar</code> creates a SpanNearQuery with "Foo" followed by "Bar"
 */
public class PayloadScoreQParserPlugin extends QParserPlugin {
  public static final String NAME = "payload_score";

  @Override
  public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
    return new QParser(qstr, localParams, params, req) {
      @Override
      public Query parse() throws SyntaxError {
        String field = localParams.get(QueryParsing.F);
        String value = localParams.get(QueryParsing.V);
        String func = localParams.get("func");
        boolean includeSpanScore = localParams.getBool("includeSpanScore", false);

        if (field == null) {
          throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "'f' not specified");
        }

        if (value == null) {
          throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "query string missing");
        }

        FieldType ft = req.getCore().getLatestSchema().getFieldType(field);
        Analyzer analyzer = ft.getQueryAnalyzer();
        SpanQuery query = PayloadUtils.createSpanQuery(field, value, analyzer);

        if (query == null) {
          throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "SpanQuery is null");
        }

        // note: this query(/parser) does not support func=first; 'first' is a payload() value source feature only
        PayloadFunction payloadFunction = PayloadUtils.getPayloadFunction(func);
        if (payloadFunction == null) throw new SyntaxError("Unknown payload function: " + func);

        return new PayloadScoreQuery(query, payloadFunction, includeSpanScore);
      }
    };
  }
}
