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

import fr.jmmc.oitools.fits.FitsUtils;
import fr.jmmc.oitools.model.OIFitsChecker;
import fr.jmmc.oitools.model.OIFitsCollection;
import fr.jmmc.oitools.model.OIFitsFile;
import fr.jmmc.oitools.model.OIFitsLoader;
import fr.jmmc.oitools.model.OIFitsWriter;
import fr.jmmc.oitools.model.Target;
import fr.jmmc.oitools.model.range.Range;
import fr.jmmc.oitools.processing.Merger;
import fr.jmmc.oitools.processing.Selector;
import fr.nom.tam.fits.FitsException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jammetv
 */
public class OIFitsProcessor extends OIFitsCommand {

    private static final String COMMAND_HELP = "help";
    private static final String COMMAND_LIST = "list";
    private static final String COMMAND_LIST_BL = "list_baselines";
    private static final String COMMAND_CONVERT = "convert";
    private static final String COMMAND_DUMP = "dump";
    private static final String COMMAND_MERGE = "merge";

    private static final String OPTION_MATCH_SEP = "-separation";
    private static final String OPTION_OUTPUT = "-output";
    /* filter options */
    private static final String OPTION_TARGET = "-target";
    private static final String OPTION_INSNAME = "-insname";
    private static final String OPTION_NIGHT = "-night";
    private static final String OPTION_MJD_RANGES = "-mjds";
    private static final String OPTION_BASELINES = "-baselines";
    private static final String OPTION_WL_RANGES = "-wavelengths";

    /**
     * Main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        try {
            if (args.length < 1) {
                showArgumentsHelp();
                return;
            }

            final boolean quiet = !hasOptionArg(args, "-l", "-log");
            bootstrap(quiet);

            final String command = args[0];

            // command processing
            if (COMMAND_HELP.equals(command)) {
                showArgumentsHelp();
            } else if (COMMAND_DUMP.equals(command)) {
                dump(args);
            } else if (COMMAND_LIST.equals(command)) {
                list(args);
            } else if (COMMAND_LIST_BL.equals(command)) {
                listBaselines(args);
            } else if (COMMAND_CONVERT.equals(command)) {
                copy(args);
            } else if (COMMAND_MERGE.equals(command)) {
                merge(args);
            } else {
                throw new IllegalArgumentException("Unknown command.");
            }
        } catch (IllegalArgumentException iae) {
            error(iae.getMessage());
            showArgumentsHelp();
        } catch (Exception e) {
            error("Processor: exception occured", e);
        }
    }

    private static void handleArgSeparation(final String[] args) {
        final String sep = getOptionArgValue(args, OPTION_MATCH_SEP);
        if (sep != null) {
            try {
                Target.MATCHER_LIKE.setSeparationInArcsec(Double.parseDouble(sep));
            } catch (NumberFormatException nfe) {
                error("Invalid separation: " + sep);
            }
        }
    }

    /**
     * List content of files
     *
     * @param args command line arguments.
     */
    private static void list(final String[] args) throws FitsException, IOException {
        final List<String> fileLocations = getInputFiles(args);
        final boolean check = hasOptionArg(args, "-c", "-check");

        handleArgSeparation(args);

        final OIFitsChecker checker = new OIFitsChecker();

        final OIFitsCollection oiFitsCollection = OIFitsCollection.create(checker, fileLocations);

        if (check) {
            info("validation results:\n" + checker.getCheckReport());
        }

        OIFitsCollectionViewer.process(oiFitsCollection);
    }

    /**
     * List baselines of files
     *
     * @param args command line arguments.
     */
    private static void listBaselines(final String[] args) throws FitsException, IOException {
        final List<String> fileLocations = getInputFiles(args);
        final boolean check = hasOptionArg(args, "-c", "-check");

        handleArgSeparation(args);

        final OIFitsChecker checker = new OIFitsChecker();

        final OIFitsCollection oiFitsCollection = OIFitsCollection.create(checker, fileLocations);

        if (check) {
            info("validation results:\n" + checker.getCheckReport());
        }

        OIFitsCollectionViewer.processBaselines(oiFitsCollection);
    }

