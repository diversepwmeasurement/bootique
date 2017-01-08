package io.bootique.help.config;

import io.bootique.help.ConsoleAppender;
import io.bootique.meta.MetadataNode;
import io.bootique.meta.config.ConfigListMetadata;
import io.bootique.meta.config.ConfigMapMetadata;
import io.bootique.meta.config.ConfigMetadataVisitor;
import io.bootique.meta.config.ConfigObjectMetadata;
import io.bootique.meta.config.ConfigValueMetadata;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @since 0.21
 */
class ConfigSectionGenerator implements ConfigMetadataVisitor<Object> {

    static final int DEFAULT_OFFSET = DefaultConfigHelpGenerator.DEFAULT_OFFSET;

    protected ConsoleAppender out;

    public ConfigSectionGenerator(ConsoleAppender out) {
        this.out = Objects.requireNonNull(out);
    }

    @Override
    public Object visitObjectMetadata(ConfigObjectMetadata metadata) {
        printNode(metadata, false);

        List<ConfigObjectMetadata> subconfigs = metadata.getAllSubConfigs()
                .map(md -> md.accept(new ConfigMetadataVisitor<ConfigObjectMetadata>() {
                    @Override
                    public ConfigObjectMetadata visitObjectMetadata(ConfigObjectMetadata metadata) {
                        return metadata.isAbstractType() || metadata.getProperties().isEmpty() ? null : metadata;
                    }
                }))
                .filter(md -> md != null)
                .collect(Collectors.toList());

        if (!subconfigs.isEmpty()) {
            ConfigObjectMetadata last = subconfigs.get(subconfigs.size() - 1);
            subconfigs.forEach(md -> {
                printObjectNoSubclasses(md);

                if (md != last) {
                    out.println();
                }
            });
        }

        return null;
    }

    @Override
    public Object visitValueMetadata(ConfigValueMetadata metadata) {
        printNode(metadata, true);
        return null;
    }

    @Override
    public Object visitListMetadata(ConfigListMetadata metadata) {
        printNode(metadata, false);

        metadata.getElementType().accept(new ConfigSectionListGenerator(out.withOffset(DEFAULT_OFFSET)));

        return null;
    }

    @Override
    public Object visitMapMetadata(ConfigMapMetadata metadata) {
        printNode(metadata, false);

        metadata.getValuesType().accept(
                new ConfigSectionMapGenerator(metadata.getKeysType(), out.withOffset(DEFAULT_OFFSET)));

        return null;
    }

    protected void printObjectNoSubclasses(ConfigObjectMetadata metadata) {

        ConsoleAppender shifted = out.withOffset(DEFAULT_OFFSET);

        if (metadata.getTypeLabel() != null) {
            shifted.println("#");
            shifted.println("# Type option: ", metadata.getTypeLabel());

            // subtype description is printed here (while supertype description is printed inside 'printNode')
            if (metadata.getDescription() != null) {
                shifted.println("# ", metadata.getDescription());
            }

            shifted.println("# Resolved as '", typeLabel(metadata.getType()), "'.");
            shifted.println("#");

            shifted.println();
            shifted.println("type: '", metadata.getTypeLabel() + "'");
        }

        ConfigSectionGenerator childGenerator = new ConfigSectionGenerator(shifted);

        metadata.getProperties()
                .stream()
                .sorted(Comparator.comparing(MetadataNode::getName))
                .forEach(p -> {
                    p.accept(childGenerator);
                });

    }

    protected void printNode(ConfigValueMetadata metadata, boolean asValue) {
        Type valueType = metadata.getType();

        if (metadata.getDescription() != null) {
            out.println("# ", metadata.getDescription());
        }

        if (valueType != null && !isImpliedType(valueType)) {
            out.println("# Resolved as '", typeLabel(valueType), "'.");
        }

        if (asValue) {
            String valueLabel = metadata.getType() != null ? sampleValue(metadata.getType()) : "?";
            out.println(metadata.getName(), ": ", valueLabel);
        } else {
            out.println(metadata.getName(), ":");
        }
    }

    protected String sampleValue(Type type) {

        // TODO: allow to provide sample values in metadata, so that we can display something useful

        String typeName = type.getTypeName();

        switch (typeName) {
            case "boolean":
            case "java.lang.Boolean":
                return "<true|false>";
            case "int":
            case "java.lang.Integer":
                return "<int>";
            case "byte":
            case "java.lang.Byte":
                return "<byte>";
            case "double":
            case "java.lang.Double":
                return "<double>";
            case "float":
            case "java.lang.Float":
                return "<float>";
            case "short":
            case "java.lang.Short":
                return "<short>";
            case "long":
            case "java.lang.Long":
                return "<long>";
            case "java.lang.String":
                return "<string>";
            default:
                if (type instanceof Class) {
                    Class<?> classType = (Class<?>) type;
                    if (classType.isEnum()) {

                        StringBuilder out = new StringBuilder("<");
                        Object[] values = classType.getEnumConstants();
                        for (int i = 0; i < values.length; i++) {
                            if (i > 0) {
                                out.append("|");
                            }
                            out.append(values[i]);
                        }
                        out.append(">");
                        return out.toString();
                    }
                }

                return "<value>";
        }
    }

    protected boolean isImpliedType(Type type) {
        String typeName = type.getTypeName();

        switch (typeName) {
            case "boolean":
            case "java.lang.Boolean":
            case "int":
            case "java.lang.Integer":
            case "byte":
            case "java.lang.Byte":
            case "double":
            case "java.lang.Double":
            case "float":
            case "java.lang.Float":
            case "short":
            case "java.lang.Short":
            case "long":
            case "java.lang.Long":
            case "java.lang.String":
                return true;
            default:
                return false;
        }
    }

    protected String typeLabel(Type type) {

        String typeName = type.getTypeName();

        switch (typeName) {
            case "java.lang.Boolean":
                return "boolean";
            case "java.lang.Integer":
                return "int";
            case "java.lang.Byte":
                return "byte";
            case "java.lang.Double":
                return "double";
            case "java.lang.Float":
                return "float";
            case "java.lang.Short":
                return "short";
            case "java.lang.Long":
                return "long";
            case "java.lang.String":
                return "String";
            default:

                if (type instanceof Class) {
                    Class<?> classType = (Class<?>) type;
                    if (Map.class.isAssignableFrom(classType)) {
                        return "Map";
                    }
                    // TODO: decipher collection type... for now hardcoding List type
                    else if (Collection.class.isAssignableFrom(classType)) {
                        return "List";
                    }
                } else if (type instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) type;

                    StringBuilder out = new StringBuilder(typeLabel(parameterizedType.getRawType()));
                    out.append("<");

                    Type[] args = parameterizedType.getActualTypeArguments();
                    if (args != null) {
                        for (int i = 0; i < args.length; i++) {
                            if (i > 0) {
                                out.append(", ");
                            }
                            out.append(typeLabel(args[i]));
                        }
                    }

                    out.append(">");
                    return out.toString();
                }

                return typeName;
        }
    }
}