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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.xwiki.contrib.python.PythonPackages;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * The names of the most downloaded PyPI packages, from the most to the least downloaded.
 * <p>
 * The list comes from https://hugovk.dev/top-pypi-packages/ (updated monthly from the PyPI download statistics). The
 * last downloaded list is stored in the permanent directory, and a copy is embedded to be used before the first
 * download.
 *
 * @version $Id$
 */
public class PypiPopularPackages
{
    private static final String EMBEDDED_LIST = "/pypiIndex/top-pypi-packages.txt";

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final File file;

    private final Logger logger;

    private volatile List<String> names = Collections.emptyList();

    private volatile Map<String, Integer> ranks = Collections.emptyMap();

    /**
     * @param file the file where the last downloaded list is stored
     * @param logger the logger used to report the errors
     */
    public PypiPopularPackages(File file, Logger logger)
    {
        this.file = file;
        this.logger = logger;
    }

    /**
     * Load the last downloaded list, or the embedded one.
     */
    public void initialize()
    {
        if (this.file.exists()) {
            try (InputStream stream = Files.newInputStream(this.file.toPath())) {
                setNames(readNames(stream));

                return;
            } catch (IOException e) {
                this.logger.warn("Failed to read the most downloaded PyPI packages from [{}], using the embedded list",
                    this.file, e);
            }
        }

        try (InputStream stream = getClass().getResourceAsStream(EMBEDDED_LIST)) {
            setNames(readNames(stream));
        } catch (IOException e) {
            this.logger.error("Failed to read the embedded list of the most downloaded PyPI packages", e);
        }
    }

    private static List<String> readNames(InputStream stream) throws IOException
    {
        List<String> result = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (!line.isBlank()) {
                result.add(line.trim());
            }
        }

        return Collections.unmodifiableList(result);
    }

    private void setNames(List<String> newNames)
    {
        Map<String, Integer> newRanks = new HashMap<>(newNames.size() * 2);
        for (int i = newNames.size() - 1; i >= 0; --i) {
            newRanks.put(newNames.get(i), i);
        }

        this.names = newNames;
        this.ranks = Collections.unmodifiableMap(newRanks);
    }

    /**
     * @return the (normalized) names of the most downloaded packages, from the most to the least downloaded
     */
    public List<String> getNames()
    {
        return this.names;
    }

    /**
     * @return the position of each of the most downloaded packages (indexed by normalized name), starting with 0 for
     *         the most downloaded one
     */
    public Map<String, Integer> getRanks()
    {
        return this.ranks;
    }

    /**
     * Replace the list with the one provided in the format of https://hugovk.dev/top-pypi-packages/.
     *
     * @param json the JSON list of the most downloaded packages
     * @throws IOException when failing to parse or store the list (the current list is kept)
     */
    public void update(InputStream json) throws IOException
    {
        List<String> newNames = parseNames(json);
        if (newNames.isEmpty()) {
            throw new IOException("The list of the most downloaded PyPI packages is empty");
        }

        // Store the list next to the current one before replacing it, to never end up with a partial file
        this.file.getParentFile().mkdirs();
        File temporaryFile = new File(this.file.getParentFile(), this.file.getName() + ".tmp");
        Files.write(temporaryFile.toPath(), newNames, StandardCharsets.UTF_8);
        Files.move(temporaryFile.toPath(), this.file.toPath(), StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);

        setNames(Collections.unmodifiableList(newNames));
    }

    /**
     * Extract the project names from {@code {"rows": [{"project": "boto3", ...}, ...], ...}}.
     */
    private static List<String> parseNames(InputStream json) throws IOException
    {
        List<String> result = new ArrayList<>();
        try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("The list of the most downloaded PyPI packages is not a JSON object");
            }

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.currentName();
                parser.nextToken();
                if ("rows".equals(fieldName) && parser.currentToken() == JsonToken.START_ARRAY) {
                    while (parser.nextToken() == JsonToken.START_OBJECT) {
                        String project = parseProject(parser);
                        if (project != null) {
                            result.add(PythonPackages.normalizeName(project));
                        }
                    }
                } else {
                    parser.skipChildren();
                }
            }
        }

        return result;
    }

    private static String parseProject(JsonParser parser) throws IOException
    {
        String project = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if ("project".equals(fieldName)) {
                project = parser.getText();
            } else {
                parser.skipChildren();
            }
        }

        return project;
    }
}