    /**
     * Dump content of files
     *
     * @param args command line arguments.
     */
    private static void dump(final String[] args) throws FitsException, IOException {
        final List<String> fileLocations = getInputFiles(args);

        FitsUtils.setup();

        final StringBuilder sb = new StringBuilder(16 * 1024);

        for (String fileLocation : fileLocations) {
            info("Processing: " + fileLocation);
            try {
                FitsUtils.dumpFile(fileLocation, false, sb);

                info(sb.toString());
                sb.setLength(0); // reset

            } catch (Exception e) {
                error("Error reading file '" + fileLocation + "'", e);
            }
        }
    }

    /**
     * Copy content of a oifits file in another
     *
     * @param inputFileLocations
     * @param outputFilePath
     */
    private static void copy(final String[] args) throws FitsException, Exception {
        final List<String> fileLocations = getInputFiles(args);
        if (fileLocations.size() > 1) {
            throw new IllegalArgumentException("too many input files, only one is accepted.");
        }

        final String inputFileLocation = fileLocations.get(0);
        final String outputFilePath = getOutputFilepath(args);
        final boolean check = hasOptionArg(args, "-c", "-check");

        // Load then save file content
        final OIFitsFile result = OIFitsLoader.loadOIFits(inputFileLocation);
        // Store result
        write(outputFilePath, result, check);
    }

    /**
     * Merge some oifits files, store result in output file
     *
     * @param args: parameter of command line
     * @param fileLocations
     * @param outputFilePath
     */
    private static void merge(final String[] args) throws FitsException, IOException {
        final List<String> fileLocations = getInputFiles(args);
        final String outputFilePath = getOutputFilepath(args);
        final boolean check = hasOptionArg(args, "-c", "-check");

        handleArgSeparation(args);

        final OIFitsCollection oiFitsCollection = OIFitsCollection.create(null, fileLocations);

        final Selector selector = new Selector();

        if (hasOptionArg(args, OPTION_TARGET)) {
            selector.setTargetUID(getOptionArgValue(args, OPTION_TARGET));
        } else {
            final String arg = "-" + Selector.FILTER_TARGET_ID.toLowerCase();
            if (hasOptionArg(args, arg)) {
                selector.setTargetUID(getOptionArgValue(args, arg));
            }
        }
        if (hasOptionArg(args, OPTION_INSNAME)) {
            selector.setInsModeUID(getOptionArgValue(args, OPTION_INSNAME));
        }
        if (hasOptionArg(args, OPTION_NIGHT)) {
            selector.setNightID(Integer.valueOf(getOptionArgValue(args, OPTION_NIGHT)));
        } else {
            final String arg = "-" + Selector.FILTER_NIGHT_ID.toLowerCase();
            if (hasOptionArg(args, arg)) {
                selector.setNightID(Integer.valueOf(getOptionArgValue(args, arg)));
            }
        }
        if (hasOptionArg(args, OPTION_MJD_RANGES)) {
            selector.addFilter(Selector.FILTER_MJD, parseRanges(getOptionArgValue(args, OPTION_MJD_RANGES)));
        } else {
            final String arg = "-" + Selector.FILTER_MJD.toLowerCase();
            if (hasOptionArg(args, arg)) {
                selector.addFilter(Selector.FILTER_MJD, parseRanges(arg));
            }
        }
        if (hasOptionArg(args, OPTION_BASELINES)) {
            selector.addFilter(Selector.FILTER_STAINDEX, parseStrings(getOptionArgValue(args, OPTION_BASELINES)));
        } else {
            final String arg = "-" + Selector.FILTER_STAINDEX.toLowerCase();
            if (hasOptionArg(args, arg)) {
                selector.addFilter(Selector.FILTER_STAINDEX, parseStrings(getOptionArgValue(args, arg)));
            }
        }
        {
            final String arg = "-" + Selector.FILTER_STACONF.toLowerCase();
            if (hasOptionArg(args, arg)) {
                selector.addFilter(Selector.FILTER_STACONF, parseStrings(getOptionArgValue(args, arg)));
            }
        }
        if (hasOptionArg(args, OPTION_WL_RANGES)) {
            selector.addFilter(Selector.FILTER_EFFWAVE, parseRanges(getOptionArgValue(args, OPTION_WL_RANGES)));
        } else {
            final String arg = "-" + Selector.FILTER_EFFWAVE.toLowerCase();
            if (hasOptionArg(args, arg)) {
                selector.addFilter(Selector.FILTER_EFFWAVE, parseRanges(getOptionArgValue(args, arg)));
            }
        }
        {
            final String arg = "-" + Selector.FILTER_EFFBAND.toLowerCase();
            if (hasOptionArg(args, arg)) {
                selector.addFilter(Selector.FILTER_EFFBAND, parseRanges(getOptionArgValue(args, arg)));
            }
        }

        // TODO: handle extra arguments from OIFITS2 data model
        // Call merge
        final OIFitsFile result = Merger.process(oiFitsCollection, selector);

        if (result != null && result.hasOiData()) {
            // Store result
            write(outputFilePath, result, check);
        } else {
            info("Result is empty, no file created.");
        }
    }

