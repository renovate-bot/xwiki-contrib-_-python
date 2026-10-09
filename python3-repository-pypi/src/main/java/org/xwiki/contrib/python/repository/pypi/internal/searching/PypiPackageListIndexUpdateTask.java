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
package org.xwiki.contrib.python.repository.pypi.internal.searching;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.TimerTask;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.xwiki.contrib.python.repository.pypi.internal.PypiParameters;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Rebuild the index of the PyPI packages from the list of projects provided by the Simple API.
 *
 * @version $Id$
 */
public class PypiPackageListIndexUpdateTask extends TimerTask
{
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final PypiPackageIndex index;

    private final PypiHttpClient httpClient;

    private final Logger logger;

    /**
     * @param index the index to update
     * @param httpClient the client used to download the Simple API index
     * @param logger the logger used to report the progress and the errors
     */
    public PypiPackageListIndexUpdateTask(PypiPackageIndex index, PypiHttpClient httpClient, Logger logger)
    {
        this.index = index;
        this.httpClient = httpClient;
        this.logger = logger;
    }

    @Override
    public void run()
    {
        logger.info("Start of update PyPI package index task");

        boolean updated = false;
        try (InputStream simpleIndexInputStream = getSimpleApiIndexInputStream()) {
            // Keep the current index when the list of packages can't be downloaded
            if (simpleIndexInputStream != null) {
                updated = this.index.update(consumer -> parsePackageNames(simpleIndexInputStream, consumer));
            }
        } catch (IOException e) {
            logger.error("IO problem whilst updating python package index", e);
        }

        if (updated) {
            logger.info("End of update PyPI package index task. PyPI packages index updated");
        } else {
            logger.info("End of update PyPI package index task. PyPI packages index not updated");
        }
    }

    /**
     * Extract the package names from the JSON form (PEP 691) of the Simple API index. The index lists all the projects
     * of PyPI (several tens of MB) so it's streamed instead of being fully loaded in memory.
     *
     * @param is the JSON form of the Simple API index
     * @param consumer called with the name of each package, in the order of the index
     * @throws IOException when failing to parse the index
     */
    protected void parsePackageNames(InputStream is, Consumer<String> consumer) throws IOException
    {
        try (JsonParser parser = JSON_FACTORY.createParser(is)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("The Simple API index is not a JSON object");
            }

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                if ("projects".equals(fieldName) && parser.currentToken() == JsonToken.START_ARRAY) {
                    while (parser.nextToken() == JsonToken.START_OBJECT) {
                        parseProject(parser, consumer);
                    }
                } else {
                    parser.skipChildren();
                }
            }
        }
    }

    private void parseProject(JsonParser parser, Consumer<String> consumer) throws IOException
    {
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String fieldName = parser.getCurrentName();
            parser.nextToken();
            if ("name".equals(fieldName)) {
                consumer.accept(parser.getText());
            } else {
                parser.skipChildren();
            }
        }
    }

    /**
     * @return the JSON form of the Simple API index, {@code null} if it can't be downloaded
     */
    public InputStream getSimpleApiIndexInputStream()
    {
        try {
            return this.httpClient.openStream(new URI(PypiParameters.PACKAGE_LIST_SIMPLE_API),
                PypiParameters.SIMPLE_API_JSON_MEDIA_TYPE);
        } catch (IOException | URISyntaxException e) {
            logger.error("Failed to get list of Python packages from PyPI", e);
        }

        return null;
    }
}
