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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.Cleaner;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import org.apache.commons.io.output.ProxyOutputStream;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.xwiki.component.descriptor.ComponentDescriptor;
import org.xwiki.context.ExecutionContext;
import org.xwiki.properties.ConverterManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.script.AbstractScriptMacro;
import org.xwiki.rendering.macro.script.ScriptMacroParameters;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.script.ScriptContextManager;

/**
 * A base helper to evaluate a polyglot-based scripting language.
 * 
 * @param <P> the type of macro parameters bean.
 * @version $Id: ce39497eea83a7a19504cbb0744e0a11e04aa8e9 $
 */
// TODO Move to a dedicated Polyglot extension
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public abstract class AbstractPolyglotMacro<P extends ScriptMacroParameters> extends AbstractScriptMacro<P>
{
    private static final String POLYGLOT_CONTEXT = "polyglot.context";

    /**
     * Close the polyglot contexts once the execution context holding them is not used anymore, since the execution
     * context does not provide any way to be notified when it's disposed.
     */
    private static final Cleaner CLEANER = Cleaner.create();

    private record PolyglotContext(Context context, ProxyOutputStream out)
    {
    };

    @Inject
    private ConverterManager converterManager;

    /**
     * Used to get the current script context to give to script engine evaluation method.
     */
    @Inject
    private ScriptContextManager scriptContextManager;

    @Inject
    private ComponentDescriptor<Macro> descriptor;


    /**
     * @param macroName the name of the macro (eg "groovy")
     */
    protected AbstractPolyglotMacro(String macroName)
    {
        super(macroName, null, ScriptMacroParameters.class);
    }

    /**
     * @param macroName the name of the macro (eg "groovy")
     * @param macroDescription the text description of the macro.
     */
    protected AbstractPolyglotMacro(String macroName, String macroDescription)
    {
        super(macroName, macroDescription, ScriptMacroParameters.class);
    }

    /**
     * @param macroName the name of the macro (eg "groovy")
     * @param macroDescription the text description of the macro.
     * @param contentDescriptor the description of the macro content.
     */
    protected AbstractPolyglotMacro(String macroName, String macroDescription, ContentDescriptor contentDescriptor)
    {
        super(macroName, macroDescription, contentDescriptor, ScriptMacroParameters.class);
    }

    /**
     * @param macroName the name of the macro (eg "groovy")
     * @param macroDescription the text description of the macro.
     * @param parametersBeanClass class of the parameters bean for this macro.
     */
    protected AbstractPolyglotMacro(String macroName, String macroDescription,
        Class<? extends ScriptMacroParameters> parametersBeanClass)
    {
        super(macroName, macroDescription, parametersBeanClass);
    }

    /**
     * @param macroName the name of the macro (eg "groovy")
     * @param macroDescription the text description of the macro.
     * @param contentDescriptor the description of the macro content.
     * @param parametersBeanClass class of the parameters bean for this macro.
     */
    protected AbstractPolyglotMacro(String macroName, String macroDescription, ContentDescriptor contentDescriptor,
        Class<? extends ScriptMacroParameters> parametersBeanClass)
    {
        super(macroName, macroDescription, contentDescriptor, parametersBeanClass);
    }

    /**
     * Method to overwrite to indicate the script engine name.
     * 
     * @param parameters the macro parameters.
     * @param context the context of the macro transformation.
     * @return the name of the script engine to use.
     */
    protected String getScriptEngineName()
    {
        return this.descriptor.getRoleHint();
    }

    @Override
    protected List<Block> evaluateBlock(P parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        if (StringUtils.isEmpty(content)) {
            return Collections.emptyList();
        }

        String engineName = getScriptEngineName();

        List<Block> result;
        if (engineName != null) {
            try {
                result = evaluateBlock(engineName, parameters, content, context);
            } catch (Exception e) {
                throw new MacroExecutionException("Failed to evaluate Script Macro for content [" + content + "]", e);
            }
        } else {
            // If no language identifier is provided, don't evaluate content
            result = parseScriptResult(content, parameters, context);
        }

        return result;
    }

    /**
     * Get the current ScriptContext and refresh it.
     * 
     * @return the script context.
     */
    protected ScriptContext getScriptContext()
    {
        return this.scriptContextManager.getScriptContext();
    }

    /**
     * @return the downloaded bindings
     */
    private Map<String, Object> downloadBindings(ScriptContext scriptContext, Value pBindings)
    {
        Map<String, Object> downloaded = new HashMap<>();
        downloadBindings(scriptContext, ScriptContext.GLOBAL_SCOPE, pBindings, downloaded);
        downloadBindings(scriptContext, ScriptContext.ENGINE_SCOPE, pBindings, downloaded);

        return downloaded;
    }

    protected void downloadBindings(ScriptContext scriptContext, int scope, Value pBindings,
        Map<String, Object> downloaded)
    {
        Bindings bindings = scriptContext.getBindings(scope);
        if (bindings != null) {
            bindings.forEach((name, value) -> {
                pBindings.putMember(name, value);
                downloaded.put(name, value);
            });
        }
    }

    /**
     * @param downloaded the bindings downloaded from the script context before executing the script
     * @param name the name of the binding
     * @param value the current value of the binding in the polyglot context
     * @return true if the binding comes from the script context and was not modified by the script
     */
    protected static boolean isDownloaded(Map<String, Object> downloaded, String name, Value value)
    {
        return downloaded.containsKey(name) && Objects.equals(downloaded.get(name), value.as(Object.class));
    }

    /**
     * Remove the bindings downloaded from the script context (and not modified by the script) from the polyglot
     * context: the script context will change after the execution of the macro (some of its bindings can be removed
     * for example), so they should not be kept, nor be seen as defined by the following scripts.
     */
    private void removeDownloadedBindings(Value pBindings, Map<String, Object> downloaded)
    {
        for (String name : downloaded.keySet()) {
            if (pBindings.hasMember(name) && isDownloaded(downloaded, name, pBindings.getMember(name))) {
                pBindings.removeMember(name);
            }
        }
    }

    protected void setAttribute(ScriptContext scriptContext, String key, Object value)
    {
        scriptContext.setAttribute(key, value, ScriptContext.ENGINE_SCOPE);
        scriptContext.setAttribute(key, value, ScriptContext.GLOBAL_SCOPE);
    }

    /**
     * @param scriptContext the script context to update
     * @param pContext the polyglot context
     * @param downloaded the bindings downloaded from the script context before executing the script
     */
    protected void uploadBindings(ScriptContext scriptContext, Context pContext, Map<String, Object> downloaded)
    {
        // Generic Polyglot bindings (must be explicitly export through a polyglot API)
        Value pBindings = pContext.getPolyglotBindings();
        for (String key : pBindings.getMemberKeys()) {
            Object value = pBindings.getMember(key).as(Object.class);
            setAttribute(scriptContext, key, value);
        }
    }

    private PolyglotContext getPolyglotContext()
    {
        ExecutionContext econtext = this.execution.getContext();
        Object configuration = getContextConfiguration();

        // Keep one polyglot context per configuration for the duration of the execution context, so that what a
        // macro defines (classes, functions, imported modules) can be used by the following ones (the variables are
        // shared through the script context). The configuration can depend on the current wiki or user, which can
        // change during the execution.
        Map<Object, PolyglotContext> contexts = null;
        if (econtext != null) {
            contexts = (Map<Object, PolyglotContext>) econtext.getProperty(POLYGLOT_CONTEXT);
            if (contexts == null) {
                contexts = new HashMap<>();
                econtext.setProperty(POLYGLOT_CONTEXT, contexts);
            }

            PolyglotContext pcontext = contexts.get(configuration);
            if (pcontext != null) {
                return pcontext;
            }
        }

        // A new builder is needed for each context since the builder is not thread safe and holds the output stream
        ProxyOutputStream out = new ProxyOutputStream(null);
        Context.Builder builder = createContextBuilder().out(out);
        updateContext(builder, configuration);
        Context pcontext = builder.build();
        PolyglotContext context = new PolyglotContext(pcontext, out);
        // The cleaning action must not reference the PolyglotContext instance, or it would never become unreachable
        CLEANER.register(context, pcontext::close);

        if (contexts != null) {
            contexts.put(configuration, context);
        }

        return context;
    }

    /**
     * @return the builder of a new polyglot context (with all access allowed, like for the other script macros)
     */
    protected abstract Context.Builder createContextBuilder();

    /**
     * @return the configuration of the polyglot context to use in the current context (a new polyglot context is
     *         created when it changes), {@code null} by default
     */
    protected Object getContextConfiguration()
    {
        return null;
    }

    /**
     * @param builder the builder of the polyglot context to customize
     * @param configuration the configuration of the polyglot context, as returned by
     *            {@link #getContextConfiguration()}
     */
    protected void updateContext(Context.Builder builder, Object configuration)
    {
        // Nothing to customize by default
    }

    /**
     * Execute provided script and return {@link Block} based result.
     * 
     * @param engine the script engine to use to evaluate the script.
     * @param parameters the macro parameters.
     * @param content the script to execute.
     * @param context the context of the macro transformation.
     * @return the result of script execution.
     * @throws ScriptException failed to evaluate script
     * @throws MacroExecutionException failed to evaluate provided content.
     * @throws IOException
     */
    protected List<Block> evaluateBlock(String engineName, P parameters, String content,
        MacroTransformationContext context) throws ScriptException, MacroExecutionException, IOException
    {
        ScriptContext scriptContext = getScriptContext();

        Writer currentWriter = scriptContext.getWriter();
        Reader currentReader = scriptContext.getReader();

        List<Block> result;

        StringWriter stringWriter = new StringWriter();
        OutputStream out = WriterOutputStream.builder().setWriter(stringWriter).getOutputStream();

        try {
            PolyglotContext pContext = getPolyglotContext();

            pContext.out().setReference(out);

            Value pBindings = pContext.context.getBindings(engineName);
            Map<String, Object> downloaded = downloadBindings(scriptContext, pBindings);

            try {
                Value value = pContext.context.eval(engineName, content);

                uploadBindings(scriptContext, pContext.context, downloaded);

                result = convertScriptExecution(value, stringWriter, parameters, context);
            } finally {
                removeDownloadedBindings(pBindings, downloaded);
            }
        } finally {
            // restore current writer
            scriptContext.setWriter(currentWriter);
            // restore current reader
            scriptContext.setReader(currentReader);
        }

        return result;
    }

    private List<Block> convertScriptExecution(Value scriptResult, StringWriter scriptContextWriter, P parameters,
        MacroTransformationContext context) throws MacroExecutionException
    {
        List<Block> result = null;

        Object hostResult = scriptResult.as(Object.class);

        if (hostResult instanceof XDOM xdom) {
            result = xdom.getChildren();
        } else if (hostResult instanceof Block block) {
            result = Collections.singletonList(block);
        } else if (hostResult instanceof List && !((List<?>) hostResult).isEmpty()
            && ((List<?>) hostResult).get(0) instanceof Block) {
            result = (List<Block>) hostResult;
        } else if (hostResult instanceof Class) {
            // Class result means class definition and we don't want to print anything in this case
            result = Collections.emptyList();
        }

        if (result == null) {
            // If the Script Context writer is empty and the Script Result isn't, then convert the String Result
            // to String and display it
            String contentToParse = scriptContextWriter.toString();
            if (StringUtils.isEmpty(contentToParse) && hostResult != null) {
                // Convert the returned value into a String.
                contentToParse = this.converterManager.convert(String.class, hostResult);
            }
            // Run the wiki syntax parser on the Script returned content
            result = parseScriptResult(contentToParse, parameters, context);
        }

        return result;
    }

    @Override
    public boolean supportsInlineMode()
    {
        return true;
    }
}
