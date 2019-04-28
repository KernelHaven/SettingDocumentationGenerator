/*
 * Copyright 2017-2019 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ssehub.kernel_haven.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.ssehub.kernel_haven.config.EnumSetting;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.config.Setting.Type;
import net.ssehub.kernel_haven.fe_analysis.Settings;

/**
 * A class for automatically generating config_template.properties. This class uses reflection to search for all
 * <code>static final Setting</code> fields and uses them to generate the documentation automagically.
 *
 * @author Adam
 */
public class SettingDocumentationGenerator {
    
    private static final String HEADER = "# Configuration file documentation for KernelHaven\n"
            + "#\n"
            + "# This file lists all known configuration options that are available for\n"
            + "# KernelHaven. Note that some plugins may define their own settings, that are\n"
            + "# not listed in this file. However, this file should cover the most common\n"
            + "# plugins.\n"
            + "#\n"
            + "# This configuration file is a standard Java Properties file (see the\n"
            + "# documentation of java.util.Properties). A properties file is a key-value\n"
            + "# storage in the format: key = value. Lines starting with a hash (#) are\n"
            + "# comments and not considered in parsing. Multiple lines can be joined together\n"
            + "# with a backslash (\\) character directly in front of the line break. This is\n"
            + "# useful for multi-line values or formatting. Backslash characters (\\) in normal\n"
            + "# text content are used for escaping; thus a double backslash (\\\\) is required\n"
            + "# to write a single backslash as a property value (this should be kept in mind\n"
            + "# when writing regular expressions as property values). The default values for\n"
            + "# settings are already escaped and have two backslash characters (\\\\) instead of\n"
            + "# a single one.\n"
            + "#\n"
            + "# This file lists the keys for the settings defined in the main infrastructure,\n"
            + "# followed by the settings of common plugins. Each setting has a short\n"
            + "# description, that contains:\n"
            + "#  * An explanation text for the setting.\n"
            + "#  * The type of setting (see below for a list possible types).\n"
            + "#  * For enums: The possible values.\n"
            + "#  * The default value for the setting, if it specifies one.\n"
            + "#  * If no default value is specified: Whether the setting is mandatory or not.\n"
            + "#\n"
            + "# Possible setting types are:\n"
            + "#  * String: A simple text value.\n"
            + "#  * Integer: An integer value. An exception is generated if this is not a valid\n"
            + "#             integer.\n"
            + "#  * Boolean: A boolean value. Everything except \"true\" (case insensitive) is\n"
            + "#             considered to be the value false.\n"
            + "#  * Regular Expression: A Java regular expression. See the documentation for\n"
            + "#                        java.util.regex.Pattern class.\n"
            + "#  * Path: A path value. The file denoted by this does not have to exist.\n"
            + "#  * Existing File: A path value for an existing file. If the specified file\n"
            + "#                   does not exist, then an exception is thrown. This can either\n"
            + "#                   be absolute, relative to the current working directory or\n"
            + "#                   relative to the source_tree setting (first file found in\n"
            + "#                   this order is used).\n"
            + "#  * Existing Directory: A path value for an existing directory. If the\n"
            + "#                        specified directory does not exist, then an exception\n"
            + "#                        is thrown. This can either be relative to the current\n"
            + "#                        working directory or an absolute path.\n"
            + "#  * Enum: One value of an enumartion of possible values. Not case sensitive.\n"
            + "#  * Comma separated list of strings: A comma separated list of string values.\n"
            + "#  * List of setting keys: A list of string values created from multiple setting\n"
            + "#                          keys. The base key is appended by a .0 for the first\n"
            + "#                          value. The following values increase this integer.\n"
            + "#                          For example:\n"
            + "#                            key.0 = a\n"
            + "#                            key.1 = b\n"
            + "#                            key.2 = c\n"
            + "#                          Defines the list [\"a\", \"b\", \"c\"].\n"
            + "#\n"
            + "# This was automatically generated on: ";

    /**
     * A list of section names.
     */
    private List<String> names;
    
    /**
     * A list of settings. First dimension is section (same indices as {@link #names}), second dimension is settings
     * in this section.
     */
    private List<List<Setting<?>>> settings;
    
    /**
     * Enum values for {@link EnumSetting}s. Key is setting name, value is a list of enum constant names.
     */
    private Map<String, List<String>> enumValues;
    
