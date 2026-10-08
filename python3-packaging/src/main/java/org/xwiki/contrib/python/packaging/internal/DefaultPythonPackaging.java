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
package org.xwiki.contrib.python.packaging.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Disposable;
import org.xwiki.contrib.python.engine.Python3Engine;
import org.xwiki.contrib.python.packaging.PythonMetadata;
import org.xwiki.contrib.python.packaging.PythonPackaging;
import org.xwiki.contrib.python.packaging.PythonPackagingException;
import org.xwiki.contrib.python.packaging.PythonRequirement;
import org.xwiki.environment.Environment;

/**
 * Default implementation of {@link PythonPackaging}, based on the packaging library (the reference implementation of
 * the Python packaging standards, used by pip) running in a dedicated context of the shared GraalPy engine.
 * <p>
 * The context is only created the first time it's needed and is shared by all the calls, so the calls are
 * serialized.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultPythonPackaging implements PythonPackaging, Disposable
{
    private static final String RESOURCES = "/python/";

    private static final String PACKAGING_WHEEL = "packaging.whl";

    private static final String HELPER = "xwiki_packaging.py";

    private static final String FALSE = "false";

    @Inject
    private Environment environment;

    @Inject
    private Python3Engine python3Engine;

    private Context context;

    private Value helper;

    private Value getHelper() throws PythonPackagingException
    {
        if (this.helper == null) {
            try {
                initializeContext();
            } catch (IOException | PolyglotException e) {
                dispose();

                throw new PythonPackagingException("Failed to initialize the Python packaging tools", e);
            }
        }

        return this.helper;
    }

    private void initializeContext() throws IOException
    {
        // The packaging library is imported from its wheel, which must be a file
        File directory = new File(this.environment.getTemporaryDirectory(), "python/packaging");
        directory.mkdirs();
        File wheel = new File(directory, PACKAGING_WHEEL);
        try (InputStream stream = getClass().getResourceAsStream(RESOURCES + PACKAGING_WHEEL)) {
            Files.copy(stream, wheel.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        this.context = this.python3Engine.createContextBuilder()
            .option("python.PythonPath", wheel.getAbsolutePath()).option("python.WarnExperimentalFeatures", FALSE)
            .out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).build();

        try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(RESOURCES + HELPER),
            StandardCharsets.UTF_8)) {
            this.context.eval(Source.newBuilder(Python3Engine.LANGUAGE, reader, HELPER).build());
        }

        this.helper = this.context.getBindings(Python3Engine.LANGUAGE);
    }

    private Value call(String function, Object... arguments) throws PythonPackagingException
    {
        try {
            return getHelper().getMember(function).execute(arguments);
        } catch (PolyglotException e) {
            throw new PythonPackagingException(e.getMessage(), e);
        }
    }

    private static List<String> toStrings(Value array)
    {
        List<String> result = new ArrayList<>((int) array.getArraySize());
        for (long i = 0; i < array.getArraySize(); ++i) {
            result.add(array.getArrayElement(i).asString());
        }

        return result;
    }

    @Override
    public synchronized String getPythonVersion() throws PythonPackagingException
    {
        return call("get_python_version").asString();
    }

    @Override
    public synchronized boolean isPythonSupported(String requiresPython) throws PythonPackagingException
    {
        return call("is_python_supported", requiresPython).asBoolean();
    }

    @Override
    public synchronized boolean isCompatibleWheel(String filename) throws PythonPackagingException
    {
        return call("is_compatible_wheel", filename).asBoolean();
    }

    @Override
    public synchronized Map<String, String> selectWheels(List<String> versions, Map<String, String> wheels)
        throws PythonPackagingException
    {
        List<String> filenames = new ArrayList<>(wheels.size());
        List<Object> requiresPythons = new ArrayList<>(wheels.size());
        wheels.forEach((filename, requiresPython) -> {
            filenames.add(filename);
            requiresPythons.add(requiresPython);
        });

        Value selected = call("select_wheels", ProxyArray.fromList(new ArrayList<>(versions)),
            ProxyArray.fromList(new ArrayList<>(filenames)), ProxyArray.fromList(requiresPythons));

        Map<String, String> result = new LinkedHashMap<>();
        for (long i = 0; i < selected.getArraySize(); ++i) {
            List<String> entry = toStrings(selected.getArrayElement(i));
            result.put(entry.get(0), entry.get(1));
        }

        return result;
    }

    @Override
    public synchronized PythonMetadata parseMetadata(String metadata) throws PythonPackagingException
    {
        Value parsed = call("parse_metadata", metadata);

        PythonMetadata result = new PythonMetadata();
        result.setName(getString(parsed, "name"));
        result.setVersion(getString(parsed, "version"));
        result.setSummary(getString(parsed, "summary"));
        result.setDescription(getString(parsed, "description"));
        result.setLicense(getString(parsed, "license"));
        result.setLicenseExpression(getString(parsed, "license_expression"));
        result.setHomePage(getString(parsed, "home_page"));
        result.setRequiresPython(getString(parsed, "requires_python"));
        result.setRequiresDist(toStrings(parsed.getHashValue("requires_dist")));

        Value projectUrls = parsed.getHashValue("project_urls");
        Map<String, String> urls = new LinkedHashMap<>();
        Value keys = projectUrls.getHashKeysIterator();
        while (keys.hasIteratorNextElement()) {
            Value key = keys.getIteratorNextElement();
            urls.put(key.asString(), projectUrls.getHashValue(key).asString());
        }
        result.setProjectUrls(urls);

        return result;
    }

    private static String getString(Value dict, String key)
    {
        Value value = dict.getHashValue(key);

        return value == null || value.isNull() ? null : value.asString();
    }

    @Override
    public synchronized List<PythonRequirement> getRequirements(Collection<String> requiresDist)
        throws PythonPackagingException
    {
        Value requirements = call("get_requirements", ProxyArray.fromList(new ArrayList<>(requiresDist)));

        List<PythonRequirement> result = new ArrayList<>((int) requirements.getArraySize());
        for (long i = 0; i < requirements.getArraySize(); ++i) {
            List<String> requirement = toStrings(requirements.getArrayElement(i));
            result.add(new PythonRequirement(requirement.get(0), requirement.get(1), requirement.get(2)));
        }

        return result;
    }

    @Override
    public synchronized List<String> filterVersions(Collection<String> versions, String specifiers,
        Boolean preReleases) throws PythonPackagingException
    {
        return toStrings(call("filter_versions", ProxyArray.fromList(new ArrayList<>(versions)),
            specifiers != null ? specifiers : "", preReleases));
    }

    @Override
    public synchronized void dispose()
    {
        if (this.context != null) {
            this.context.close();
            this.context = null;
        }
        this.helper = null;
    }
}
