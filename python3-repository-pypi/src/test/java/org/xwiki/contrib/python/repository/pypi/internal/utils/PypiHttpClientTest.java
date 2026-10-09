/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.python.repository.pypi.internal.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.extension.ExtensionManagerConfiguration;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Validate {@link PypiHttpClient} with a local HTTP server.
 *
 * @version $Id$
 */
@ComponentTest
class PypiHttpClientTest
{
    @InjectMockComponents
    private PypiHttpClient httpClient;

    @MockComponent
    private ExtensionManagerConfiguration configuration;

    private HttpServer server;

    private final Map<String, String> requestHeaders = new HashMap<>();

    @BeforeEach
    void beforeEach() throws IOException
    {
        when(this.configuration.getUserAgent()).thenReturn("XWikiTest");

        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.createContext("/ok", exchange -> {
            this.requestHeaders.put("Accept", exchange.getRequestHeaders().getFirst("Accept"));
            this.requestHeaders.put("User-Agent", exchange.getRequestHeaders().getFirst("User-Agent"));
            send(exchange, 200, "content");
        });
        this.server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok");
            send(exchange, 301, "");
        });
        this.server.createContext("/gzip", exchange -> {
            this.requestHeaders.put("Accept-Encoding", exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write("compressed content".getBytes(StandardCharsets.UTF_8));
            }
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, compressed.size());
            try (OutputStream stream = exchange.getResponseBody()) {
                compressed.writeTo(stream);
            }
        });
        this.server.createContext("/error", exchange -> send(exchange, 500, "error"));
        this.server.start();
    }

    @AfterEach
    void afterEach()
    {
        this.server.stop(0);
        this.httpClient.dispose();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length > 0 ? bytes.length : -1);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    private URI uri(String path)
    {
        return URI.create("http://localhost:" + this.server.getAddress().getPort() + path);
    }

    @Test
    void openStream() throws IOException
    {
        try (InputStream stream = this.httpClient.openStream(uri("/ok"), "application/json")) {
            assertEquals("content", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }

        assertEquals("application/json", this.requestHeaders.get("Accept"));
        assertEquals("XWikiTest", this.requestHeaders.get("User-Agent"));
    }

    @Test
    void openStreamWithCompressedContent() throws IOException
    {
        try (InputStream stream = this.httpClient.openStream(uri("/gzip"), null)) {
            assertEquals("compressed content", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }

        assertEquals("gzip", this.requestHeaders.get("Accept-Encoding"));
    }

    @Test
    void openStreamWithRedirect() throws IOException
    {
        try (InputStream stream = this.httpClient.openStream(uri("/redirect"), null)) {
            assertEquals("content", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }

        assertNull(this.requestHeaders.get("Accept"));
    }

    @Test
    void openStreamWhenNotFound() throws IOException
    {
        assertNull(this.httpClient.openStream(uri("/missing"), null));
    }

    @Test
    void openStreamWhenError()
    {
        assertThrows(IOException.class, () -> this.httpClient.openStream(uri("/error"), null));
    }
}
