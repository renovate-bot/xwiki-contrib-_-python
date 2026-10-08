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
package org.xwiki.contrib.python.engine.internal;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.slf4j.Logger;

/**
 * Forward the GraalPy logs to a SLF4J logger (instead of the standard error output).
 *
 * @version $Id$
 */
public class LoggerHandler extends Handler
{
    private static final String LOG_FORMAT = "[{}] {}";

    private final Logger logger;

    /**
     * @param logger the logger to forward the logs to
     */
    public LoggerHandler(Logger logger)
    {
        this.logger = logger;
    }

    @Override
    public void publish(LogRecord logRecord)
    {
        if (logRecord.getLevel().intValue() >= Level.WARNING.intValue()) {
            this.logger.warn(LOG_FORMAT, logRecord.getLoggerName(), logRecord.getMessage());
        } else {
            this.logger.debug(LOG_FORMAT, logRecord.getLoggerName(), logRecord.getMessage());
        }
    }

    @Override
    public void flush()
    {
        // Nothing to flush
    }

    @Override
    public void close()
    {
        // Nothing to close
    }
}
