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
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Validate {@link PypiPackageIndex}.
 *
 * @version $Id$
 */
public class PypiPackageIndexTest
{
    private File directory;

    private Logger logger = mock(Logger.class);

    private PypiPackageIndex index;

    @Before
    public void before()
    {
        this.directory = new File("target/test-" + System.nanoTime()).getAbsoluteFile();
        this.index = new PypiPackageIndex(this.directory, this.logger);
    }

    @Test
    public void initializeWithoutDirectory()
    {
        this.index.initialize();

        assertNull(this.index.getFile());
        assertNull(this.index.getSearcher());
    }

    @Test
    public void initializeKeepsMostRecentIndex() throws Exception
    {
        this.directory.mkdirs();
        File older = new File(this.directory, "older.txt");
        Files.write(older.toPath(), Arrays.asList("older"));
        older.setLastModified(1000);
        File newer = new File(this.directory, "newer.txt");
        Files.write(newer.toPath(), Arrays.asList("newer"));
        newer.setLastModified(2000);
        // A Lucene index stored by older versions of the extension
        File luceneIndex = new File(this.directory, "3f5d8a5c-1b2e-4c1f-9a77-0d5c6f0f4b11");
        luceneIndex.mkdirs();
        Files.write(new File(luceneIndex, "segments_1").toPath(), new byte[] { 1 });

        this.index.initialize();

        assertEquals(newer, this.index.getFile());
        assertEquals(newer, this.index.getSearcher().getIndexFile());
        assertFalse(older.exists());
        assertFalse(luceneIndex.exists());
    }

    @Test
    public void update() throws Exception
    {
        assertTrue(this.index.update(consumer -> {
            consumer.accept("first");
            consumer.accept("second");
        }));
        File first = this.index.getFile();
        assertEquals(Arrays.asList("first", "second"), Files.readAllLines(first.toPath()));

        assertTrue(this.index.update(consumer -> consumer.accept("third")));

        assertFalse(first.exists());
        assertEquals(Arrays.asList("third"), Files.readAllLines(this.index.getFile().toPath()));
    }

    @Test
    public void updateFailure() throws Exception
    {
        this.index.update(consumer -> consumer.accept("current"));
        File current = this.index.getFile();

        IOException exception = new IOException("failure");
        assertFalse(this.index.update(consumer -> {
            consumer.accept("partial");
            throw exception;
        }));

        assertEquals(current, this.index.getFile());
        assertEquals(Arrays.asList("current"), Files.readAllLines(current.toPath()));
        // The partial index is deleted
        assertEquals(1, this.directory.list().length);
        verify(this.logger).error(eq("Failed to create the PyPI package index"), any(IOException.class));
    }
}
