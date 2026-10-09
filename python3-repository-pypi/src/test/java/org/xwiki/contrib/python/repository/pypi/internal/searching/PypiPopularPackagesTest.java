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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;
import org.xwiki.test.junit5.XWikiTempDir;
import org.xwiki.test.junit5.XWikiTempDirExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validate {@link PypiPopularPackages} and {@link PypiPopularPackagesUpdateTask}.
 *
 * @version $Id$
 */
@ExtendWith(XWikiTempDirExtension.class)
class PypiPopularPackagesTest
{
    private static final String JSON = "{\"last_update\":\"2026-10-01 12:40:51\",\"source\":\"bigquery\","
        + "\"meta\":{\"x\":[1,2]},\"rows\":[{\"download_count\":10,\"project\":\"Typing_Extensions\"},"
        + "{\"project\":\"requests\",\"download_count\":5},{\"download_count\":1}],\"total_rows\":3}";

    @XWikiTempDir
    private File directory;

    private final Logger logger = mock(Logger.class);

    private File file;

    private PypiPopularPackages popularPackages;

    @BeforeEach
    void beforeEach()
    {
        this.file = new File(this.directory, "popular/top.txt");
        this.popularPackages = new PypiPopularPackages(this.file, this.logger);
    }

    @Test
    void initializeWithEmbeddedList()
    {
        this.popularPackages.initialize();

        List<String> names = this.popularPackages.getNames();
        assertEquals(15000, names.size());
        assertEquals("boto3", names.get(0));
        // The names are normalized
        assertEquals("typing-extensions", names.get(2));
    }

    @Test
    void update() throws IOException
    {
        this.popularPackages.initialize();
        this.popularPackages.update(IOUtils.toInputStream(JSON, UTF_8));

        assertEquals(Arrays.asList("typing-extensions", "requests"), this.popularPackages.getNames());
        assertEquals(Map.of("typing-extensions", 0, "requests", 1), this.popularPackages.getRanks());

        // The list is kept for the next restart
        PypiPopularPackages restarted = new PypiPopularPackages(this.file, this.logger);
        restarted.initialize();
        assertEquals(Arrays.asList("typing-extensions", "requests"), restarted.getNames());
    }

    @Test
    void updateWithInvalidList() throws IOException
    {
        this.popularPackages.initialize();
        this.popularPackages.update(IOUtils.toInputStream(JSON, UTF_8));

        assertThrows(IOException.class, () -> this.popularPackages.update(IOUtils.toInputStream("[]", UTF_8)));
        assertThrows(IOException.class,
            () -> this.popularPackages.update(IOUtils.toInputStream("{\"rows\":[]}", UTF_8)));
        assertThrows(IOException.class, () -> this.popularPackages.update(IOUtils.toInputStream("{\"rows\":", UTF_8)));

        // The current list is kept
        assertEquals(Arrays.asList("typing-extensions", "requests"), this.popularPackages.getNames());
    }

    @Test
    void initializeWithInvalidStoredList() throws IOException
    {
        this.file.mkdirs();

        this.popularPackages.initialize();

        // The stored list can't be read (it's a directory), so the embedded one is used
        assertEquals("boto3", this.popularPackages.getNames().get(0));
        verify(this.logger).warn(any(String.class), eq(this.file), any(IOException.class));
    }

    @Test
    void updateTask() throws Exception
    {
        this.popularPackages.initialize();
        PypiHttpClient httpClient = mock(PypiHttpClient.class);
        when(httpClient.openStream(new URI("https://example.com/top.json"), null))
            .thenReturn(IOUtils.toInputStream(JSON, UTF_8));

        new PypiPopularPackagesUpdateTask(this.popularPackages, "https://example.com/top.json", httpClient,
            this.logger).run();

        assertEquals(Arrays.asList("typing-extensions", "requests"), this.popularPackages.getNames());
        assertEquals("typing-extensions\nrequests\n", Files.readString(this.file.toPath()));
    }

    @Test
    void updateTaskWhenListNotAvailable() throws Exception
    {
        this.popularPackages.initialize();
        PypiHttpClient httpClient = mock(PypiHttpClient.class);
        when(httpClient.openStream(any(), isNull())).thenReturn(null);

        new PypiPopularPackagesUpdateTask(this.popularPackages, "https://example.com/top.json", httpClient,
            this.logger).run();

        // The current list is kept
        assertEquals("boto3", this.popularPackages.getNames().get(0));
        verify(this.logger).warn(any(String.class), eq("https://example.com/top.json"), eq("The list does not exist"));
    }
}
