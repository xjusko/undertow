/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server.handlers;

import io.undertow.protocols.http2.Http2Channel;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@AjpIgnore
public class LongURLTestCase {

    private static final String MESSAGE = "HelloUrl";
    private static final int COUNT = 10000;

    // TODO private static OptionMap EXISTING_OPTIONS = DefaultServer.getUndertowOptions();

    @BeforeClass
    public static void setup() {
        // skip this test if we are running in a scenario with default max header size property
        Assume.assumeNotNull(System.getProperty(Http2Channel.HTTP2_MAX_HEADER_SIZE_PROPERTY));
        // TODO
        // DefaultServer.setUndertowOptions(OptionMap.builder().set(UndertowOptions.HTTP2_MAX_HEADER_SIZE, 100000).getMap());

        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                exchange.getResponseSender().send(exchange.getRelativePath());
            }
        });
    }

    // TODO
    /*@AfterClass
    public static void teardown() {
        DefaultServer.setUndertowOptions(EXISTING_OPTIONS);
    }*/

    @Test
    public void testLargeURL() throws IOException {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COUNT; ++i) {
            sb.append(MESSAGE);
        }
        String message = sb.toString();

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/" + message);
            HttpResponse result = client.execute(get);
            Assert.assertEquals("/" + message, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
