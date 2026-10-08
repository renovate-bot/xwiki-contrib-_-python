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
package org.xwiki.rendering.macro.python;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.environment.Environment;
import org.xwiki.environment.internal.StandardEnvironment;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.script.JUnit5ScriptMockSetup;
import org.xwiki.rendering.macro.script.ScriptMacroParameters;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.script.ScriptContextManager;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.XWikiTempDir;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * Validate how the Python 3 macro shares variables with the script context.
 *
 * @version $Id$
 */
@ComponentTest
@AllComponents
class Python3MacroBindingsTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @XWikiTempDir
    private File permanentDirectory;

    private final SimpleScriptContext scriptContext = new SimpleScriptContext();

    private final Bindings globalBindings = new SimpleBindings();

    private Macro<ScriptMacroParameters> macro;

    @BeforeEach
    void beforeEach() throws Exception
    {
        new JUnit5ScriptMockSetup(this.componentManager);

        // Like during a request, the macro needs an execution context
        this.componentManager.<Execution>getInstance(Execution.class).setContext(new ExecutionContext());
        // The script evaluation events (rights checks, class loader) are not the topic of this test
        this.componentManager.registerMockComponent(ObservationManager.class);

        ScriptContextManager scriptContextManager =
            this.componentManager.registerMockComponent(ScriptContextManager.class);
        when(scriptContextManager.getScriptContext()).thenReturn(this.scriptContext);
        this.scriptContext.setBindings(this.globalBindings, ScriptContext.GLOBAL_SCOPE);

        StandardEnvironment environment = this.componentManager.getInstance(Environment.class);
        environment.setPermanentDirectory(this.permanentDirectory);

        this.macro = this.componentManager.getInstance(Macro.class, "python3");
    }

    private void execute(String script) throws Exception
    {
        MacroTransformationContext context = new MacroTransformationContext();
        context.setSyntax(Syntax.XWIKI_2_1);

        this.macro.execute(new ScriptMacroParameters(), script, context);
    }

    @Test
    void bindings() throws Exception
    {
        // Something used by the caller of the script (like the stacks used by Velocity to execute its macros)
        Deque<String> callerState = new ArrayDeque<>();
        this.scriptContext.setAttribute("callerState", callerState, ScriptContext.ENGINE_SCOPE);
        this.scriptContext.setAttribute("modified", "before", ScriptContext.ENGINE_SCOPE);

        execute(String.join("\n",
            "assert callerState is not None",
            "modified = 'after'",
            "defined = 42"));

        // The variables defined or modified by the script are shared with the script context
        assertEquals("after", this.globalBindings.get("modified"));
        assertEquals(42, this.globalBindings.get("defined"));
        // But not the ones which come from the script context and were not modified
        assertFalse(this.globalBindings.containsKey("callerState"));
        assertSame(callerState, this.scriptContext.getAttribute("callerState", ScriptContext.ENGINE_SCOPE));

        // The caller removes what it does not need anymore
        this.scriptContext.removeAttribute("callerState", ScriptContext.ENGINE_SCOPE);

        // The following scripts don't see it anymore (and don't put it back in the script context)
        execute(String.join("\n",
            "assert 'callerState' not in globals()",
            "other = 1"));

        assertFalse(this.globalBindings.containsKey("callerState"));
        assertEquals(1, this.globalBindings.get("other"));
    }
}
