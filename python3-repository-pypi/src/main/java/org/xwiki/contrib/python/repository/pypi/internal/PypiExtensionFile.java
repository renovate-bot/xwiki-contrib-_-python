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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;
import org.xwiki.extension.ExtensionFile;

/**
 * A distribution file published on PyPI, whose content is checked against the hash provided by PyPI when it's
 * downloaded.
 *
 * @version $Id$
 */
public class PypiExtensionFile implements ExtensionFile
{
    private final URI uriToDownload;

    private final long sizeOfFile;

    private final String sha256;

    private final PypiHttpClient httpClient;

    /**
     * Check the hash once the whole file is read, so that a corrupted or tampered file fails the installation.
     */
    private final class HashCheckingInputStream extends DigestInputStream
    {
        private boolean checked;

        HashCheckingInputStream(InputStream stream, MessageDigest digest)
        {
            super(stream, digest);
        }

        @Override
        public int read() throws IOException
        {
            return check(super.read());
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            return check(super.read(b, off, len));
        }

        private int check(int result) throws IOException
        {
            if (result == -1 && !this.checked) {
                this.checked = true;

                String actual = HexFormat.of().formatHex(getMessageDigest().digest());
                if (!actual.equalsIgnoreCase(sha256)) {
                    throw new IOException(String.format("Unexpected SHA-256 hash [%s] for the package file [%s]",
                        actual, uriToDownload));
                }
            }

            return result;
        }
    }

    /**
     * @param uriToDownload the URI of the file
     * @param sizeOfFile the size of the file, -1 if unknown
     * @param sha256 the expected SHA-256 hash of the file, {@code null} if unknown
     * @param httpClient the client used to download the file
     */
    public PypiExtensionFile(URI uriToDownload, long sizeOfFile, String sha256, PypiHttpClient httpClient)
    {
        this.uriToDownload = uriToDownload;
        this.sizeOfFile = sizeOfFile;
        this.sha256 = sha256;
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

        if (this.sha256 == null) {
            return stream;
        }

        try {
            return new HashCheckingInputStream(stream, MessageDigest.getInstance("SHA-256"));
        } catch (NoSuchAlgorithmException e) {
            stream.close();

            throw new IOException("SHA-256 is not supported", e);
        }
    }
}
