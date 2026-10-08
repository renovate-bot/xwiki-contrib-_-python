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
package org.xwiki.contrib.python.repository.pypi.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;
import org.xwiki.extension.ExtensionFile;

/**
 * @version $Id$
 */
public class PypiExtensionFile implements ExtensionFile
{
    private final URI uriToDownload;

    private final long sizeOfFile;

    private final PypiHttpClient httpClient;

    /**
     * @param uriToDownload the URI of the file
     * @param sizeOfFile the size of the file, -1 if unknown
     * @param httpClient the client used to download the file
     */
    public PypiExtensionFile(URI uriToDownload, long sizeOfFile, PypiHttpClient httpClient)
    {
        this.uriToDownload = uriToDownload;
        this.sizeOfFile = sizeOfFile;
        this.httpClient = httpClient;
    }

    @Override
    public long getLength()
    {
        return this.sizeOfFile;
    }

    @Override
    public InputStream openStream() throws IOException
    {
        InputStream stream = this.httpClient.openStream(this.uriToDownload, null);

        if (stream == null) {
            throw new IOException("The package file [" + this.uriToDownload + "] does not exist");
        }

        return stream;
    }
}