    private static void write(final String outputFilePath, final OIFitsFile result, final boolean check) throws IOException, FitsException {
        if (check) {
            final OIFitsChecker checker = new OIFitsChecker();
            result.check(checker);
            info("validation results:\n" + checker.getCheckReport());
        }

        info("Writing: " + outputFilePath);
        // Store result
        OIFitsWriter.writeOIFits(outputFilePath, result);
    }

    /**
     * Get output file path from command arguments
     *
     * @param args
     * @return output file path
     */
    private static String getOutputFilepath(String[] args) {
        String outputFilePath = null;

        for (int i = 1; i < args.length; i++) {
            if (OPTION_OUTPUT.substring(0, 2).equals(args[i]) || OPTION_OUTPUT.equals(args[i])) {
                outputFilePath = (++i < args.length) ? args[i] : null;
                break;
            }
        }
        if (outputFilePath == null) {
            throw new IllegalArgumentException("No output file given in arguments.");
        }
        return outputFilePath;
    }

    /**
     * Get input file paths from command arguments
     *
     * @param args
     * @return input file paths
     */
    private static List<String> getInputFiles(String[] args) {
        final List<String> fileLocations = new ArrayList<String>();

        for (int i = 1; i < args.length; i++) {
            // note: should be generalized to any argument having value(s):
            if (OPTION_OUTPUT.substring(0, 2).equals(args[i])
                    || OPTION_MATCH_SEP.equals(args[i])
                    || OPTION_OUTPUT.equals(args[i])
                    || OPTION_TARGET.equals(args[i])
                    || OPTION_INSNAME.equals(args[i])
                    || OPTION_NIGHT.equals(args[i])
                    || OPTION_BASELINES.equals(args[i])
                    || OPTION_MJD_RANGES.equals(args[i])
                    || OPTION_WL_RANGES.equals(args[i])) {
                i++;  // skip next parameter which is the output file
            } else if (args[i].startsWith("-")) {
                // ignore short options
            } else {
                fileLocations.add(args[i]);
            }
        }

        if (fileLocations.isEmpty()) {
            throw new IllegalArgumentException("No file location given in arguments.");
        }
        return fileLocations;
    }

    /**
     * Show command arguments help
     */
    protected static void showArgumentsHelp() {
        info("--------------------------------------------------------------------------------------");
        info("Usage: " + OIFitsProcessor.class.getName() + " command -o <path_output_file> <file locations>");
        info("------------- Arguments help ---------------------------------------------------------");
        info("| Key          Value           Description                                           |");
        info("|------------------------------------------------------------------------------------|");
        info("| command      " + COMMAND_HELP + "           Show this help                                         |");
        info("| command      " + COMMAND_LIST + "           List content of several oifits files                   |");
        info("| command      " + COMMAND_LIST_BL + " List baselines and triplets used by several oifits files      |");
        info("| command      " + COMMAND_DUMP + "           Dump the given oifits files                            |");
        info("| command      " + COMMAND_CONVERT + "        Convert the given input file                           |");
        info("| command      " + COMMAND_MERGE + "          Merge several oifits files                             |");
        info("|------------------------------------------------------------------------------------|");
        info("| [-l] or [-log]              Enable logging (quiet by default)                      |");
        info("| [-c] or [-check]            Check output file before writing                       |");
        info("| [-separation] <value>       Separation in arcsec for the target matcher            |");
        info("| [-o] or [-output] <file_path> Complete path, absolute or relative, for output file |");
        info("| [-target] <value>           Filter result on given Target                          |");
        info("| [-insname] <value>          Filter result on given InsName                         |");
        info("| [-night] <value>            Filter result on given Night (integer)                 |");
        info("| [-baselines] <values>       Filter result on given Baselines or Triplets (comma-separated) |");
        info("| [-mjds] <values>            Filter result on given MJD ranges (comma-separated pairs) |");
        info("| [-wavelengths] <values>     Filter result on given wavelength ranges (comma-separated pairs) |");

        // TODO: dump extra arguments from OIFITS2 data model
        info("--------------------------------------------------------------------------------------");
    }

