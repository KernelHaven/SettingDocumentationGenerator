package net.ssehub.kernel_haven.util;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.ssehub.kernel_haven.config.EnumSetting;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.config.Setting.Type;

public class SettingDocumentationGenerator {
    
    private static final String HEADER = "# Configuration file documentation for KernelHaven\n" + 
            "#\n" + 
            "# This file lists all known configuration options that are available for\n" + 
            "# KernelHaven. Note that some plugins may define their own settings, that are\n" + 
            "# not listed in this file. However, this file should cover the most common\n" + 
            "# plugins.\n" + 
            "#\n" + 
            "# This configuration file is a standard Java Properties file (see the\n" + 
            "# documentation of java.util.Properties). A properties file is a key-value\n" + 
            "# storage in the format: key = value. Lines starting with a hash (#) are\n" + 
            "# comments and not considered in parsing. Multiple lines can be joined together\n" + 
            "# with a backslash (\\) character directly in front of the line break. This is\n" + 
            "# useful for multi-line values or formatting.\n" + 
            "#\n" + 
            "# This file lists the keys for the settings defined in the main infrastructure,\n" + 
            "# followed by the settings of common plugins. Each setting has a short\n" + 
            "# description, that contains:\n" + 
            "#  * An explanation text for the setting.\n" + 
            "#  * The type of setting (see below for a list possible types).\n" + 
            "#  * For enums: The possible values.\n" + 
            "#  * The default value for the setting, if it specifies one.\n" + 
            "#  * If no default value is specified: Whether the setting is mandatory or not.\n" + 
            "#\n" + 
            "# Possible setting types are:\n" + 
            "#  * String: A simple text value.\n" + 
            "#  * Integer: An integer value. An exception is generated if this is not a valid\n" + 
            "#             integer.\n" + 
            "#  * Boolean: A boolean value. Everything except \"true\" (case insensitive) is\n" + 
            "#             considered to be the value false.\n" + 
            "#  * Regular Expression: A Java regular expression. See the documentation for\n" + 
            "#                        java.util.regex.Pattern class.\n" + 
            "#  * Path: A path value. The file denoted by this does not have to exist.\n" + 
            "#  * Existing File: A path value for an existing file. If the specified file\n" + 
            "#                   does not exist, then an exception is thrown. This can either\n" + 
            "#                   be relative to the current working directory or an absolute\n" + 
            "#                   path.\n" + 
            "#  * Existing Directory: A path value for an existing directory. If the\n" + 
            "#                        specified directory does not exist, then an exception\n" + 
            "#                        is thrown. This can either be relative to the current\n" + 
            "#                        working directory or an absolute path.\n" + 
            "#  * Enum: One value of an enumartion of possible values. Not case sensitive.\n" + 
            "#  * Comma separated list of strings: A comma separated list of string values.\n" + 
            "#  * List of setting keys: A list of string values created from multiple setting\n" + 
            "#                          keys. The base key is appended by a .0 for the first\n" + 
            "#                          value. The following values increase this integer.\n" + 
            "#                          For example:\n" + 
            "#                            key.0 = a\n" + 
            "#                            key.1 = b\n" + 
            "#                            key.2 = c\n" + 
            "#                          Defines the list [\"a\", \"b\", \"c\"].\n" + 
            "#\n" + 
            "# This was automatically generated on: ";
    
    private List<String> names;
    
    private List<List<Setting<?>>> settings;
    
    private Map<String, List<String>> enumValues;
    
    public SettingDocumentationGenerator() {
        this.names = new LinkedList<>();
        this.settings = new LinkedList<>();
        this.enumValues = new HashMap<>();
    }
    
    public void addSettingsFromClassPath(File classPath, String sectionName) throws Exception {
        List<Setting<?>> result = new LinkedList<>();
        
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] { classPath.toURI().toURL() })) {
            classLoader.setDefaultAssertionStatus(false);
            
            Path path = classPath.toPath();
            Files.walk(path)
                    .filter((p) -> Files.isRegularFile(p))
                    .filter((p) -> p.toString().endsWith(".class"))
                    .filter((p) -> !p.toString().contains("$"))
                    .map((p) -> path.relativize(p))
                    .map((p) -> p.toString().replace(".class", "").replace(File.separatorChar, '.'))
                    .map((className) -> {
                        try {
                            return classLoader.loadClass(className);
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
        
        settings.add(result);
        names.add(sectionName);
    }
    
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
    
    private void generateHeader(StringBuilder b, String text) {
        for (int i = 0; i < text.length() + 4; i++) {
            b.append('#');
        }
        b.append("\n# ").append(text).append(" #\n");
        for (int i = 0; i < text.length() + 4; i++) {
            b.append('#');
        }
        b.append("\n\n");
    }
    
    public String generateSettingText() {
        StringBuilder result = new StringBuilder(HEADER);
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime now = LocalDateTime.now();
        result.append(dtf.format(now)).append("\n\n");
        
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
                result.append("# Type: ").append(typetToString(setting.getType())).append("\n");
                
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
                    .append(setting.getDefaultValue().isEmpty() ? "(empty string)" : setting.getDefaultValue())
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
        
        return result.toString();
    }
    
    private String typetToString(Type type) {
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

    public static void main(String[] args) throws Exception {
        SettingDocumentationGenerator generator = new SettingDocumentationGenerator();
        generator.addSettingsFromClassPath(new File("../KernelHaven/bin"), "Main Infrastructure");
        
        // Utilities
        generator.addSettingsFromClassPath(new File("../CnfUtils/bin"), "CnfUtils");
        generator.addSettingsFromClassPath(new File("../IOUtils/bin"), "IOUtils");
        generator.addSettingsFromClassPath(new File("../NonBooleanUtils/bin"), "NonBooleanUtils");
        
        // analyses
        generator.addSettingsFromClassPath(new File("../FeatureEffectAnalysis/bin"), "FeatureEffectAnalysis");
        generator.addSettingsFromClassPath(new File("../MetricHaven/bin"), "MetricHaven");
        generator.addSettingsFromClassPath(new File("../UnDeadAnalyzer/bin"), "UnDeadAnalyzer");
        
        // extractors
        generator.addSettingsFromClassPath(new File("../KbuildMinerExtractor/bin"), "KbuildMinerExtractor");
        generator.addSettingsFromClassPath(new File("../KconfigReaderExtractor/bin"), "KconfigReaderExtractor");
        generator.addSettingsFromClassPath(new File("../SrcMlExtractor/bin"), "SrcMlExtractor");
        generator.addSettingsFromClassPath(new File("../TypeChefExtractor/bin"), "TypeChefExtractor");
        generator.addSettingsFromClassPath(new File("../UndertakerExtractor/bin"), "UndertakerExtractor");
        
        System.out.println(generator.generateSettingText());
    }

}
