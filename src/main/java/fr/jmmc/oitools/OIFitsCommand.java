/*
 * Copyright (C) 2018 CNRS - JMMC project ( http://www.jmmc.fr )
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.jmmc.oitools;

import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jammetv
 */
public abstract class OIFitsCommand {

    /**
     * Bootstrap the runtime (locale, logger)
     *
     * @param quiet true to disable java.util.logging
     */
    public static void bootstrap(final boolean quiet) {
        // Set the default locale to en-US locale (for Numerical Fields "." ",")
        Locale.setDefault(Locale.US);

        // Set the default timezone to GMT to handle properly the date in UTC
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));

        initLoggers(quiet);
    }

    /**
     * Initialise java.util.logging Logger
     *
     * @param quiet true to disable java.util.logging
     */
    private static void initLoggers(final boolean quiet) {
        Logger logger = Logger.getLogger(OIFitsViewer.class.getName());

        // Get root logger:
        while (logger.getParent() != null) {
            logger = logger.getParent();
        }

        logger.setLevel((quiet) ? Level.SEVERE : Level.INFO);
    }

    /**
     * Print an information message
     *
     * @param message message to print
     */
    public static void info(final String message) {
        System.out.println(message);
    }

    /**
     * Print an error message
     *
     * @param message message to print
     */
    public static void error(final String message) {
        System.err.println(message);
    }

    /**
     * Print an error message with an exception
     *
     * @param message message to print
     * @param exception message to print
     */
    public static void error(final String message, final Exception exception) {
        error(message);

        Throwable errorElement = exception;
        while (errorElement != null) {
            error(errorElement.getMessage());
            errorElement = errorElement.getCause();
        }

        if (exception != null) {
            // Show stack trace:
            exception.printStackTrace(System.err);
        }
    }

    static boolean hasOptionArg(final String[] args, final String opt, final String longOpt) {
        for (final String arg : args) {
            if (arg.equals(opt) || arg.equals(longOpt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get position of an option in args. 
     * @param args
     * @param opt
     * @param longOpt
     * @return index of the option in args if found, -1 else.
     */
    static int getOptionArgPosition(final String[] args, final String opt, final String longOpt) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(opt) || args[i].equals(longOpt)) {
                return i;
            }
        }
        return -1;
    }

}
