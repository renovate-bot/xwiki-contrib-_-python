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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.extension.repository.result.CollectionIterableResult;
import org.xwiki.extension.repository.result.IterableResult;

/**
 * Search the package names of a {@link PypiPackageIndex} file.
 *
 * @version $Id$
 */
public class PypiPackageSearcher
{
    private final File indexFile;

    /**
     * @param indexFile the index file, containing one package name per line
     */
    public PypiPackageSearcher(File indexFile)
    {
        this.indexFile = indexFile;
    }

    /**
     * @return the index file
     */
    public File getIndexFile()
    {
        return this.indexFile;
    }

    /**
     * Find the packages whose name contains the query, ignoring the case. The package with exactly the searched name
     * comes first, the others follow in the order of the index.
     *
     * @param searchQuery the text to search in the package names, all packages are matched when empty
     * @param offset the index of the first package to return
     * @param hitsPerPage the maximum number of packages to return, or a negative value to return all of them
     * @return the names of the packages found
     * @throws IOException when failing to read the index
     */
    public IterableResult<String> search(String searchQuery, int offset, int hitsPerPage) throws IOException
    {
        String query = StringUtils.defaultString(searchQuery).trim().toLowerCase(Locale.ROOT);
        int from = Math.max(offset, 0);
        // Only the packages up to the end of the requested page need to be remembered
        long max = hitsPerPage < 0 ? Integer.MAX_VALUE : Math.min((long) from + hitsPerPage, Integer.MAX_VALUE);

        int totalHits = 0;
        String exactMatch = null;
        List<String> matches = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(this.indexFile.toPath(), StandardCharsets.UTF_8)) {
            for (String packageName = reader.readLine(); packageName != null; packageName = reader.readLine()) {
                String lowerPackageName = packageName.toLowerCase(Locale.ROOT);
                if (lowerPackageName.contains(query)) {
                    ++totalHits;
                    if (exactMatch == null && lowerPackageName.equals(query)) {
                        exactMatch = packageName;
                    } else if (matches.size() < max) {
                        matches.add(packageName);
                    }
                }
            }
        }

        if (exactMatch != null) {
            matches.add(0, exactMatch);
        }

        List<String> result;
        if (from >= matches.size() || max <= from) {
            result = Collections.emptyList();
        } else {
            result = matches.subList(from, (int) Math.min(matches.size(), max));
        }

        return new CollectionIterableResult<>(totalHits, offset, result);
    }
}
