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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.extension.ExtensionManagerConfiguration;

/**
 * Perform the HTTP requests to PyPI with a single HTTP client, so that the connections are reused.
 *
 * @version $Id$
 */
@Component(roles = PypiHttpClient.class)
@Singleton
public class PypiHttpClient implements Initializable, Disposable
{
    private static final int HTTP_OK = 200;

    private static final int HTTP_NOT_FOUND = 404;

    private static final String GZIP = "gzip";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(60);

    /**
     * The maximum time to wait for the response headers (the body itself can take longer to download).
     */
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(5);

    @Inject
    private ExtensionManagerConfiguration configuration;

    private HttpClient httpClient;

    @Override
    public void initialize()
    {
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT).proxy(ProxySelector.getDefault()).build();
    }

    /**
     * @param uri the URI to request
     * @param accept the media type to ask for in the {@code Accept} header, or {@code null} to not send any
     * @return the body of the response (which must be closed), or {@code null} if the resource does not exist
     * @throws IOException when failing to request the URI
     */
    public InputStream openStream(URI uri, String accept) throws IOException
    {
        // The JSON documents provided by PyPI (like the Simple API index, which lists all the packages) are a lot
        // smaller once compressed
        HttpRequest.Builder request =
            HttpRequest.newBuilder(uri).timeout(RESPONSE_TIMEOUT).header("Accept-Encoding", GZIP).GET();
        String userAgent = this.configuration.getUserAgent();
        if (userAgent != null) {
            request.header("User-Agent", userAgent);
        }
        if (accept != null) {
            request.header("Accept", accept);
        }

        HttpResponse<InputStream> response;
        try {
            response = this.httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new InterruptedIOException("Interrupted while requesting [" + uri + "]");
        }

        if (response.statusCode() == HTTP_OK) {
            return response.headers().firstValue("Content-Encoding").filter(GZIP::equalsIgnoreCase).isPresent()
                ? new GZIPInputStream(response.body()) : response.body();
        }

        // Release the connection
        response.body().close();

        if (response.statusCode() == HTTP_NOT_FOUND) {
            return null;
        }

        throw new IOException(
            String.format("Invalid answer [%s] from the server when requesting [%s]", response.statusCode(), uri));
    }

    @Override
    public void dispose()
    {
        this.httpClient.close();
    }
}
