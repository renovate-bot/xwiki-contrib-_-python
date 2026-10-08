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
package org.xwiki.rendering.internal.macro.python;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.script.ScriptContext;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Value;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.python.PythonPaths;
import org.xwiki.contrib.python.engine.Python3Engine;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.macro.script.ScriptMacroParameters;

/**
 * A macro for executing Python 3 scripts.
 * 
 * @version $Id: ee55972a8ae877b833f896681ad23d3d0e9f29a8 $
 */
@Component
@Named("python3")
@Singleton
public class Python3Macro extends AbstractPolyglotMacro<ScriptMacroParameters>
{
    /**
     * The description of the macro.
     */
    private static final String DESCRIPTION = "Executes a Python 3 script.";

    /**
     * The description of the macro content.
     */
    private static final String CONTENT_DESCRIPTION = "The Python 3 script to execute";

    @Inject
    private PythonPaths paths;

    @Inject
    private Python3Engine python3Engine;

    /**
     * Create and initialize the descriptor of the macro.
     */
    public Python3Macro()
    {
        super("Python 3", DESCRIPTION, new DefaultContentDescriptor(CONTENT_DESCRIPTION));
    }

    @Override
    protected Builder createContextBuilder()
    {
        return this.python3Engine.createContextBuilder();
    }

    @Override
    protected String getScriptEngineName()
    {
        return "python";
    }

    @Override
    protected Object getContextConfiguration()
    {
        // The Python paths depend on the current wiki and user
        return List.copyOf(this.paths.getPaths());
    }

    @Override
    protected void updateContext(Builder builder, Object configuration)
    {
        super.updateContext(builder, configuration);

        // Set the Python paths available in the current context
        builder.option("python.PythonPath", StringUtils.join((List<?>) configuration, File.pathSeparator));
    }

    @Override
    protected void uploadBindings(ScriptContext scriptContext, Context pContext, Map<String, Object> downloaded)
    {
        super.uploadBindings(scriptContext, pContext, downloaded);

        // Also inject global Python variables to mimic the behavior of JSR223-based macros (in the Polyglot world
        // you are supposed to use special APi to export/import bindings). The variables coming from the script context
        // and not modified by the script are not injected back, since the script context entries they come from can
        // be modified or removed later (by Velocity for example), which an injected copy would not follow.
        Value globals = pContext.eval(getScriptEngineName(), "globals()");
        Value keys = globals.getHashKeysIterator();
        while (keys.hasIteratorNextElement()) {
            String name = keys.getIteratorNextElement().asString();
            if (!name.startsWith("__")) {
                Value value = globals.getHashValue(name);
                if (!isDownloaded(downloaded, name, value)) {
                    scriptContext.setAttribute(name, value.as(Object.class), ScriptContext.GLOBAL_SCOPE);
                }
            }
        }
    }
}