    /**
     * Creates a {@link SettingDocumentationGenerator}.
     */
    public SettingDocumentationGenerator() {
        this.names = new LinkedList<>();
        this.settings = new LinkedList<>();
        this.enumValues = new HashMap<>();
    }
    
    /**
     * Searches for {@link Setting} constants in a jar file. This walks through all .class files in the jar, loads the
     * class and searches for {@link Setting} constants via reflection. The jar must be in the class-path of this JVM.
     * 
     * @param jarFile The jar file to search in.
     * @param sectionName The name of the section that the {@link Setting}s found in the file should appear under.
     * 
     * @throws IOException If reading the jar file fails.
     */
    public void findSettingsInJarFile(File jarFile, String sectionName) throws IOException {
        List<Setting<?>> result = new LinkedList<>();
        
        try (ZipArchive jar = new ZipArchive(jarFile)) {
            
            List<String> classes = new LinkedList<>();
            
            for (File file : jar.listFiles()) {
                if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                    String className = file.getPath().replace(".class", "").replace(File.separatorChar, '.');
                    if (className.startsWith("net.ssehub")) {
                        classes.add(className);
                    }
                }
            }
            
            loadSettingsFromClasses(classes.stream().sorted(), result);
        }
        
        settings.add(result);
        names.add(sectionName);
    }
    
    /**
     * Searches for {@link Setting} constants in a directory. This walks through all .class files in the directory,
     * loads the class and searches for {@link Setting} constants via reflection. The directory must be in the
     * class-path of this JVM.
     * 
     * @param classPathDir The directory to search in.
     * @param sectionName The name of the section that the {@link Setting}s found in this directory should appear under.
     * 
     * @throws IOException If searching for .class files fails.
     */
    public void findSettingsInClassPath(File classPathDir, String sectionName) throws IOException {
        List<Setting<?>> result = new LinkedList<>();
        
        Path classPath = classPathDir.toPath();
        Stream<String> classNames = Files.walk(classPath)
                .filter((path) -> Files.isRegularFile(path))
                .filter((path) -> path.toString().endsWith(".class"))
                .filter((path) -> !path.toString().contains("$"))
                .map((path) -> classPath.relativize(path))
                .map((path) -> path.toString().replace(".class", "").replace(File.separatorChar, '.'))
                .sorted();
        
        loadSettingsFromClasses(classNames, result);
        
        settings.add(result);
        names.add(sectionName);
    }
    
    /**
     * Walks through the given stream of class names and searches for <code>static final Setting</code> fields. Adds
     * all of these into the result list. All of the classes must be load-able by {@link Class#forName(String)}.
     *  
     * @param classNames The stream containing the fully qualified class names to search in.
     * @param result The list where to add the results.
     */
    private void loadSettingsFromClasses(Stream<String> classNames, List<Setting<?>> result) {
        classNames
            .map((className) -> {
                try {
                    return Class.forName(className);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            })
            .map((clazz) -> clazz.getDeclaredFields())
            .flatMap((fields) -> Arrays.stream(fields))
            .filter((field) -> Modifier.isFinal(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
            .filter((field) -> Setting.class.isAssignableFrom(field.getType()))
            .map((field) -> {
                field.setAccessible(true);
                try {
                    return (Setting<?>) field.get(null);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            })
            .peek((setting) -> {
                if (setting instanceof EnumSetting<?>) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Enum<?>> enumClass = ((EnumSetting<? extends Enum<?>>) setting).getEnumClass();
                    
                    List<String> fields = new LinkedList<>();
                    for (Field field : enumClass.getFields()) {
                        if (enumClass.isAssignableFrom(field.getType())) {
                            fields.add(field.getName());
                            
                        }
                    }
                    enumValues.put(setting.getKey(), fields);
                    
                }
            })
            .forEach((setting) -> result.add(setting));
    }
    
    /**
     * Splits a line up so that each line is at most 78 characters long. Tries to split at spaces. Single words longer
     * than 78 characters are not split up.
     * 
     * @param description The text to split up.
     * @return The list of resulting lines.
     */
    private List<String> splitDescription(String description) {
        List<String> lines = new LinkedList<>();
        
        int currentLength = 0;
        int previousEnd = -1;
        for (int i = 0; i < description.length(); i++) {
            if (description.charAt(i) == '\n') {
                lines.add(description.substring(previousEnd + 1, i));
                previousEnd = i;
                currentLength = 0;
            }
            currentLength++;
            if (currentLength > 78) {
                // search previous space
                int j = i;
                for (; j > previousEnd + 1; j--) {
                    char c = description.charAt(j); 
                    if (c == ' ' || c == '\n') {
                        break;
                    }
                }
                if (j == previousEnd + 1) {
                    for (; j < description.length(); j++) {
                        char c = description.charAt(j); 
                        if (c == ' ' || c == '\n') {
                            break;
                        }
                    }
                }
                lines.add(description.substring(previousEnd + 1, j));
                previousEnd = j;
                currentLength = 0;
                i = j;
            }
        }
        if (previousEnd + 1 < description.length()) {
            lines.add(description.substring(previousEnd + 1));
        }
        
        return lines;
    }
    
    /**
     * Generates a "section header" string for a section name and appends it to the given buffer. The header will have
     * the format:
     * <code><pre>
     * #############
     * # Some Text #
     * #############
     * </pre></code>
     *  
     * @param buffer The buffer to add the "header" string to.
     * @param text The text that should appear in the section header.
     */
    private void generateHeader(StringBuilder buffer, String text) {
        for (int i = 0; i < text.length() + 4; i++) {
            buffer.append('#');
        }
        buffer.append("\n# ").append(text).append(" #\n");
        for (int i = 0; i < text.length() + 4; i++) {
            buffer.append('#');
        }
        buffer.append("\n\n");
    }
    
    /**
     * Escapes \ characters to \\.
     * 
     * @param value The setting value to escape in.
     * 
     * @return The escaped setting value.
     */
    private String escapeSettingValue(String value) {
        return value.replace("\\", "\\\\");
    }
    
    /**
     * Returns the list of section names.
     * 
     * @return The section names.
     */
    public List<String> getSectionNames() {
        return names;
    }
    
    /**
     * Returns all settings found. The first dimension are the sections, in the same order as they were added via
     * the find*() methods.
     * 
     * @return The list of all settings.
     * 
     * @see #getSectionNames()
     */
    public List<List<Setting<?>>> getSettings() {
        return settings;
    }
    
    /**
     * Returns a map containing all enum values for {@link EnumSetting}. The keys are the setting keys.
     * 
     * @return A map of enum values.
     */
    public Map<String, List<String>> getEnumValues() {
        return enumValues;
    }
    
    /**
     * Generates the documentation text for all settings that were previously added via the
     * <code>addSettingsFrom*()</code> methods. This text can be used as the content for config_template.properties.
     * 
     * @return The documentation text for all settings.
     */
    public String generateSettingText() {
        StringBuilder result = new StringBuilder(HEADER);
        
        result.append(Timestamp.INSTANCE.getTimestamp()).append("\n\n");
        
        for (int i = 0; i < names.size(); i++) {
            if (settings.get(i).isEmpty()) {
                continue;
            }
            
            generateHeader(result, names.get(i));
            
            for (Setting<?> setting : settings.get(i)) {
                for (String line : splitDescription(setting.getDescription())) {
                    result.append("# ").append(line).append("\n");
                }
                result.append("#\n");
                result.append("# Type: ").append(typeToString(setting.getType())).append("\n");
                
                if (setting.getType() == Type.ENUM) {
                    result.append("# Possible values: ");
                    for (String value : enumValues.get(setting.getKey())) {
                        result.append(value).append(", ");
                    }
                    result.replace(result.length() - 2, result.length(), ""); // remove trailing ,
                    result.append("\n");
                }
                
                if (setting.getDefaultValue() != null) {
                    result.append("# Default value: ")
                        .append(setting.getDefaultValue().isEmpty()
                                ? "(empty string)" : escapeSettingValue(setting.getDefaultValue()))
                        .append("\n");
                } else {
                    result.append("# Mandatory: ").append(setting.isMandatory() ? "Yes" : "No").append("\n");
                }
                
                String key = setting.getKey();
                if (setting.getType() == Type.SETTING_LIST) {
                    key += ".0";
                }
                result.append(key).append(" =");
                result.append("\n\n");
            }
            
        }
        
        result.replace(result.length() - 1, result.length(), ""); // remove one trailing \n
        
        return result.toString();
    }
    
    /**
     * Converts a setting type into a human-readable string.
     * 
     * @param type The type to get the string for.
     * @return The human readable text.
     */
    private String typeToString(Type type) {
        String str;
        switch (type) {
        case STRING:
            str = "String";
            break;
        case INTEGER:
            str = "Integer";
            break;
        case BOOLEAN:
            str = "Boolean";
            break;
        case REGEX:
            str = "Regular Expression";
            break;
        case PATH:
            str = "Path";
            break;
        case FILE:
            str = "Existing File";
            break;
        case DIRECTORY:
            str = "Existing Directory";
            break;
        case ENUM:
            str = "Enum";
            break;
        case STRING_LIST:
            str = "Comma separated list of strings";
            break;
            
        case SETTING_LIST:
            str = "List of setting keys";
            break;

        default:
            str = type.toString();
            break;
        }
        return str;
    }
    
    /**
     * The main method that executes the {@link SettingDocumentationGenerator}. This can be executed locally from
     * within eclipse, or from a shell with command line arguments (e.g. from an ant script).
     * <p>
     * If this is called from eclipse, no command line arguments are needed. It automatically searches in the public
     * KernelHaven eclipse projects. All of these projects must be in the workspace. All of these projects must be
     * added to the class-path of this project.
     * <p>
     * If this is called from somewhere else (e.g. ant or a shell), pass locations of jar archives as the command line
     * arguments. Each jar file location must be followed by a string containing the section header for the settings
     * found in that jar. Each of these jars must be in the class-path of this JVM.
     * <h2>Example</h2>
     * <code>java -cp .:plugin1.jar:plugin2.jar net.ssehub.kernel_haven.util.SettingDocumentationGenerator
     * plugin1.jar "Plugin 1" plugin2.jar "Plugin 2"</code>
     * <p>
     * The created documentation text is printed to {@link System#out}. You may want to save that in a file.
     * 
     * @param args Command line arguments. See above.
     * 
     * @throws IOException If finding {@link Settings}s fails.
     */
    public static void main(String[] args) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler((thread, exc) -> {
            exc.printStackTrace();
            System.exit(1);
        });
        
        SettingDocumentationGenerator generator = new SettingDocumentationGenerator();
        
        if (args.length == 0) {
            // this branch is taken when locally executing this from Eclipse with no java parameters
            // visit all Eclipse project in the current work space
            
            generator.findSettingsInClassPath(new File("../KernelHaven/bin"), "Main Infrastructure");
            
            // Utilities
            generator.findSettingsInClassPath(new File("../CnfUtils/bin"), "CnfUtils");
            generator.findSettingsInClassPath(new File("../IOUtils/bin"), "IOUtils");
            generator.findSettingsInClassPath(new File("../NonBooleanUtils/bin"), "NonBooleanUtils");
            generator.findSettingsInClassPath(new File("../DBUtils/bin"), "DBUtils");
            generator.findSettingsInClassPath(new File("../BusybootPreparation/bin"), "BusybootPreparation");
            
            // analyses
            generator.findSettingsInClassPath(new File("../FeatureEffectAnalysis/bin"), "FeatureEffectAnalysis");
            generator.findSettingsInClassPath(new File("../MetricHaven/bin"), "MetricHaven");
            generator.findSettingsInClassPath(new File("../UnDeadAnalyzer/bin"), "UnDeadAnalyzer");
            generator.findSettingsInClassPath(new File("../ConfigurationMismatchAnalysis/bin"),
                    "ConfigurationMismatchAnalysis");
            
            // extractors
            generator.findSettingsInClassPath(new File("../KbuildMinerExtractor/bin"), "KbuildMinerExtractor");
            generator.findSettingsInClassPath(new File("../KconfigReaderExtractor/bin"), "KconfigReaderExtractor");
            generator.findSettingsInClassPath(new File("../srcMLExtractor/bin"), "SrcMlExtractor");
            generator.findSettingsInClassPath(new File("../TypeChefExtractor/bin"), "TypeChefExtractor");
            generator.findSettingsInClassPath(new File("../UndertakerExtractor/bin"), "UndertakerExtractor");
            generator.findSettingsInClassPath(new File("../CodeBlockExtractor/bin"), "CodeBlockExtractor");
            
        } else {
            // this branch is taken when called from Ant
            // command line arguments are jar locations followed by the section name
            
            if (args.length % 2 != 0) {
                throw new IllegalArgumentException("Expecting: (<jarfile> <section name>)*");
            }
            
            for (int i = 0; i < args.length; i += 2) {
                generator.findSettingsInJarFile(new File(args[i]), args[i + 1]);
            }
        }
        
        System.out.print(generator.generateSettingText());
    }

}