    public static List<String> parseStrings(final String input) {
        final String[] values = input.split(",");

        final List<String> baselineList = new ArrayList<String>(values.length);
        for (String value : values) {
            baselineList.add(value.trim());
        }
        if (baselineList.isEmpty()) {
            return null;
        }
        return baselineList;
    }

    public static StringBuilder dumpStrings(final List<String> values, final StringBuilder sb) {
        if (values == null || values.isEmpty()) {
            return sb;
        }
        for (String v : values) {
            sb.append(v).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb;
    }

    public static List<Range> parseRanges(final String input) {
        final String[] values = input.split(",");

        if ((values.length % 2) == 1) {
            throw new IllegalStateException("Invalid ranges (" + values.length + " items): " + input);
        }
        final List<Range> ranges = new ArrayList<Range>(values.length);

        for (int i = 0; i < values.length; i += 2) {
            final double min = Double.valueOf(values[i]);
            final double max = Double.valueOf(values[i + 1]);

            if (min > max) {
                throw new IllegalStateException("Invalid range [" + min + "," + max + "]");
            }

            ranges.add(new Range(min, max));
        }
        if (ranges.isEmpty()) {
            return null;
        }
        return ranges;
    }

    public static StringBuilder dumpRanges(final List<Range> values, final StringBuilder sb) {
        if (values == null || values.isEmpty()) {
            return sb;
        }
        for (Range r : values) {
            sb.append(r.getMin()).append(",").append(r.getMax()).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb;
    }

    public static String generateCLIargs(final Selector selector) {
        if (selector != null) {
            final StringBuilder sb = new StringBuilder(128);

            if (selector.getTargetUID() != null) {
                sb.append("-").append(Selector.FILTER_TARGET_ID.toLowerCase()).append(" ").append(selector.getTargetUID()).append(" ");
            }
            if (selector.getInsModeUID() != null) {
                sb.append(OIFitsProcessor.OPTION_INSNAME).append(" ").append(selector.getInsModeUID()).append(" ");
            }
            if (selector.getNightID() != null) {
                sb.append("-").append(Selector.FILTER_NIGHT_ID.toLowerCase()).append(" ").append(selector.getNightID()).append(" ");
            }
            /* no way to define selector.tables via CLI */

            if (selector.hasFilter(Selector.FILTER_MJD)) {
                sb.append("-").append(Selector.FILTER_MJD.toLowerCase()).append(" ");
                dumpRanges(selector.getFilter(Selector.FILTER_MJD), sb).append(" ");
            }

            if (selector.hasFilter(Selector.FILTER_STAINDEX)) {
                sb.append("-").append(Selector.FILTER_STAINDEX.toLowerCase()).append(" ");
                dumpStrings(selector.getFilter(Selector.FILTER_STAINDEX), sb).append(" ");
            }
            if (selector.hasFilter(Selector.FILTER_STACONF)) {
                sb.append("-").append(Selector.FILTER_STACONF.toLowerCase()).append(" ");
                dumpStrings(selector.getFilter(Selector.FILTER_STACONF), sb).append(" ");
            }

            if (selector.hasFilter(Selector.FILTER_EFFWAVE)) {
                sb.append("-").append(Selector.FILTER_EFFWAVE.toLowerCase()).append(" ");
                dumpRanges(selector.getFilter(Selector.FILTER_EFFWAVE), sb).append(" ");
            }
            if (selector.hasFilter(Selector.FILTER_EFFBAND)) {
                sb.append("-").append(Selector.FILTER_EFFBAND.toLowerCase()).append(" ");
                dumpRanges(selector.getFilter(Selector.FILTER_EFFBAND), sb).append(" ");
            }

            // convert generic filters from selector.filters (1D)
            for (Map.Entry<String, List<?>> e : selector.getFiltersMap().entrySet()) {
                if (!Selector.isCustomFilter(e.getKey())) {
                    sb.append("-").append(e.getKey().toLowerCase()).append(" ");
                    dumpRanges((List<Range>) e.getValue(), sb).append(" ");
                }
            }
            return sb.toString();
        }
        return "";
    }
}
